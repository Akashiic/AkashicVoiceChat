"""Compile recovery regression tests with an actual JDK 8; never open audio hardware."""
from pathlib import Path
import argparse, hashlib, json, os, shutil, subprocess, re

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('label')
parser.add_argument('--all-smokes', action='store_true')
args = parser.parse_args()
if not args.label.replace('-', '').replace('_', '').isalnum():
    parser.error('invalid label')
run = root / 'audit' / 'qa-runs' / args.label
run.mkdir(parents=True, exist_ok=False)
status = {'state': 'STARTING', 'runner_pid': os.getpid()}
def save():
    temporary = run / 'status.tmp'
    temporary.write_text(json.dumps(status, indent=2), encoding='utf-8')
    temporary.replace(run / 'status.json')
save()
homes = [Path(os.environ['JAVA8_HOME'])] if os.environ.get('JAVA8_HOME') else []
homes += sorted(Path('C:/Program Files/Java').glob('jdk*'))
java_home = None
for home in homes:
    executable = home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
    if executable.is_file():
        result = subprocess.run([str(executable), '-version'], capture_output=True, timeout=10)
        if b'javac 1.8.' in result.stdout + result.stderr:
            java_home = home
            break
if java_home is None:
    status.update(state='BLOCKED', reason='Set JAVA8_HOME to an actual JDK 8')
    save()
    raise SystemExit(2)
client = 'src/main/java/dev/akashic/voice/voice/client/'
sources = [client + 'VoiceClient.java', client + 'MicCapture.java',
           client + 'jitter/OpusJitterBuffer.java', 'tools/qa/MicSequenceRecoverySmoke.java']
optional = [client + 'audio/capture/MicCaptureSession.java', 'tools/qa/MicCaptureSessionSmoke.java']
sources += [name for name in optional if (root / name).is_file()]
if args.all_smokes:
    for base in (root / 'src/main/java', root / 'shared/client-protocol/src/main/java'):
        sources += [path.relative_to(root).as_posix() for path in sorted(base.rglob('*.java'))]
    sources += [path.relative_to(root).as_posix() for path in sorted((root / 'tools/qa').glob('*Smoke.java'))]
    sources = list(dict.fromkeys(sources))
status.update(java_home=str(java_home), source_sha256={})
inputs = []
for name in sources:
    data = (root / name).read_bytes()
    status['source_sha256'][name] = hashlib.sha256(data).hexdigest()
    target = run / 'src' / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    inputs.append(str(target))
classes = run / 'classes'
classes.mkdir()
classpath = os.pathsep.join([str(classes), str(root / 'build/classes/java/main'),
    str(root / 'build/resources/main'), (root / 'audit/compile-classpath.txt').read_text().strip()])
java = str(java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
javac = str(java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac'))
commands = [[javac, '-encoding', 'UTF-8', '-sourcepath', '', '-implicit:none',
    '-cp', classpath, '-d', str(classes)] + inputs,
    [java, '-cp', classpath, 'dev.akashic.voice.voice.client.MicSequenceRecoverySmoke']]
if (root / 'tools/qa/MicCaptureSessionSmoke.java').is_file():
    commands.append([java, '-cp', classpath,
        'dev.akashic.voice.voice.client.audio.capture.MicCaptureSessionSmoke'])
if args.all_smokes:
    for name in sources:
        path = root / name
        if not name.startswith('tools/qa/') or path.name in ('MicSequenceRecoverySmoke.java', 'MicCaptureSessionSmoke.java'):
            continue
        text = path.read_text(encoding='utf-8')
        package = re.search(r'^package ([\w.]+);', text, re.MULTILINE)
        main = (package.group(1) + '.' if package else '') + path.stem
        cp = classpath
        if path.stem == 'CodecBackendStrictSmoke':
            cp = os.pathsep.join(item for item in classpath.split(os.pathsep) if 'opus-jni-rust' not in item)
        commands.append([java, '-cp', cp, main])
    commands.append([java, '-cp', classpath, 'CodecBackendSmoke', 'java', 'java-concentus'])
status['compile_source_count'] = len(inputs)
status['test_mains'] = [command[3:] for command in commands[1:]]
try:
    with (run / 'qa.log').open('wb') as log:
        for index, command in enumerate(commands):
            status.update(state='RUNNING', step=index, executable=command[0])
            save()
            result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=120)
            log.flush()
            status['exit_code'] = result.returncode
            if result.returncode:
                status['state'] = 'FAIL'
                break
        else:
            status['state'] = 'PASS'
except Exception as error:
    status.update(state='ERROR', error=repr(error), exit_code=2)
finally:
    save()
print('MIC_RECOVERY_QA', status['state'], str(run / 'status.json'), flush=True)
raise SystemExit(status.get('exit_code', 1))
