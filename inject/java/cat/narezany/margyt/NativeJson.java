package cat.narezany.margyt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Native Gson is renamed in release builds. Resolve exact contracts once. */
final class NativeJson {
    private static volatile Codec codec;
    private static final class Codec {
        final Object gson;
        final Method encode, decode;
        Codec() throws Exception {
            Class<?> type=Class.forName("com.google.gson.Gson");
            gson=type.getConstructor().newInstance();
            encode=method(type,"toJson",String.class,Object.class);
            decode=method(type,"fromJson",Object.class,String.class,Class.class);
        }
    }
    private static Method method(Class<?> type,String name,Class<?> result,Class<?>... args) throws Exception {
        try {Method named=type.getMethod(name,args);if(named.getReturnType()==result)return named;}
        catch(NoSuchMethodException ignored){}
        Method found=null;
        for(Method candidate:type.getMethods()) {
            if(Modifier.isStatic(candidate.getModifiers()) || candidate.getReturnType()!=result
                    || !java.util.Arrays.equals(candidate.getParameterTypes(),args))continue;
            if(found!=null)throw new NoSuchMethodException("Ambiguous native JSON contract: "+name);
            found=candidate;
        }
        if(found==null)throw new NoSuchMethodException("Native JSON contract unavailable: "+name);
        return found;
    }
    private static Codec get() throws Exception {
        Codec ready=codec;
        if(ready==null)synchronized(NativeJson.class){ready=codec;if(ready==null)codec=ready=new Codec();}
        return ready;
    }
    static String encode(Object value) throws Exception {Codec c=get();return (String)c.encode.invoke(c.gson,value);}
    static Object decode(String value,Class<?> type) throws Exception {Codec c=get();return c.decode.invoke(c.gson,value,type);}
}
