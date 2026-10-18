package cat.narezany.margyt.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * The mod, from a plugin's side.
 *
 * A plugin gets one of these before its first hook and keeps it. Everything a
 * plugin is likely to want that it should not have to find for itself: the
 * application context, somewhere to write, settings of its own that no other
 * plugin can collide with, and a line in the mod's diary.
 */
public final class PluginContext {

    private final Context context;
    private final String id;
    private final File folder;
    private final Diarist diarist;
    private final java.util.Set<String> downloads=new java.util.HashSet<>();

    /** How the loader writes into the mod's diary without exporting it. */
    public interface Diarist {
        void note(String line);
    }

    public PluginContext(Context context, String id, File folder, Diarist diarist) {
        this.context = context;
        this.id = id;
        this.folder = folder;
        this.diarist = diarist;
    }

    /** The application context. Never an activity, so it is safe to keep. */
    public Context context() {
        return context;
    }

    /** The plugin's own id, as its manifest spells it. */
    public String id() {
        return id;
    }

    /** Where the plugin was unpacked: its own files are here, read-only. */
    public File folder() {
        return folder;
    }

    /** Settings of the plugin's own, in a file named after its id. */
    public SharedPreferences prefs() {
        return context.getSharedPreferences("margyt_plugin_" + id, Context.MODE_PRIVATE);
    }
    /** Separate plugin settings for every account, including signed-out mode. */
    public SharedPreferences accountPrefs() {
        String uid=cat.narezany.margyt.Account.liveId();
        return context.getSharedPreferences("margyt_plugin_"+id+"_"+(uid==null?"guest":uid),Context.MODE_PRIVATE);
    }
    /** Metadata of the currently bound visible post, without session access. */
    public String currentVideoId(){return cat.narezany.margyt.VideoActions.currentId();}
    public long currentVideoPublishedTime(){return cat.narezany.margyt.VideoActions.currentPublishedTime();}
    public String getString(String key,String fallback){return prefs().getString(key,fallback);}
    public void putString(String key,String value){if(key==null||key.length()>80||value==null||value.length()>8192)throw new IllegalArgumentException("setting size");prefs().edit().putString(key,value).apply();}
    public interface Selected {void selected(int index);}
    /** Native mod choice window, always dispatched to the UI thread. */
    public void choose(String title,String[] options,Selected callback){
        if(options==null||options.length==0||options.length>32)throw new IllegalArgumentException("options");
        final String[] copy=options.clone();for(String item:copy)if(item==null||item.length()>160)throw new IllegalArgumentException("option length");
        onUiThread(()->{android.app.Activity a=activity();if(a==null||a.isFinishing())return;cat.narezany.margyt.Plugins.choose(a,title,copy,callback);});
    }
    public String accountId(){return cat.narezany.margyt.Account.liveId();}
    public int accentColour(){return cat.narezany.margyt.Accent.colour();}
    public android.app.Activity activity(){return cat.narezany.margyt.Screen.now();}
    public void onUiThread(Runnable action){new android.os.Handler(android.os.Looper.getMainLooper()).post(action);}
    public void copyText(final String text){onUiThread(()->{
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText(id,text==null?"":text));
    });}
    public interface Downloaded {void finished(File file,boolean success);}
    public void download(final String url,final String name,final Downloaded callback) {
        if(name==null || !name.matches("[A-Za-z0-9._-]{1,80}") || name.equals(".") || name.equals(".."))throw new IllegalArgumentException("file name");
        final File file=new File(context.getFilesDir(),"margyt/plugin-downloads/"+id+"/"+name);
        final String job="plugin-api:"+id+":"+name;
        synchronized(downloads){if(!downloads.add(job))throw new IllegalStateException("download already running");}
        final cat.narezany.margyt.DownloadTasks.Control control=cat.narezany.margyt.DownloadTasks.begin(job,name,id,()->download(url,name,callback));
        cat.narezany.margyt.Screen.progress(job,name,0);
        cat.narezany.margyt.Net.away(job,()->{
            boolean ok=false;
            try{ok=cat.narezany.margyt.Net.download(url,file,(percent,got,total)->{cat.narezany.margyt.DownloadTasks.progress(job,percent);cat.narezany.margyt.Screen.progress(job,name,percent);},control);}
            catch(Throwable error){log("download: "+error);}
            finally{cat.narezany.margyt.DownloadTasks.finish(job,ok);if(ok)cat.narezany.margyt.Screen.progressDone(job);else cat.narezany.margyt.Screen.progressGone(job);synchronized(downloads){downloads.remove(job);}}
            final boolean done=ok;onUiThread(()->{if(callback!=null)callback.finished(file,done);});
        });
    }

    /**
     * A line in the mod's diary, which the person can read and copy out of the
     * settings screen. This is the plugin's way of saying anything at all: an
     * app repacked from a release has no log anyone is watching.
     */
    // ------------------------------------------------------------ the screen

    /**
     * Ways of putting something on the screen.
     *
     * A plugin cannot declare an activity of its own -- components are read
     * out of the manifest when the app is installed, and the mod is not going
     * to rewrite somebody's installed apk. What it can do is everything short
     * of that: a row in the mod's settings, a window over whatever is showing,
     * a button that floats above a screen, and an ordinary Android intent if
     * it really does want its own screen from another app.
     */
    public interface Tapped {
        void tapped();
    }

    /** A row of the mod's own settings, under the plugin's own heading. */
    public void addSettingsRow(String title, String detail, Tapped action) {
        cat.narezany.margyt.Plugins.addRow(id, title, detail, action);
    }

    // --------------------------------------------- settings of the plugin's own
    //
    // These show up in the mod's settings under the plugin's name, only while
    // the plugin is switched on. Switches, choices and sliders are stored in
    // `prefs()` under the key given, so the plugin reads them back with
    // `getBoolean`, `getInt` or `prefs()` directly. Added in API 3.

    /** A small heading inside the plugin's block of settings. */
    public void addSettingsSection(String title) {
        cat.narezany.margyt.Plugins.addSection(id, title);
    }

    /** A switch. Read it back with getBoolean(key, def). */
    public void addSettingsToggle(String title, String detail, String key, boolean def) {
        cat.narezany.margyt.Plugins.addToggle(id, title, detail, key, def);
    }

    /** A list of options; the chosen index is stored. Read it with getInt(key, def). */
    public void addSettingsChoice(String title, String detail, String key,
                                  String[] options, int def) {
        cat.narezany.margyt.Plugins.addChoice(id, title, detail, key, options, def);
    }

    /** A slider from min to max inclusive. Read it with getInt(key, def). */
    public void addSettingsSlider(String title, String detail, String key,
                                  int min, int max, int def) {
        cat.narezany.margyt.Plugins.addSlider(id, title, detail, key, min, max, def);
    }

    public boolean getBoolean(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public int getInt(String key, int def) {
        return prefs().getInt(key, def);
    }

    /** The mod's own window: the same card TikTok's own dialogs are drawn as. */
    public void showWindow(String title, String message) {
        onUiThread(()->{android.app.Activity activity=activity();if(activity!=null && !activity.isFinishing())cat.narezany.margyt.Popup.show(activity,title,message);});
    }

    /** A button over whatever is on screen, for as long as that screen is. */
    public void offer(String label, final Tapped action) {
        cat.narezany.margyt.Screen.offer(label, new Runnable() {
            @Override
            public void run() {
                action.tapped();
            }
        });
    }

    /** A line along the top of the screen, for something that takes a while. */
    public void progress(String what, int percent) {
        cat.narezany.margyt.Screen.progress("plugin-api:"+id,what,percent);
    }

    /** Finish with the same flower-and-check ending the mod's own downloads get. */
    public void progressDone() {
        cat.narezany.margyt.Screen.progressDone("plugin-api:"+id);
    }

    public void progressGone() {
        cat.narezany.margyt.Screen.progressGone("plugin-api:"+id);
    }

    /** Whatever screen is up, or null between screens. */
    public android.app.Activity screen() {
        return cat.narezany.margyt.Screen.now();
    }

    /** Fetch something, off the main thread. Answers null rather than throwing. */
    public byte[] fetch(String url) {
        return cat.narezany.margyt.Net.bytes(url);
    }

    /** Run something off the main thread, named so the diary can say whose. */
    public void away(String what, Runnable work) {
        cat.narezany.margyt.Net.away(id + ": " + what, work);
    }

    public void log(String line) {
        diarist.note(id + ": " + line);
    }
}
