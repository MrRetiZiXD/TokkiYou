package cat.narezany.margyt;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** TikTok 47.2.41's own authenticated bio-save path; no credentials leave TikTok. */
final class NativeBio {
    private static final java.util.Set<Object> callbacks=java.util.Collections.synchronizedSet(new java.util.HashSet<Object>());
    static Object service() throws Exception {
        return Class.forName("X.14EI").getMethod("LJFF").invoke(null);
    }
    static Object current(String uid) throws Exception {
        Object active=service();
        Object user=active.getClass().getMethod("getCurUser").invoke(active);
        if (user==null || !uid.equals(user.getClass().getMethod("getUid").invoke(user)))
            throw new IllegalStateException("account changed");
        return user;
    }
    static String bio(String uid) throws Exception {
        Object user=current(uid);
        Object result=user.getClass().getMethod("getSignature").invoke(user);
        return result==null ? "" : (String)result;
    }
    static String handle(String uid) throws Exception {
        Object user=current(uid);
        Object result=user.getClass().getMethod("getUniqueId").invoke(user);
        return result instanceof String ? ((String)result).trim() : null;
    }
    static String extras(String uid) throws Exception {
        Object user=current(uid);
        Object value=user.getClass().getMethod("getSignatureExtra").invoke(user);
        return NativeJson.encode(value);
    }
    static List<?> decodeExtras(String json) throws Exception {
        Class<?> element=Class.forName("com.ss.android.ugc.aweme.profile.model.SignatureExtraInfo");
        Class<?> array=java.lang.reflect.Array.newInstance(element,0).getClass();
        Object result=NativeJson.decode(json,array);
        if (result==null) return java.util.Collections.emptyList();
        java.util.ArrayList<Object> out=new java.util.ArrayList<Object>();
        for (int i=0;i<java.lang.reflect.Array.getLength(result);i++)out.add(java.lang.reflect.Array.get(result,i));
        return out;
    }
    static boolean available() {
        try {
            Class<?> handler=Class.forName("com.bytedance.common.utility.collection.WeakHandler");
            service().getClass().getMethod("LJJLJLI",handler,String.class,List.class,int.class);
            return true;
        } catch (Throwable error) {return false;}
    }
    /** Worker-only; response must identify this account and confirm the exact saved text. */
    static boolean save(final String uid,final String expected,final String wanted,String extras) throws Exception {
        final List<?> metadata=decodeExtras(extras);
        final CountDownLatch done=new CountDownLatch(1);
        final boolean[] accepted={false};
        final java.util.concurrent.atomic.AtomicBoolean submitted=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean expired=new java.util.concurrent.atomic.AtomicBoolean();
        // Strongly retain proxy until completion; WeakHandler holds its target weakly.
        final Object[] callback=new Object[1];
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (expired.get()) {done.countDown();return;}
                if (!uid.equals(Account.id()) || !expected.equals(bio(uid))) {done.countDown();return;}
                final Object active=service();
                Class<?> handler=Class.forName("com.bytedance.common.utility.collection.WeakHandler");
                Class<?> contract=Class.forName("com.bytedance.common.utility.collection.WeakHandler$IHandler");
                callback[0]=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args) -> {
                    if (method.getDeclaringClass()==Object.class) {
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy==args[0];
                        return "margyt bio callback";
                    }
                    try {
                        if (args!=null && args.length==1 && args[0] instanceof Message) {
                            Object result=((Message)args[0]).obj;
                            result=Class.forName("com.bytedance.bpea.transmit.hook.HandlerHook")
                                    .getMethod("getMessageObj",Object.class).invoke(null,result);
                            if (result!=null && result.getClass().getName().equals("com.ss.android.ugc.aweme.profile.model.User")
                                    && uid.equals(Account.id())
                                    && uid.equals(result.getClass().getMethod("getUid").invoke(result))
                                    && wanted.equals(result.getClass().getMethod("getSignature").invoke(result))) {
                                // Same local cache update as the native signature-save presenter.
                                active.getClass().getMethod("LJJLI",String.class,List.class).invoke(active,wanted,metadata);
                                accepted[0]=true;
                            }
                        }
                    } catch (Throwable error) {Diary.note("bio response: "+error);}
                    callbacks.remove(proxy);done.countDown();return null;
                });
                callbacks.add(callback[0]);
                Object receiver=handler.getConstructor(contract).newInstance(callback[0]);
                submitted.set(true);
                active.getClass().getMethod("LJJLJLI",handler,String.class,List.class,int.class)
                        .invoke(active,receiver,wanted,metadata,0);
            } catch (Throwable error) {if(callback[0]!=null)callbacks.remove(callback[0]);Diary.note("bio save: "+error);done.countDown();}
        });
        boolean timely=done.await(30,TimeUnit.SECONDS);
        if (!timely) {
            expired.set(true);
            // Allow a submitted request to report a late success and repair the
            // local cache, but do not retain a missing callback indefinitely.
            if (submitted.get()) new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (callback[0]!=null) callbacks.remove(callback[0]);
            }, 120000L);
        }
        return timely && accepted[0];
    }
}
