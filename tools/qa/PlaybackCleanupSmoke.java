package dev.akashic.voice.voice.client.playback;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/** Real playback queue and epoch policy; physical OpenAL channels are test doubles. */
public final class PlaybackCleanupSmoke {
    interface Scenario { void run() throws Exception; }
    static int passed, failed;
    static final UUID A = new UUID(10, 11), B = new UUID(20, 21);
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run(String name, Scenario scenario) {
        try { scenario.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); }
    }
    public static void main(String[] args) throws Exception {
        run("retired_close_failure_does_not_escape", PlaybackCleanupSmoke::retiredFailure);
        run("replacement_frame_survives_old_close_failure", PlaybackCleanupSmoke::replacement);
        run("linkage_failure_is_isolated", PlaybackCleanupSmoke::linkage);
        run("shutdown_rejects_new_frames", PlaybackCleanupSmoke::shutdown);
        run("fatal_vm_error_is_not_swallowed", PlaybackCleanupSmoke::fatal);
        System.out.println("PLAYBACK_CLEANUP_RESULT passed="+passed+" failed="+failed);
        if (failed != 0) throw new AssertionError("playback cleanup regressions="+failed);
    }
    static void retiredFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            f.frame(A, 1); f.frame(B, 1);
            f.made.get(0).failure = new IllegalStateException("synthetic-close");
            f.output.retireStream(A, 1);
            call(f.output,"reconcileRetiredChannels");
            check(f.made.get(0).closes==1,"retired sink not closed exactly once");
            check(f.made.get(1).closes==0,"unrelated sink was closed");
            call(f.output,"reconcileRetiredChannels");
            f.frame(B,1);
            check(f.made.get(1).frames==2,"other speaker lost voice");
            check(f.made.get(0).closes==1,"failed close retried every audio cycle");
        }
    }
    static void replacement() throws Exception {
        try (Fixture f = new Fixture()) {
            f.frame(A,1); f.made.get(0).failure=new IllegalStateException("synthetic-close");
            f.frame(A,2);
            check(f.made.size()==2 && f.made.get(1).frames==1,"first successor frame dropped");
            check(f.made.get(0).closes==1,"old channel not retired once");
        }
    }
    static void linkage() throws Exception {
        try (Fixture f = new Fixture()) {
            f.frame(A,1); f.made.get(0).failure=new UnsatisfiedLinkError("synthetic-linkage");
            f.output.retireStream(A,1); call(f.output,"reconcileRetiredChannels");
            f.frame(B,1);
            check(f.made.size()==2 && f.made.get(1).frames==1,"linkage failure killed playback");
        }
    }
    static void shutdown() throws Exception {
        try (Fixture f = new Fixture()) {
            f.frame(A,1);
            PlaybackFrame inFlight=f.output.acquireFrame();
            check(inFlight!=null,"missing in-flight PCM");
            call(f.output,"shutdownOpenAl");
            check(f.output.acquireFrame()==null,"shutdown output still dispenses PCM");
            check(!f.output.submitFrame(inFlight,A,true,0,0,0,48,false,2),"frame accepted after shutdown");
            check(f.queue("freeFrames").size()==128,"shutdown lost PCM pool entries");
            check(f.queue("pendingFrames").isEmpty(),"post-shutdown audio queued");
        }
    }
    static void fatal() throws Exception {
        try (Fixture f = new Fixture()) {
            f.frame(A,1); f.made.get(0).failure=new OutOfMemoryError("synthetic-fatal");
            f.output.retireStream(A,1);
            try { call(f.output,"reconcileRetiredChannels"); throw new AssertionError("fatal VM error hidden"); }
            catch (OutOfMemoryError expected) { check("synthetic-fatal".equals(expected.getMessage()),"wrong fatal error"); }
        }
    }
    static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static Object call(Object object,String name) throws Exception {
        return invoke(object,object.getClass().getDeclaredMethod(name));
    }
    static Object invoke(Object target,Method method,Object... args) throws Exception {
        method.setAccessible(true);
        try { return method.invoke(target,args); }
        catch (InvocationTargetException e) {
            Throwable t=e.getCause(); if(t instanceof Error) throw (Error)t; throw (Exception)t;
        }
    }
    static final class Sink implements AudioPlayback.Channel {
        int closes,frames; Throwable failure;
        public void setSpatial(boolean value) {}
        public void setPosition(float x,float y,float z) {}
        public void setMaxDistance(float value) {}
        public void enqueue(short[] pcm) { frames++; }
        public void close() {
            closes++; if(failure instanceof Error) throw (Error)failure;
            if(failure instanceof RuntimeException) throw (RuntimeException)failure;
        }
    }
    static final class Fixture implements AutoCloseable {
        final List<Sink> made=new ArrayList<Sink>();
        final AudioPlayback output=new AudioPlayback(distance->{ Sink sink=new Sink();made.add(sink);return sink; });
        Fixture() throws Exception {
            Field running=AudioPlayback.class.getDeclaredField("running");running.setAccessible(true);running.setBoolean(output,true);
        }
        @SuppressWarnings("unchecked") BlockingQueue<PlaybackFrame> queue(String name) throws Exception {
            return (BlockingQueue<PlaybackFrame>)field(output,name);
        }
        void frame(UUID sender,long epoch) throws Exception {
            PlaybackFrame frame=output.acquireFrame();check(frame!=null,"empty pool");
            check(output.submitFrame(frame,sender,true,0,0,0,48,false,epoch),"submission rejected");
            PlaybackFrame next=queue("pendingFrames").poll();check(next!=null,"empty queue");
            try { invoke(output,AudioPlayback.class.getDeclaredMethod("processFrame",PlaybackFrame.class),next); }
            finally { output.releaseFrame(next); }
        }
        public void close() throws Exception { call(output,"shutdownOpenAl"); }
    }
}
