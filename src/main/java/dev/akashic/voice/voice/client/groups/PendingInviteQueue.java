package dev.akashic.voice.voice.client.groups;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import dev.akashic.voice.net.PacketInviteNotify;

public final class PendingInviteQueue {

    private static final Deque<PacketInviteNotify> queue = new ConcurrentLinkedDeque<>();

    private PendingInviteQueue() {}

    public static void enqueue(PacketInviteNotify n) {
        queue.add(n);
    }

    public static PacketInviteNotify peek() {
        return queue.peek();
    }

    public static PacketInviteNotify pop() {
        return queue.poll();
    }

    public static boolean isEmpty() {
        return queue.isEmpty();
    }

    public static void clear() {
        queue.clear();
    }
}
