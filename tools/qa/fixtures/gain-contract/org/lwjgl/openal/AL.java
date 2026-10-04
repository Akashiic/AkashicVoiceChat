package org.lwjgl.openal;
public final class AL {
 public static boolean isCreated(){return true;}
 public static void create(){throw new AssertionError("test must not create native audio");}
 public static void destroy(){throw new AssertionError("test must not destroy shared audio");}
}
