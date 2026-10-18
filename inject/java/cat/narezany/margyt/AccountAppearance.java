package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;

/** Appearance is owned by a UID, never by the device or mutable username. */
public final class AccountAppearance {
    private static volatile String selected;
    private static volatile long revision;
    private AccountAppearance(){}
    /**
     * One set of settings, whoever is signed in.
     *
     * They used to be kept an account: switch accounts and the mod was set up
     * from nothing again, its row gone from where it had been put and every
     * colour back to TikTok's. Nobody wanted that -- the settings are how the
     * app is to look and behave on this phone, not something about the person
     * signed in. So there is one set now, and it starts as a copy of the set
     * the account last signed in had.
     */
    static final String SHARED = "shared";
    static String scope(Context context) {
        return SHARED;
    }
    /** The account whose own set the shared one is made from: the one last seen signed in. */
    private static String last(Context context) {
        String saved=context.getSharedPreferences(Margy.PREFS,0).getString(Account.KEY_ID,"");
        return valid(saved)?saved:"guest";
    }
    private static boolean valid(String uid){return uid!=null && uid.matches("[0-9]{1,24}");}
    static synchronized void select(String uid) {
        String next=valid(uid)?uid:"guest";
        if(next.equals(selected))return;
        final long change=++revision;
        Context context=Margy.context();
        if(context==null){selected=next;return;}
        if(selected==null && valid(context.getSharedPreferences(Margy.PREFS,0).getString(Account.KEY_ID,"")))prefs(context);
        selected=next;
        prefs(context);
        Accent.reload();AppearanceColors.reload();Fonts.reload();Themes.forget();LikeColors.reload();
        FeedRail.reloadSettings();
        FeedBlacklist.reload();PlayerTools.accountChanged();
        VideoActions.accountChanged();PinnedFriends.reload();ProfileLayout.reload();MessageTimes.reload();ModNotifications.reload();Repost.reload();
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            if (change != revision) return;
            Themes.repaintVisible();Accent.refreshLikeViews();
            android.app.Activity activity=Screen.now();
            if(activity instanceof SettingsActivity)((SettingsActivity)activity).accountAppearanceChanged();
            Plugins.accountChanged(uid);
        });
    }
    public static SharedPreferences prefs(Context context) {
        SharedPreferences own=context.getSharedPreferences("margyt_appearance_"+SHARED,0);
        if(!own.getBoolean("_initialized",false))synchronized(AccountAppearance.class) {
            if(!own.getBoolean("_initialized",false)) {
                SharedPreferences legacy=context.getSharedPreferences(Margy.PREFS,0);
                SharedPreferences.Editor edit=own.edit();
                String uid=last(context);
                SharedPreferences theirs=context.getSharedPreferences("margyt_appearance_"+uid,0);
                if(!theirs.getBoolean("_initialized",false)&&!uid.equals("guest")){
                    SharedPreferences guest=context.getSharedPreferences("margyt_appearance_guest",0);
                    if(guest.getBoolean("_initialized",false)){theirs=guest;uid="guest";}
                }
                if(theirs.getBoolean("_initialized",false)) {
                    // what that account had, and the files it had beside it
                    for(java.util.Map.Entry<String,?> entry:theirs.getAll().entrySet())copy(edit,entry.getKey(),entry.getValue());
                    File from=new File(context.getFilesDir(),"margyt/accounts/"+uid),to=new File(context.getFilesDir(),"margyt/accounts/"+SHARED);
                    File[] files=from.listFiles();
                    if(files!=null)for(File file:files)if(file.isFile())move(file,new File(to,file.getName()));
                    Diary.note("settings: one set for every account now, taken from "+(uid.equals("guest")?"the signed-out one":"the account last signed in"));
                } else if(!legacy.getBoolean("appearance_uid_migrated",false)) {
                    // older still: everything in one file with the rest of the mod's settings
                    for(java.util.Map.Entry<String,?> entry:legacy.getAll().entrySet())
                        if(appearanceKey(entry.getKey()))copy(edit,entry.getKey(),entry.getValue());
                    for(String path:new String[]{"margyt/font","margyt/emoji-emoji_file"}) {
                        String name=path.endsWith("emoji-emoji_file")?"emoji-custom":new File(path).getName();
                        move(new File(context.getFilesDir(),path),new File(context.getFilesDir(),"margyt/accounts/"+SHARED+"/"+name));
                    }
                    legacy.edit().putBoolean("appearance_uid_migrated",true).apply();
                }
                edit.putBoolean("_initialized",true).apply();
            }
        }
        return own;
    }
    /** A copy, in spite of the name: what an account had is left where it was. */
    private static void move(File from,File to) {
        if(!from.isFile() || to.exists())return;
        try {to.getParentFile().mkdirs();
            java.io.InputStream in=new java.io.FileInputStream(from);java.io.OutputStream out=new java.io.FileOutputStream(to);
            try {byte[] block=new byte[16384];int n;while((n=in.read(block))!=-1)out.write(block,0,n);}finally{in.close();out.close();}
        }catch(Throwable error){Diary.note("settings: could not bring a file over: "+error);}
    }
    static boolean appearanceKey(String key) {
        return key.equals("streak_send_minute") || key.startsWith("notify_") || key.startsWith("friends_") || key.equals("message_times") || key.startsWith("repost_") || key.equals("profile_layout") || key.startsWith("rail_") || key.startsWith("player_") || key.startsWith("dl_") || key.equals("frame_quality") || key.startsWith("feed_blocked_") || key.equals("accent") || key.equals("accent_custom") || key.startsWith("appearance_color_") || key.startsWith("theme_")
            || key.equals("font") || key.equals("font_emoji") || key.equals("like_color") || key.equals("comment_like_color")
            || key.startsWith("profile_gradient_") || key.startsWith("profile_nickname_")
            || key.startsWith("profile_badge_") || key.startsWith("profile_crown_") || key.startsWith("profile_share") || key.equals("profile_display_name");
    }
    private static void copy(SharedPreferences.Editor edit,String key,Object value) {
        if(value instanceof String)edit.putString(key,(String)value);
        else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
        else if(value instanceof Integer)edit.putInt(key,(Integer)value);
        else if(value instanceof Long)edit.putLong(key,(Long)value);
        else if(value instanceof Float)edit.putFloat(key,(Float)value);
    }
    static File file(Context context,String name) {
        return new File(context.getFilesDir(),"margyt/accounts/"+scope(context)+"/"+name);
    }
}
