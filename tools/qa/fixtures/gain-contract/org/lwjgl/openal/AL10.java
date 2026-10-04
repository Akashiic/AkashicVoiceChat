package org.lwjgl.openal;

import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.*;

/** TEST ONLY. Records native calls; never compiled into the mod or used by other QA. */
public final class AL10 {
    public static final int AL_FALSE=0, AL_TRUE=1, AL_SOURCE_RELATIVE=0x202;
    public static final int AL_POSITION=0x1004, AL_REFERENCE_DISTANCE=0x1020;
    public static final int AL_ROLLOFF_FACTOR=0x1021, AL_MAX_DISTANCE=0x1023;
    public static final int AL_BUFFERS_QUEUED=0x1015, AL_BUFFERS_PROCESSED=0x1016;
    public static final int AL_SOURCE_STATE=0x1010, AL_PLAYING=0x1012;
    public static final int AL_STOPPED=0x1014, AL_FORMAT_MONO16=0x1101;
    public static final int AL_GAIN=0x100A, AL_MAX_GAIN=0x100E;
    public static final int AL_RENDERER=0xB003, AL_ORIENTATION=0x100F;
    public static final int AL_INVERSE_DISTANCE_CLAMPED=0xD002;
    public static boolean extended=true, float32=true;
    public static String renderer="OpenAL Soft";
    public static float gainLimit=16f, listenerGain=0.37f;
    public static int listenerWrites, failSourceOnce=-1;
    public static final Map<Integer,float[]> floatBuffers=new HashMap<Integer,float[]>();
    public static final Map<Integer, Source> sources=new LinkedHashMap<Integer, Source>();
    public static final Map<Integer, short[]> buffers=new HashMap<Integer, short[]>();
    public static int nextSource=1, nextBuffer=1000;
    public static final class Source {
        public boolean relative;
        public float x,y,z,reference=1,rolloff=1,maxDistance=Float.MAX_VALUE,gain=1,maxGain=1;
        public int state=AL_STOPPED, positionCalls, relativeCalls, uploads;
        public final Deque<Integer> queued=new ArrayDeque<Integer>();
    }
    private static Source source(int id) {
        Source value=sources.get(id);
        if(value==null)throw new AssertionError("Unknown source "+id);
        return value;
    }
    public static void alGenSources(IntBuffer out) {
        int id=nextSource++;sources.put(id,new Source());out.put(out.position(),id);
    }
    public static void alSourcei(int id,int key,int value) {
        if(key!=AL_SOURCE_RELATIVE)throw new AssertionError("Unexpected source int "+key);
        source(id).relative=value==AL_TRUE;source(id).relativeCalls++;
    }
    public static void alSourcef(int id,int key,float value) {
        Source s=source(id);
        if(id==failSourceOnce && key==AL_GAIN){failSourceOnce=-1;throw new IllegalStateException("synthetic gain failure");}
        if(key==AL_GAIN)s.gain=value;
        else if(key==AL_MAX_GAIN){if(!extended && value>1)throw new AssertionError("unsupported max gain");s.maxGain=value;}
        else if(key==AL_REFERENCE_DISTANCE)s.reference=value;
        else if(key==AL_ROLLOFF_FACTOR)s.rolloff=value;
        else if(key==AL_MAX_DISTANCE)s.maxDistance=value;
        else throw new AssertionError("Unexpected source float "+key);
    }
    public static void alSource3f(int id,int key,float x,float y,float z) {
        if(key!=AL_POSITION)throw new AssertionError("Unexpected source vector "+key);
        Source s=source(id);s.x=x;s.y=y;s.z=z;s.positionCalls++;
    }
    public static int alGetSourcei(int id,int key) {
        Source s=source(id);
        if(key==AL_BUFFERS_QUEUED)return s.queued.size();
        if(key==AL_BUFFERS_PROCESSED)return 0;
        if(key==AL_SOURCE_STATE)return s.state;
        throw new AssertionError("Unexpected source query "+key);
    }
    public static void alGenBuffers(IntBuffer out) {
        int id=nextBuffer++;buffers.put(id,new short[0]);out.put(out.position(),id);
    }
    public static void alBufferData(int id,int format,ShortBuffer pcm,int rate) {
        if(format!=AL_FORMAT_MONO16 || rate!=48000)throw new AssertionError("Audio format changed");
        if(!buffers.containsKey(id))throw new AssertionError("Unknown buffer");
        ShortBuffer copy=pcm.duplicate();short[] data=new short[copy.remaining()];copy.get(data);
        buffers.put(id,data);
    }
    public static void alSourceQueueBuffers(int id,IntBuffer values) {
        Source s=source(id);for(int i=values.position();i<values.limit();i++)s.queued.add(values.get(i));
        s.uploads++;
    }
    public static void alSourcePlay(int id) { source(id).state=AL_PLAYING; }
    public static void alSourceStop(int id) { source(id).state=AL_STOPPED; }
    public static void alSourceUnqueueBuffers(int id,IntBuffer out) {
        Integer buffer=source(id).queued.poll();
        if(buffer==null)throw new AssertionError("Empty queue");out.put(out.position(),buffer);
    }
    public static void alDeleteBuffers(IntBuffer values) {
        for(int i=values.position();i<values.limit();i++)buffers.remove(values.get(i));
    }
    public static void alDeleteSources(IntBuffer values) {
        for(int i=values.position();i<values.limit();i++)sources.remove(values.get(i));
    }
    public static boolean alIsExtensionPresent(String name) {
        return "AL_SOFT_gain_clamp_ex".equals(name)?extended:"AL_EXT_FLOAT32".equals(name)&&float32;
    }
    public static float alGetFloat(int key) { if(key!=0x200E||!extended)throw new AssertionError("invalid query");return gainLimit; }
    public static String alGetString(int key) { if(key!=AL_RENDERER)throw new AssertionError("invalid string");return renderer; }
    public static float alGetSourcef(int id,int key) {
        Source s=source(id);if(key==AL_GAIN)return s.gain;if(key==AL_MAX_GAIN)return s.maxGain;
        throw new AssertionError("unexpected source float query");
    }
    public static void alBufferData(int id,int format,FloatBuffer pcm,int rate) {
        if(format!=0x10010||!float32||rate!=48000)throw new AssertionError("invalid float format");
        FloatBuffer copy=pcm.duplicate();float[] data=new float[copy.remaining()];copy.get(data);
        floatBuffers.put(id,data);buffers.put(id,new short[0]);
    }
    public static void alListenerf(int key,float value) {
        if(key!=AL_GAIN)throw new AssertionError("unexpected listener scalar");listenerGain=value;listenerWrites++;
    }
    public static void alListener3f(int key,float x,float y,float z) {}
    public static void alListener(int key,FloatBuffer value) {}
    public static void alDistanceModel(int model) {}
}
