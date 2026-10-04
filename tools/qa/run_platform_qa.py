"""Finite Gradle QA with a local log, heartbeat and duplicate-run protection."""
from pathlib import Path
import argparse
import datetime as dt
import json
import os
import subprocess
import time
from source_fingerprint import fingerprint


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('label')
    parser.add_argument('--build', action='store_true')
    parser.add_argument('--legacy', action='store_true')
    parser.add_argument('--format-mic', action='store_true')
    parser.add_argument('--tests', default='')
    args = parser.parse_args()
    if args.format_mic:
        args.legacy = True
    if args.legacy and args.tests:
        parser.error('--tests is for the platform gateway only')
    if not args.label.replace('-', '').replace('_', '').isalnum():
        parser.error('label must contain only letters, digits, hyphens or underscores')
    audit = root / 'audit' / 'qa-runs'
    audit.mkdir(parents=True, exist_ok=True)
    lock = audit / 'platform.lock'
    run = audit / args.label
    run.mkdir(exist_ok=False)
    status = run / 'status.json'
    started = dt.datetime.now(dt.timezone.utc).isoformat()
    state = {'runner_pid': os.getpid(), 'started': started, 'state': 'STARTING'}

    def receipt():
        state['heartbeat'] = dt.datetime.now(dt.timezone.utc).isoformat()
        temporary = status.with_suffix('.tmp')
        temporary.write_text(json.dumps(state, indent=2), encoding='utf-8')
        temporary.replace(status)

    try:
        state['source_sha256'] = fingerprint(root, platform_only=not args.legacy)
    except Exception as error:
        state.update(state='ERROR', error=repr(error), exit_code=2)
        receipt()
        return 2

    try:
        fd = os.open(str(lock), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        state.update(state='BLOCKED', reason='Another QA lock exists; inspect it before retrying')
        receipt()
        return 2
    with os.fdopen(fd, 'w', encoding='utf-8') as handle:
        json.dump({'runner_pid': os.getpid(), 'run': str(run)}, handle)
    wrapper = root / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    command = [str(wrapper), '-p', str(root if args.legacy else root / 'platform'), '--console=plain',
               '--no-daemon', '--max-workers=2', '--offline']
    if args.format_mic:
        command += ['spotlessApply', '-PspotlessFiles=.*MicCapture[.]java,.*MicCaptureSession[.]java,.*VoiceClient[.]java']
    elif args.tests:
        command += [':gateway-velocity:test', '--tests', args.tests, '--rerun-tasks']
    else:
        command += ['build' if args.build else 'test', '--rerun-tasks']
    state['command'] = command
    receipt()
    try:
        with (run / 'gradle.log').open('wb') as output:
            process = subprocess.Popen(command, cwd=root, stdout=output, stderr=subprocess.STDOUT)
            state.update(state='RUNNING', gradle_pid=process.pid)
            print('QA_STARTED', str(status), flush=True)
            while process.poll() is None:
                receipt()
                time.sleep(2)
            state.update(state='PASS' if process.returncode == 0 else 'FAIL',
                         exit_code=process.returncode, gradle_exit_code=process.returncode)
            after = fingerprint(root, platform_only=not args.legacy)
            changed = sorted(name for name in set(after) | set(state['source_sha256'])
                if after.get(name) != state['source_sha256'].get(name))
            state['changed_inputs'] = changed
            allowed = {'src/main/java/dev/akashic/voice/voice/client/' + suffix
                for suffix in ('MicCapture.java', 'VoiceClient.java', 'audio/capture/MicCaptureSession.java')}
            if changed and not (args.format_mic and set(changed) <= allowed):
                state.update(state='STALE', exit_code=3)
            state['source_after_sha256'] = after
            receipt()
    except Exception as error:
        state.update(state='ERROR', error=repr(error))
        receipt()
        raise
    finally:
        active = locals().get('process')
        if active is None or active.poll() is not None:
            lock.unlink(missing_ok=True)
        else:
            # A lost runner must not permit a second build over its live child.
            print('QA_LOCK_RETAINED: inspect child PID before removing', flush=True)
    print('QA_FINISHED', state['state'], str(status), flush=True)
    return state.get('exit_code', 1)


if __name__ == '__main__':
    raise SystemExit(main())
