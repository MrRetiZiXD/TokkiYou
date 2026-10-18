package cat.narezany.margyt;
import java.util.*;
/** Bounded task history. Retry closures use application context, never a window. */
public final class DownloadTasks {
    public static final class Control {
        volatile long limit;
        volatile boolean cancelled;volatile java.net.HttpURLConnection connection;
        public boolean cancelled(){return cancelled;}
        void cancel(){cancelled=true;final java.net.HttpURLConnection live=connection;if(live!=null)Net.away("cancel download",()->live.disconnect());}
    }
    public static final class Task {
        public final String key,label,origin,owner;public final int percent;public final String state;public final boolean cancel,retry;
        Task(Entry e){key=e.key;label=e.label;origin=e.origin;owner=e.owner;percent=e.percent;state=e.state;cancel=!e.readOnly&&e.state.equals(Local.of("Загрузка"))&&!e.control.cancelled;retry=!cancel&&!e.state.equals(Local.of("Отмена…"))&&e.retry!=null&&!e.state.equals(Local.of("Готово"));}
    }
    private static final class Entry {
        String key,label,origin,owner,state=Local.of("Загрузка");int percent;boolean readOnly;Control control=new Control();Runnable retry;
    }
    private static final LinkedHashMap<String,Entry> tasks=new LinkedHashMap<>();
    public static synchronized Control begin(String key,String label,String origin,Runnable retry){
        Entry old=tasks.get(key);if(old!=null&&(old.state.equals(Local.of("Загрузка"))||old.state.equals(Local.of("Отмена…"))))throw new IllegalStateException("task running");
        Entry e=new Entry();e.key=key;e.label=label;e.origin=origin;e.retry=retry;
        String handle=Account.profileHandle();e.owner=Account.liveId()==null?Local.of("Без аккаунта"):handle==null?Local.of("Аккаунт ")+Account.liveId():"@"+handle;
        tasks.remove(key);tasks.put(key,e);trim();return e.control;
    }
    static synchronized void observe(String key,String label,int percent){Entry e=tasks.get(key);if(e==null||e.readOnly&&!e.state.equals(Local.of("Загрузка"))){e=new Entry();e.key=key;e.label=label;e.origin=Local.of("Мод");String handle=Account.profileHandle();e.owner=handle==null?Local.of("Этот аккаунт"):"@"+handle;e.readOnly=true;tasks.put(key,e);trim();}e.percent=percent;}
    static synchronized void endObserved(String key,boolean success){Entry e=tasks.get(key);if(e!=null&&e.readOnly){e.state=success?Local.of("Готово"):Local.of("Завершено");trim();}}
    private static void trim(){Iterator<Entry> i=tasks.values().iterator();while(tasks.size()>16&&i.hasNext()){Entry e=i.next();if(!e.state.equals(Local.of("Загрузка"))&&!e.state.equals(Local.of("Отмена…")))i.remove();}}
    public static synchronized void progress(String key,int percent){Entry e=tasks.get(key);if(e!=null)e.percent=percent<0?-1:Math.min(100,percent);}
    public static synchronized void finish(String key,boolean success){Entry e=tasks.get(key);if(e==null)return;e.state=e.control.cancelled?Local.of("Отменено"):success?Local.of("Готово"):Local.of("Ошибка");e.control.connection=null;if(success)e.retry=null;trim();}
    public static void cancel(String key){Control control=null;synchronized(DownloadTasks.class){Entry e=tasks.get(key);if(e!=null&&e.state.equals(Local.of("Загрузка"))){e.state=Local.of("Отмена…");control=e.control;}}if(control!=null)control.cancel();}
    public static void retry(String key){Runnable action=null;synchronized(DownloadTasks.class){Entry e=tasks.get(key);if(e!=null&&!e.state.equals(Local.of("Загрузка"))&&!e.state.equals(Local.of("Отмена…"))){action=e.retry;e.retry=null;}}if(action!=null)action.run();}
    static synchronized List<Task> snapshot(){List<Task> result=new ArrayList<>();for(Entry e:tasks.values())result.add(new Task(e));return result;}
}
