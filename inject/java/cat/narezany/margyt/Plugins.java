package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cat.narezany.margyt.plugin.MargyPlugin;
import cat.narezany.margyt.plugin.PluginContext;

import dalvik.system.DexClassLoader;

/**
 * Other people's code, running inside this one.
 *
 * A plugin is a zip -- `.mtp` -- holding a manifest, a dex and, if it likes, an
 * icon. Installing one unpacks it into the app's own files; switching it on
 * loads its dex with `DexClassLoader` and calls the hooks in `MargyPlugin`.
 *
 * Margy's plugins are Python, and these are not, for a reason that is about
 * repacking rather than taste: Margy is a fork and builds its own apk, so a
 * Python runtime goes in at build time. TokkiYou edits an apk somebody else
 * built, and a Python runtime would mean carrying CPython's native libraries
 * and its standard library into a 340 MB archive whose alignment is not ours to
 * decide. `DexClassLoader` is an ordinary Android API that costs nothing and
 * needs no root, so the format is the same shape -- manifest, icon, metadata,
 * a list with switches -- with a dex where the `main.py` would be.
 *
 * There is no sandbox. A plugin runs with everything TikTok has: its files, its
 * network, its session. The manifest says who wrote it; nothing here checks
 * that it is true. This is written the same way in the documentation, in the
 * settings screen and here, because it is the whole risk in one sentence.
 */
public final class Plugins {

    private Plugins() {}
    /** Public UI bridge; lifecycle has already been checked by PluginContext. */
    public static void choose(Activity activity,String title,String[] options,PluginContext.Selected selected){
        new ModDialog.Builder(activity).setTitle(title).setItems(options,(dialog,index)->{if(selected!=null)try{selected.selected(index);}catch(Throwable error){Diary.note("plugin choice: "+error);}}).show();
    }

    public static final String FOLDER = "plugins";
    public static final String MANIFEST = "manifest.json";
    public static final String DEX = "classes.dex";
    public static final String ICON = "icon.png";
    public static final String EXTENSION = ".mtp";

    private static final String PREFS = "margyt_plugins";

    /** What a manifest says, and what the loader made of it. */
    /** The package each running plugin's own classes are in, and whose it is. */
    private static final java.util.concurrent.ConcurrentHashMap<String, String> PACKAGES =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** A plugin's classes are taken to be those in the package of the class it starts with. */
    private static void claim(Info info) {
        int dot = info.entry.lastIndexOf('.');
        if (dot <= 0) return;
        String prefix = info.entry.substring(0, dot + 1);
        // not a package the app, Android or the mod itself has classes in
        for (String taken : new String[] {"cat.narezany.margyt.", "android.", "androidx.", "java.", "kotlin.", "com.ss.", "com.bytedance.", "X."}) {
            if (prefix.startsWith(taken)) return;
        }
        String before = PACKAGES.put(prefix, info.id);
        // two plugins in one package cannot be told apart by a class name
        if (before != null && !before.equals(info.id)) PACKAGES.put(prefix, before + " / " + info.id);
    }

    /**
     * Which plugin a class belongs to, or null when it is nobody's. Asked
     * when the app has stalled, about the classes that were running.
     */
    static String owner(String className) {
        if (className == null || PACKAGES.isEmpty()) return null;
        for (java.util.Map.Entry<String, String> one : PACKAGES.entrySet()) {
            if (className.startsWith(one.getKey())) return one.getValue();
        }
        return null;
    }

    public static final class Info {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String description;
        public final String entry;
        public final int minApi;
        /** The TikTok it was written against, or empty for any. */
        public final String tiktok;
        public final File folder;

        /** Why it is not running, or null when it is fine. */
        public String trouble;
        public String safety;

        private Bitmap icon;
        private boolean iconRead;

        Info(String id, String name, String version, String author, String description,
             String entry, int minApi, String tiktok, File folder) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
            this.description = description;
            this.entry = entry;
            this.minApi = minApi;
            this.tiktok = tiktok;
            this.folder = folder;
        }

        /** The icon, read once and kept; null when the plugin ships without one. */
        public Bitmap icon() {
            if (!iconRead) {
                iconRead = true;
                File file = new File(folder, ICON);
                if (file.isFile()) {
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(),options);
                    if(options.outWidth>0&&options.outHeight>0&&options.outWidth<=2048&&options.outHeight<=2048){
                        options.inJustDecodeBounds=false;options.inSampleSize=1;
                        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>128)options.inSampleSize*=2;
                        icon=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
                    }
                }
            }
            return icon;
        }
    }

    /** One installed plugin, with the instance when it is running. */
    private static final class Live {
        final Info info;
        MargyPlugin instance;
        /** The last instance that ran. It stays after a drop so the settings window still opens. */
        MargyPlugin settings;

        Live(Info info) {
            this.info = info;
        }
    }

    private static final List<Live> installed = new ArrayList<>();
    private static volatile MargyPlugin[] running = new MargyPlugin[0];
    private static boolean read;

    // -------------------------------------------------------------- reading

    private static File home(Context context) {
        File folder = new File(context.getFilesDir(), FOLDER);
        if (!folder.isDirectory()) folder.mkdirs();
        return folder;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Everything installed, whether it runs or not. */
    public static synchronized List<Info> list() {
        Context context = Margy.context();
        if (context == null) return Collections.emptyList();
        if (!read) scan(context);
        List<Info> out = new ArrayList<>();
        for (Live live : installed) out.add(live.info);
        return out;
    }

    public static boolean isEnabled(String id) {
        Context context = Margy.context();
        if (context == null) return false;
        return prefs(context).getBoolean(id, false);
    }

    public static synchronized boolean openSettings(String id,Activity activity) {
        for(Live live:installed) if(live.info.id.equals(id)) {
            MargyPlugin target=live.instance!=null?live.instance:live.settings;
            // Enabled, but never started in this process (the start was skipped or failed
            // earlier): start it now rather than claiming it has no settings.
            if(target==null && isEnabled(id)) {
                Context context=Margy.context();
                if(context!=null && start(context,live)) {publish();target=live.instance;}
            }
            if(target==null)return false;
            try {
                if(target.onOpenSettings(activity))return true;
                // Compatibility for older plugins exposing a public settings method.
                for(String name:new String[]{"openSettings","showSettings","showSettingsDialog"}) {
                    for(Class<?> argument:new Class<?>[]{Activity.class,Context.class}) {
                        try {
                            java.lang.reflect.Method method=target.getClass().getMethod(name,argument);
                            if(method.getReturnType()!=Void.TYPE && method.getReturnType()!=Boolean.TYPE)continue;
                            Object result=method.invoke(target,activity);
                            if(method.getReturnType()==Void.TYPE || Boolean.TRUE.equals(result))return true;
                        } catch(NoSuchMethodException absent){}
                    }
                }
            } catch(Throwable error){Diary.note("plugin settings "+id+": "+error);live.info.trouble=String.valueOf(error);}
            return false;
        }
        return false;
    }

    /** True while the plugin has a live instance in this process. */
    public static synchronized boolean isRunning(String id) {
        for(Live live:installed) if(live.info.id.equals(id))return live.instance!=null;
        return false;
    }

    /** Why the plugin is not running, or null. */
    public static synchronized String problem(String id) {
        for(Live live:installed) if(live.info.id.equals(id))return live.info.trouble;
        return null;
    }

    /** Start an enabled plugin again, for instance after it was dropped for throwing. */
    public static synchronized boolean revive(String id) {
        Context context=Margy.context();
        if(context==null || !isEnabled(id))return false;
        if(!read)scan(context);
        for(Live live:installed) if(live.info.id.equals(id)) {
            live.instance=null;
            boolean ok=start(context,live);
            publish();
            return ok;
        }
        return false;
    }

    /**
     * Switch a plugin on or off. It starts at once; stopping it unloads
     * nothing, because a class loaded into a process stays there -- the hooks
     * simply stop being called, and the next start of the app is clean.
     */
    public static synchronized void setEnabled(String id, boolean enabled) {
        Context context = Margy.context();
        if (context == null) return;
        prefs(context).edit().putBoolean(id, enabled).apply();
        if (!read) scan(context);
        for (Live live : installed) {
            if (!live.info.id.equals(id)) continue;
            if (enabled && live.instance == null) {
                start(context, live);
            } else if (!enabled && live.instance != null) {
                MargyPlugin instance = live.instance;
                live.instance = null;
                try {
                    instance.onStop();
                } catch (Throwable error) {
                    Diary.note("plugin " + id + " threw on stop: " + error);
                }
            }
            break;
        }
        publish();
    }

    private static void scan(Context context) {
        read = true;
        java.util.Map<String,Live> previous=new java.util.HashMap<String,Live>();
        for(Live live:installed)previous.put(live.info.id,live);
        installed.clear();
        File[] folders = home(context).listFiles();
        if (folders == null) return;
        for (File folder : folders) {
            if (!folder.isDirectory()) continue;
            Info info = readManifest(folder);
            if (info != null) {Live live=new Live(info);Live old=previous.get(info.id);if(old!=null){live.instance=old.instance;live.settings=old.settings!=null?old.settings:old.instance;}installed.add(live);}
        }
    }

    private static Info readManifest(File folder) {
        File manifest = new File(folder, MANIFEST);
        if (!manifest.isFile()) return null;
        try {
            JSONObject json = new JSONObject(new String(PluginAudit.read(manifest,65536),
                    "UTF-8"));
            String id = json.optString("id", "");
            if(!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))return null;
            Info info = new Info(
                    id,
                    localised(json, "name", id),
                    json.optString("version", "?"),
                    json.optString("author", "?"),
                    localised(json, "description", ""),
                    json.optString("entry", ""),
                    json.optInt("min_api", 1),
                    json.optString("tiktok", ""),
                    folder);
            if (!info.entry.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) info.trouble = "manifest has no valid entry class";
            if (info.minApi > MargyPlugin.API) {
                info.trouble = "wants TokkiYou plugin api " + info.minApi
                        + ", this one is " + MargyPlugin.API;
            }
            // a plugin that names the TikTok it was written against is not run
            // on another one. Everything a plugin reaches into is renamed
            // between releases, so a plugin that guesses wrong does not fail
            // politely -- it fails in the middle of somebody's feed.
            if (info.tiktok.length() > 0 && !info.tiktok.equals(Version.TIKTOK)) {
                info.trouble = "written for TikTok " + info.tiktok
                        + ", this is " + Version.TIKTOK;
            }
            Context current=Margy.context();if(current!=null)info.safety=prefs(current).getString("audit:"+info.id,"");
            return info;
        } catch (Throwable error) {
            Diary.note("plugin in " + folder.getName() + " has a bad manifest: " + error);
            return null;
        }
    }

    /** `name_ru` before `name`, so a plugin can speak the phone's language. */
    private static String localised(JSONObject json, String field, String fallback) {
        String language = Locale.getDefault().getLanguage();
        String translated = json.optString(field + "_" + language, "");
        if (!translated.isEmpty()) return translated;
        return json.optString(field, fallback);
    }

    // ------------------------------------------------------------- starting

    /**
     * Load and start everything that is switched on.
     *
     * Called from the mod's start-up provider, before TikTok's own onCreate.
     */
    public static synchronized void startAll(Context context) {
        ScriptPlugins.reload(context);
        if (!read) scan(context);
        int started = 0;
        for (Live live : installed) {
            if (!isEnabled(live.info.id) || live.info.trouble != null) continue;
            if (start(context, live)) started++;
        }
        publish();
        if (!installed.isEmpty()) {
            Diary.note("plugins: " + started + " of " + installed.size() + " running");
        }
    }

    private static boolean start(Context context, Live live) {
        Info info = live.info;
        try {
            File dex = new File(info.folder, DEX);
            if (!dex.isFile()) {
                info.trouble = "no " + DEX + " in the plugin";
                return false;
            }
            PluginAudit.Report inspection=PluginAudit.folder(info.folder);
            info.safety=inspection.summary();
            // Android 14 refuses to load a dex anyone could still write to
            if (dex.canWrite()) dex.setReadOnly();

            ClassLoader loader = new DexClassLoader(
                    dex.getAbsolutePath(),
                    context.getCodeCacheDir().getAbsolutePath(),
                    null,
                    Plugins.class.getClassLoader());

            Class<?> type = loader.loadClass(info.entry);
            Object made = type.getDeclaredConstructor().newInstance();
            if (!(made instanceof MargyPlugin)) {
                info.trouble = info.entry + " does not extend MargyPlugin";
                return false;
            }
            MargyPlugin plugin = (MargyPlugin) made;
            plugin.attach(new PluginContext(context.getApplicationContext(), info.id,
                    info.folder, new PluginContext.Diarist() {
                @Override
                public void note(String line) {
                    Diary.note(line);
                }
            }));
            plugin.onStart(context);
            claim(info);
            live.instance = plugin;
            live.settings = plugin;
            info.trouble = null;
            return true;
        } catch (Throwable error) {
            info.trouble = String.valueOf(error);
            Diary.note("plugin " + info.id + " would not start: " + error);
            return false;
        }
    }

    /** The running set, as an array the hot hooks can walk without locking. */
    private static void publish() {
        List<MargyPlugin> live = new ArrayList<>();
        for (Live one : installed) {
            if (one.instance != null) live.add(one.instance);
        }
        running = live.toArray(new MargyPlugin[0]);
    }

    /**
     * A plugin that throws is dropped rather than asked again.
     *
     * The alternative is the same exception on every frame, which is not a
     * plugin misbehaving any more, it is TikTok not working.
     */
    private static synchronized void drop(MargyPlugin plugin, Throwable error) {
        for (Live live : installed) {
            if (live.instance != plugin) continue;
            Diary.note("plugin " + live.info.id + " threw, dropped: " + error);
            live.info.trouble = String.valueOf(error);
            live.instance = null;
            break;
        }
        publish();
    }

    /** Whether anything at all is running, for the hooks on the hot paths. */
    public static boolean anyRunning() {
        return running.length > 0;
    }

    // ---------------------------------------------------------- the sending

    public static void onActivityCreated(Activity activity) {
        for (MargyPlugin plugin : running) {
            try {
                plugin.onActivityCreated(activity);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }

    public static void onActivityResumed(Activity activity) {
        for (MargyPlugin plugin : running) {
            try {
                plugin.onActivityResumed(activity);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }

    public static void onActivityPaused(Activity activity) {
        for (MargyPlugin plugin : running) {
            try {
                plugin.onActivityPaused(activity);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }
    public static void accountChanged(String uid) {
        for(MargyPlugin plugin:running)try{plugin.onAccountChanged(uid);}catch(Throwable error){drop(plugin,error);}
    }
    public static void settingsChanged(String id,String key) {
        MargyPlugin plugin=null;
        synchronized(Plugins.class){for(Live live:installed)if(id.equals(live.info.id)){plugin=live.instance;break;}}
        if(plugin!=null)try{plugin.onSettingsChanged(key);}catch(Throwable error){drop(plugin,error);}
    }

    /** In the drawing path: the empty case has to cost nothing. */
    // ------------------------------------------------ what plugins are told

    /** A screen came up; every plugin hears about it by name. */
    public static void screen(Activity activity) {
        MargyPlugin[] plugins = running;
        if (activity == null || plugins.length == 0) return;
        String name = activity.getClass().getName();
        for (MargyPlugin plugin : plugins) {
            try {
                plugin.onScreen(activity, name);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
    }

    /** A page of the feed, passed through every plugin in turn. */
    public static java.util.List feed(java.util.List posts) {
        MargyPlugin[] plugins = running;
        if (plugins.length == 0) return posts;
        java.util.List out = posts;
        for (MargyPlugin plugin : plugins) {
            try {
                java.util.List said = plugin.onFeed(out);
                if (said != null) out = said;
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
        return out;
    }

    /** A name about to be drawn, before the mod's own badges go on it. */
    public static String name(String uid, String name) {
        name=ScriptPlugins.name(uid,name);
        MargyPlugin[] plugins = running;
        if (plugins.length == 0) return name;
        String out = name;
        for (MargyPlugin plugin : plugins) {
            try {
                String said = plugin.onName(uid, out);
                if (said != null) out = said;
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
        return out;
    }

    /**
     * One entry a plugin has put into the mod's settings.
     *
     * Besides a plain tappable row there are switches, lists of choices,
     * sliders and small headings. Values of the first three live in the
     * plugin's own preferences under `key`, so the plugin reads them back with
     * `margyt().prefs()` and the mod never has to know what they mean.
     */
    public static final class Row {
        public static final int ACTION = 0, TOGGLE = 1, CHOICE = 2, SLIDER = 3, SECTION = 4;

        public final String plugin;
        public final String title;
        public final String detail;
        public final cat.narezany.margyt.plugin.PluginContext.Tapped action;
        public final int kind;
        public final String key;
        public final String[] options;
        public final int min, max, def;

        Row(String plugin, String title, String detail,
            cat.narezany.margyt.plugin.PluginContext.Tapped action) {
            this(plugin, title, detail, action, ACTION, null, null, 0, 0, 0);
        }

        Row(String plugin, String title, String detail,
            cat.narezany.margyt.plugin.PluginContext.Tapped action,
            int kind, String key, String[] options, int min, int max, int def) {
            this.plugin = plugin;
            this.title = title;
            this.detail = detail;
            this.action = action;
            this.kind = kind;
            this.key = key;
            this.options = options;
            this.min = min;
            this.max = max;
            this.def = def;
        }
    }

    private static final java.util.List<Row> rows = new java.util.ArrayList<Row>();

    private static void add(Row row) {
        if (row.plugin == null || row.title == null) return;
        synchronized (rows) {
            for (Row have : rows) {
                if (have.plugin.equals(row.plugin) && have.title.equals(row.title)
                        && have.kind == row.kind) return;
            }
            rows.add(row);
        }
    }

    public static void addRow(String plugin, String title, String detail,
                              cat.narezany.margyt.plugin.PluginContext.Tapped action) {
        add(new Row(plugin, title, detail, action));
    }

    public static void addSection(String plugin, String title) {
        add(new Row(plugin, title, null, null, Row.SECTION, null, null, 0, 0, 0));
    }

    public static void addToggle(String plugin, String title, String detail,
                                 String key, boolean def) {
        if (key == null) return;
        add(new Row(plugin, title, detail, null, Row.TOGGLE, key, null, 0, 0, def ? 1 : 0));
    }

    public static void addChoice(String plugin, String title, String detail,
                                 String key, String[] options, int def) {
        if (key == null || options == null || options.length == 0) return;
        add(new Row(plugin, title, detail, null, Row.CHOICE, key, options.clone(),
                0, options.length - 1, def));
    }

    public static void addSlider(String plugin, String title, String detail,
                                 String key, int min, int max, int def) {
        if (key == null || max <= min) return;
        add(new Row(plugin, title, detail, null, Row.SLIDER, key, null, min, max, def));
    }

    public static java.util.List<Row> rows() {
        synchronized (rows) {
            return new java.util.ArrayList<Row>(rows);
        }
    }

    public static int colour(int colour) {
        colour=ScriptPlugins.colour(colour);
        MargyPlugin[] plugins = running;
        if (plugins.length == 0) return colour;
        for (MargyPlugin plugin : plugins) {
            try {
                colour = plugin.onColour(colour);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
        return colour;
    }

    public static String region(String key, String value) {
        MargyPlugin[] plugins = running;
        if (plugins.length == 0) return value;
        for (MargyPlugin plugin : plugins) {
            try {
                value = plugin.onRegion(key, value);
            } catch (Throwable error) {
                drop(plugin, error);
            }
        }
        return value;
    }

    // ------------------------------------------------------- installing one

    /**
     * Unpack a `.mtp` the person picked, keyed by the id in its manifest.
     *
     * Installing over an existing id replaces it, which is how a plugin is
     * updated. Returns the id, or throws with something worth showing.
     */
    public static synchronized PluginAudit.Report inspectInstalled(String id)throws Exception{
        for(Info info:list())if(info.id.equals(id))return PluginAudit.folder(info.folder);
        throw new Exception(Local.of("Плагин не найден"));
    }
    public static void approve(Context context,String id,String digest){prefs(context).edit().putString("approved:"+id,digest).apply();}
    public static PluginAudit.Report inspect(Context context,Uri source)throws Exception {
        File staging=new File(context.getCacheDir(),"mtp-review-"+java.util.UUID.randomUUID());
        try {unpack(context,source,staging);Info info=readManifest(staging);if(info==null)throw new Exception(Local.of("Некорректный manifest.json"));PluginAudit.Report report=PluginAudit.folder(staging);report.pluginId=info.id;return report;}finally{remove(staging);}
    }
    public static synchronized String install(Context context, Uri source) throws Exception {return install(context,source,null);}
    public static synchronized String install(Context context, Uri source,String expectedDigest) throws Exception {
        File staging = new File(context.getCacheDir(), "mtp-" + System.currentTimeMillis());
        try {
            unpack(context, source, staging);

            Info info = readManifest(staging);
            if (info == null) throw new Exception("no readable " + MANIFEST + " in the plugin");
            if (!new File(staging, DEX).isFile()) throw new Exception("no " + DEX + " in the plugin");
            if(info.trouble!=null)throw new Exception(info.trouble);
            if (info.minApi > MargyPlugin.API) {
                throw new Exception("the plugin wants api " + info.minApi
                        + " and this TokkiYou has " + MargyPlugin.API);
            }
            if (info.tiktok.length() > 0 && !info.tiktok.equals(Version.TIKTOK)) {
                throw new Exception("the plugin was written for TikTok " + info.tiktok
                        + " and this is " + Version.TIKTOK);
            }

            PluginAudit.Report report=PluginAudit.folder(staging);
            if(report.blocked)throw new Exception(report.summary());
            if(expectedDigest!=null && !expectedDigest.equals(report.digest))throw new Exception(Local.of("Плагин изменился после проверки"));
            setEnabled(info.id,false);
            File home = new File(home(context), safe(info.id));
            File backup=new File(context.getCacheDir(),"mtp-old-"+java.util.UUID.randomUUID());
            boolean had=home.exists();
            if(had&&!home.renameTo(backup))throw new Exception(Local.of("Не удалось сохранить предыдущий плагин"));
            try {
                if(!staging.renameTo(home)){copyFolder(staging,home);remove(staging);}
            }catch(Exception failed){remove(home);if(had&&!backup.renameTo(home))Diary.note("plugin rollback failed: "+info.id);throw failed;}
            remove(backup);
            prefs(context).edit().putBoolean(info.id,false).putString("audit:"+info.id,report.summary()).apply();
            read = false;
            Diary.note("plugin installed: " + info.id + " " + info.version);
            return info.id;
        } finally {
            remove(staging);
        }
    }

    /** Take a plugin off the phone. It stops being called at once. */
    public static synchronized void uninstall(Context context, String id) {
        setEnabled(id, false);
        prefs(context).edit().remove(id).apply();
        remove(new File(home(context), safe(id)));
        read = false;
    }

    private static void unpack(Context context, Uri source, File into) throws Exception {
        InputStream raw=context.getContentResolver().openInputStream(source);
        if(raw==null)throw new Exception(Local.of("Невозможно прочитать плагин"));
        PluginPackage.unpack(raw,into);
    }

    /** A file name that cannot be a path, an id that cannot be a folder. */
    private static String safe(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-'
                    ? c : '_');
        }
        String cleaned = out.toString();
        return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")
                ? "plugin" : cleaned;
    }

    private static void copyFolder(File from, File to) throws Exception {
        to.mkdirs();
        File[] files = from.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) continue;
            InputStream in = new FileInputStream(file);
            OutputStream out = new FileOutputStream(new File(to, file.getName()));
            try {
                copy(in, out);
            } finally {
                in.close();
                out.close();
            }
        }
    }

    private static void remove(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) remove(child);
        }
        file.delete();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
