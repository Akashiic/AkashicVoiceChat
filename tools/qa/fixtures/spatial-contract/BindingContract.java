import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;

/** Compare the recording facade to the actual LWJGL2 types, without calling native audio. */
public final class BindingContract {
    public static void main(String[] args) throws Exception {
        Class<?> actual=Class.forName("org.lwjgl.openal.AL10");
        int fields=0,methods=0;
        try(URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()},null)) {
            Class<?> fixture=loader.loadClass("org.lwjgl.openal.AL10");
            for(Field field:fixture.getFields()) {
                if(!field.getName().startsWith("AL_"))continue;
                if(field.getInt(null)!=actual.getField(field.getName()).getInt(null))
                    throw new AssertionError("Constant differs: "+field.getName());
                fields++;
            }
            for(Method method:fixture.getDeclaredMethods()) {
                if(!method.getName().startsWith("al"))continue;
                Method original=actual.getMethod(method.getName(),method.getParameterTypes());
                if(original.getReturnType()!=method.getReturnType())throw new AssertionError(method.toString());
                methods++;
            }
        }
        System.out.println("LWJGL_BINDING_CONTRACT_OK constants="+fields+" methods="+methods);
        System.out.println("REAL_LWJGL_ORIGIN="+actual.getProtectionDomain().getCodeSource().getLocation());
    }
}
