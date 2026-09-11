import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import dev.akashic.voice.voice.common.WireProtocol;
import dev.akashic.voice.voice.common.crypto.SessionCrypto;
import dev.akashic.voice.voice.common.crypto.SessionKeys;

public final class CryptoConcurrencySmoke {
    private static final int THREADS = 4;
    private static final int PER_THREAD = 500;

    public static void main(String[] args) throws Exception {
        byte[] master = new byte[SessionKeys.MASTER_SECRET_BYTES];
        byte[] cn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        byte[] sn = new byte[SessionKeys.HANDSHAKE_NONCE_BYTES];
        for (int i = 0; i < master.length; i++) master[i] = (byte) (i * 3 + 1);
        for (int i = 0; i < cn.length; i++) cn[i] = (byte) (i + 11);
        for (int i = 0; i < sn.length; i++) sn[i] = (byte) (i + 47);

        SessionKeys keys = SessionKeys.derive(master, cn, sn);
        final SessionCrypto crypto = SessionCrypto.forClient(keys);
        keys.close();
        final Set<Long> counters = Collections.synchronizedSet(new HashSet<Long>());
        final CountDownLatch ready = new CountDownLatch(THREADS);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);
        final Throwable[] failure = new Throwable[1];

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        go.await();
                        for (int i = 0; i < PER_THREAD; i++) {
                            ByteBuffer plain = ByteBuffer.allocate(32);
                            plain.putInt(threadId).putInt(i);
                            ByteBuffer wire = ByteBuffer.allocate(128);
                            if (!crypto.seal(plain, wire)) throw new AssertionError("seal failed");
                            ByteBuffer view = ByteBuffer.wrap(wire.array(), 0, wire.position());
                            if (view.getShort() != WireProtocol.MAGIC) throw new AssertionError("bad magic");
                            view.get();
                            view.get();
                            counters.add(view.getLong());
                        }
                    } catch (Throwable e) {
                        synchronized (failure) {
                            if (failure[0] == null) failure[0] = e;
                        }
                    } finally {
                        done.countDown();
                    }
                }
            }, "crypto-smoke-" + t);
            worker.start();
        }
        ready.await();
        go.countDown();
        done.await();
        crypto.close();

        if (failure[0] != null) throw new AssertionError("worker failed", failure[0]);
        int expected = THREADS * PER_THREAD;
        if (counters.size() != expected) {
            throw new AssertionError("duplicate counter: " + counters.size() + " of " + expected);
        }
        for (long i = 0; i < expected; i++) {
            if (!counters.contains(Long.valueOf(i))) throw new AssertionError("missing counter " + i);
        }
        System.out.println("crypto-concurrency-smoke-ok counters=" + expected);
    }
}
