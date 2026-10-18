package cat.narezany.tokkiyou;
import android.app.Notification;import android.app.NotificationManager;import android.content.*;
public final class ModNotifications {
 static final String MESSAGES="notify_messages",PROMOS="notify_promos",OTHER="notify_other",NO_PROMPTS="notify_no_prompts";
 private static volatile boolean[] options;
 static boolean option(int i){boolean[] values=options;if(values==null){values=new boolean[]{true,true,true,false};try{android.content.SharedPreferences p=AccountAppearance.prefs(TokkiYou.context());String[] keys={MESSAGES,PROMOS,OTHER,NO_PROMPTS};for(int n=0;n<4;n++)values[n]=p.getBoolean(keys[n],values[n]);}catch(Throwable ignored){}if(TokkiYou.context()!=null)options=values;}return values[i];}
 static void reload(){options=null;}
 static void set(String key,boolean on){android.content.SharedPreferences.Editor edit=AccountAppearance.prefs(TokkiYou.context()).edit().putBoolean(key,on);if(!on&&!NO_PROMPTS.equals(key))edit.putBoolean(NO_PROMPTS,true);edit.apply();reload();}
 public static boolean noPrompts(){return option(3);}
 static boolean allow(Notification notification){if(notification==null)return false;return option(Notification.CATEGORY_MESSAGE.equals(notification.category)?0:Notification.CATEGORY_PROMO.equals(notification.category)?1:2);}
 public static void notify(NotificationManager manager,int id,Notification notification){if(manager==null||!allow(notification))return;try{manager.notify(id,notification);}catch(SecurityException error){Diary.note("notifications: system permission denied");}}
 public static void notify(NotificationManager manager,String tag,int id,Notification notification){if(manager==null||!allow(notification))return;try{manager.notify(tag,id,notification);}catch(SecurityException error){Diary.note("notifications: system permission denied");}}
 static void settings(Context c){try{Intent intent;if(android.os.Build.VERSION.SDK_INT>=26){intent=new Intent("android.settings.APP_NOTIFICATION_SETTINGS");intent.putExtra("android.provider.extra.APP_PACKAGE",c.getPackageName());}else{intent=new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",android.net.Uri.parse("package:"+c.getPackageName()));}c.startActivity(intent);}catch(Throwable error){Diary.note("notification settings: "+error);Screen.say(Text.PLAYER_NOT_READY);}}
}
