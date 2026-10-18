package cat.narezany.margyt;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Plugins somebody has put in the store, and a button to take one.
 *
 * A screen of its own rather than another heading in the settings: the list
 * comes from the network, every row has a picture and three lines of its own,
 * and folding all that under a chevron in a page that is already long is how
 * something ends up never being opened.
 *
 * What the store knows about a plugin is what that plugin's own manifest says
 * -- the server reads it out of the packed file rather than taking anybody's
 * word for it -- so the name here is the name the phone will load.
 */
public class StoreActivity extends Activity {

    private Skin skin;
    private LinearLayout column;
    private List<Plugin> offered = new ArrayList<Plugin>();
    private String trouble = "";
    private String endpoint="";
    private int generation;
    private boolean loading;
    private final java.util.concurrent.ThreadPoolExecutor icons=new java.util.concurrent.ThreadPoolExecutor(2,2,15,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(64),r->{Thread t=new Thread(r,"margyt-store-icon");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());

    /** One plugin, as the store describes it. */
    private static final class Plugin {
        String id = "", name = "", version = "", author = "", about = "", tiktok = "";
        String url = "", icon = "", sha256="";
        long size;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Margy.attach(this);
        skin = Skin.remembered(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(skin.page);

        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, statusBar(), 0, dp(32));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll);
        setContentView(root);

        try {
            getWindow().setStatusBarColor(skin.page);
            getWindow().setNavigationBarColor(skin.page);
        } catch (Throwable ignored) {
        }

        rebuild();
        fetch();
    }

    // ------------------------------------------------------------ the list

    private void fetch() {
        endpoint=PluginCatalog.endpoint(this);
        final int token=++generation;
        icons.getQueue().clear();
        final String catalog=endpoint;
        if(catalog.isEmpty()){offered.clear();trouble=Local.of("Каталог ещё не подключён");loading=false;rebuild();return;}
        loading=true;
        Net.away("store", new Runnable() {
            @Override
            public void run() {
                final List<Plugin> found = new ArrayList<Plugin>();
                String trouble = "";
                try {
                    byte[] raw = Net.bytes(catalog);
                    if (raw == null) {
                        trouble = Text.STORE_OFFLINE;
                    } else {
                        JSONArray list = new JSONObject(new String(raw, "UTF-8"))
                                .optJSONArray("plugins");
                        for (int i = 0; list != null && i < Math.min(100,list.length()); i++) {
                            JSONObject one = list.optJSONObject(i);
                            if (one == null) continue;
                            Plugin plugin = new Plugin();
                            plugin.id = one.optString("id", "");
                            plugin.name = shortText(one.optString("name", plugin.id),80);
                            plugin.version = shortText(one.optString("version", ""),32);
                            plugin.author = shortText(one.optString("author", ""),80);
                            plugin.about = shortText(one.optString("about", ""),240);
                            plugin.tiktok = one.optString("tiktok", "");
                            plugin.sha256=one.optString("sha256","");
                            // a hash is checked when the catalog gives one; ours gave none before 2.0
                            if(!PluginCatalog.id(plugin.id)||(plugin.sha256.length()>0&&!PluginCatalog.digest(plugin.sha256)))continue;
                            try {
                                plugin.url=PluginCatalog.resolve(catalog,one.optString("url",""));
                                String icon=one.optString("icon","");
                                plugin.icon=icon.isEmpty()?"":PluginCatalog.resolve(catalog,icon);
                            }catch(Exception invalid){continue;}
                            plugin.size = one.optLong("size", 0);
                            found.add(plugin);
                        }
                    }
                } catch (Throwable error) {
                    trouble = String.valueOf(error);
                    Diary.note("store: " + error);
                }
                final String said = trouble;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(isFinishing()||token!=generation)return;
                        loading=false;
                        offered = found;
                        StoreActivity.this.trouble = said;
                        rebuild();
                    }
                });
            }
        });
    }

    private void rebuild() {
        column.removeAllViews();
        column.addView(back());
        column.addView(title(Text.STORE));
        TextView connect=new TextView(this);connect.setText(endpoint.isEmpty()?Local.of("Подключить каталог"):Local.of("Каталог · изменить адрес"));connect.setTextColor(Accent.colour());connect.setTextSize(14);connect.setPadding(skin.margin,dp(8),skin.margin,dp(16));Fonts.apply(connect);Motion.press(connect);connect.setOnClickListener(v->configureCatalog());column.addView(connect);
        if(!endpoint.isEmpty()){TextView refresh=new TextView(this);refresh.setText(loading?Local.of("Обновление…"):Local.of("Обновить"));refresh.setTextColor(skin.text);refresh.setPadding(skin.margin,dp(8),skin.margin,dp(12));Fonts.apply(refresh);refresh.setOnClickListener(v->{if(!loading){fetch();rebuild();}});column.addView(refresh);}

        LinearLayout card = card();
        if (offered.isEmpty()) {
            card.addView(quiet(trouble.length() > 0 ? trouble : loading?Text.STORE_WAIT:Local.of("В каталоге пока пусто")));
        } else {
            for (int i = 0; i < offered.size(); i++) {
                if (i > 0) card.addView(line());
                card.addView(row(offered.get(i)));
            }
        }
        column.addView(wrap(card));
        column.addView(quiet(Local.of("Скачанные плагины остаются выключенными, пока вы их не включите.")));
    }

    private View row(final Plugin plugin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        ImageView picture = new ImageView(this);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(44), dp(44));
        size.rightMargin = dp(14);
        row.addView(picture, size);
        paint(picture, plugin.icon);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(plugin.name + "  " + plugin.version);
        name.setTextColor(skin.text);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.addView(name);

        TextView under = new TextView(this);
        String said = plugin.author;
        if (plugin.about.length() > 0) said += "  ·  " + plugin.about;
        under.setText(said);
        under.setTextColor(skin.muted());
        under.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.addView(under);

        boolean fits = plugin.tiktok.length() == 0 || plugin.tiktok.equals(Version.TIKTOK);
        if (!fits) {
            TextView warn = new TextView(this);
            warn.setText(Text.STORE_WRONG_VERSION + "  ·  " + plugin.tiktok);
            warn.setTextColor(skin.muted());
            warn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            text.addView(warn);
        }

        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView get = new TextView(this);
        get.setText(fits ? Text.STORE_GET : Text.STORE_ANYWAY);
        get.setTextColor(fits ? onAccent() : skin.text);
        get.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        get.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(12));
        if (fits) {
            pill.setColor(Accent.colour());
        } else {
            pill.setColor(0x00000000);
            pill.setStroke(dp(1), skin.muted());
        }
        get.setBackground(pill);
        get.setEnabled(fits);
        Fonts.apply(name);Fonts.apply(under);Fonts.apply(get);Motion.press(get);
        get.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                install(plugin);
            }
        });
        row.addView(get);
        return row;
    }

    private void paint(final ImageView view, final String path) {
        if (path == null || path.length() == 0) {
            view.setImageBitmap(Badges.note());
            return;
        }
        final int token=generation;
        icons.execute(new Runnable() {
            @Override
            public void run() {
                if(token!=generation||isFinishing())return;
                final byte[] raw = Net.bytes(path);
                if (raw == null) return;
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);
                if(options.outWidth<=0||options.outHeight<=0||options.outWidth>2048||options.outHeight>2048)return;
                options.inJustDecodeBounds=false;options.inSampleSize=1;
                while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>128)options.inSampleSize*=2;
                final Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.length,options);
                if (bitmap == null) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(token==generation&&!isFinishing()&&view.isAttachedToWindow())view.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    // --------------------------------------------------------- taking one

    private void install(final Plugin plugin) {
        final android.content.Context app=getApplicationContext();
        installTask(app,plugin);
    }
    private static void installTask(final android.content.Context app,final Plugin plugin){
        final String job="plugin:"+plugin.id;
        synchronized(activeDownloads){if(!activeDownloads.add(plugin.id))return;}
        final DownloadTasks.Control control=DownloadTasks.begin(job,plugin.name.isEmpty()?plugin.id:plugin.name,Local.of("Магазин плагинов"),()->installTask(app,plugin));
        control.limit=18L*1024*1024;
        Screen.progress(job,Text.STORE_GETTING, 0);
        Net.away("store: " + plugin.id, new Runnable() {
            @Override
            public void run() {
                final File into = new File(app.getCacheDir(), plugin.id + ".mtp");
                boolean installed=false;
                try {
                boolean got = Net.download(plugin.url, into,
                        new Net.Along() {
                            @Override
                            public void at(int percent, long some, long all) {
                                DownloadTasks.progress(job,percent);Screen.progress(job,Text.STORE_GETTING, percent);
                            }
                        },control);
                final boolean ok = got && put(app,into,plugin);
                installed=ok;
                // the settings screen under this one offers the restart when it comes back
                if(ok)SettingsActivity.restartOwed();
                if(!control.cancelled())Screen.say(ok ? Text.STORE_GOT : Text.STORE_FAILED);
                } finally {DownloadTasks.finish(job,installed);into.delete();if(installed)Screen.progressDone(job);else Screen.progressGone(job);
                    synchronized(activeDownloads){activeDownloads.remove(plugin.id);}}
            }
        });
    }

    private static final java.util.Set<String> activeDownloads=new java.util.HashSet<String>();
    private static boolean put(android.content.Context app,File file,Plugin expected) {
        try {
            if(expected.sha256.length()>0&&!PluginAudit.sha256(PluginAudit.read(file,18*1024*1024)).equalsIgnoreCase(expected.sha256))throw new Exception(Local.of("SHA-256 файла не совпадает с каталогом"));
            PluginAudit.Report report=Plugins.inspect(app,android.net.Uri.fromFile(file));
            if(!expected.id.equals(report.pluginId))throw new Exception(Local.of("ID плагина отличается от каталога"));
            if(report.blocked)throw new Exception(report.summary());
            Plugins.install(app, android.net.Uri.fromFile(file),report.digest);
            return true;
        } catch (Throwable error) {
            Diary.note("store: " + error);Screen.say(String.valueOf(error.getMessage()));
            return false;
        }
    }

    private void configureCatalog(){
        android.widget.EditText field=new android.widget.EditText(this);field.setText(PluginCatalog.endpoint(this));field.setHint("https://…/catalog.json");field.setTextColor(skin.text);field.setSingleLine(true);Fonts.apply(field);
        Panel.with(this,skin,Local.of("Каталог плагинов")).text(Local.of("Каталог TokkiYou подключён по умолчанию. Можно указать другой HTTPS JSON-каталог; пустой адрес отключает магазин.")).view(field).primary(Local.of("Сохранить"),()->{
            try{PluginCatalog.endpoint(this,field.getText().toString());fetch();rebuild();}catch(Exception invalid){Screen.say(invalid.getMessage());}
        }).quiet(Local.of("Назад"),null).show();
    }
    private static String shortText(String value,int max){return value.length()>max?value.substring(0,max):value;}
    @Override protected void onDestroy(){generation++;icons.shutdownNow();super.onDestroy();}

    // ------------------------------------------------------------- the look

    private View back() {
        TextView view = new TextView(this);
        view.setText("←");
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        view.setPadding(skin.margin, dp(14), skin.margin, dp(6));
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        if(view instanceof TextView)Fonts.apply((TextView)view);
        return view;
    }

    private View title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(skin.text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(skin.margin, dp(8), skin.margin, dp(20));
        if(view instanceof TextView)Fonts.apply((TextView)view);
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(skin.card);
        background.setCornerRadius(skin.radius);
        card.setBackground(background);
        return card;
    }

    private View wrap(View card) {
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(skin.margin, 0, skin.margin, dp(8));
        holder.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return holder;
    }

    private View line() {
        View view = new View(this);
        view.setBackgroundColor(skin.muted() & 0x33FFFFFF);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        if(view instanceof TextView)Fonts.apply((TextView)view);
        return view;
    }

    private TextView quiet(String said) {
        TextView view = new TextView(this);
        view.setText(said);
        view.setTextColor(skin.muted());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(skin.margin + dp(4), dp(12), skin.margin + dp(4), dp(12));
        if(view instanceof TextView)Fonts.apply((TextView)view);
        return view;
    }

    private int onAccent() {
        int colour = Accent.colour();
        int light = ((colour >> 16) & 0xFF) * 299 + ((colour >> 8) & 0xFF) * 587
                + (colour & 0xFF) * 114;
        return light / 1000 > 140 ? 0xFF10221F : 0xFFFFFFFF;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int statusBar() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }
}
