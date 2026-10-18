package cat.narezany.tokkiyou;
import java.util.Calendar;import android.content.Context;
/** A local account schedule. Sending always still requires a fresh waiting streak. */
final class StreakSchedule {
 static final String KEY="streak_send_minute";
 static int minute(){try{int v=AccountAppearance.prefs(TokkiYou.context()).getInt(KEY,-1);return v>=0&&v<1440?v:-1;}catch(Throwable ignored){return -1;}}
 static boolean due(long now,int minute){if(minute<0)return true;Calendar c=Calendar.getInstance();c.setTimeInMillis(now);return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE)>=minute;}
 static long delay(long now,long cadence){int minute=minute();if(minute<0)return cadence;Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,minute/60);c.set(Calendar.MINUTE,minute%60);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);long until=c.getTimeInMillis()-now;return until>0?Math.max(1000,Math.min(cadence,until)):cadence;}
 /** The next moment worth waking for: today's time if still ahead, a short retry while sends are pending, else tomorrow's time. */
 static long next(long now,int minute,boolean retry,long cadence){if(minute<0)return now+cadence;Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,minute/60);c.set(Calendar.MINUTE,minute%60);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);long today=c.getTimeInMillis();if(now<today)return today;if(retry&&now<today+3*60*60*1000L)return now+5*60*1000L;c.add(Calendar.DAY_OF_YEAR,1);return c.getTimeInMillis();}
 static boolean sameDay(long a,long b){if(b<=0)return false;Calendar x=Calendar.getInstance(),y=Calendar.getInstance();x.setTimeInMillis(a);y.setTimeInMillis(b);return x.get(Calendar.ERA)==y.get(Calendar.ERA)&&x.get(Calendar.YEAR)==y.get(Calendar.YEAR)&&x.get(Calendar.DAY_OF_YEAR)==y.get(Calendar.DAY_OF_YEAR);}
 static int parse(String value){if(value==null||!value.matches("[0-9]{1,2}:[0-9]{2}"))return -2;String[] p=value.split(":");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);return h<24&&m<60?h*60+m:-2;}
 static String label(){int m=minute();return m<0?Local.of("Когда серия ждёт продления"):String.format(java.util.Locale.ROOT,Local.of("В %02d:%02d"),m/60,m%60);}
 static void choose(Context c,Runnable changed){Skin skin=Skin.remembered(c);Panel p=Panel.with(c,skin,Local.of("Время автопродления"));p.text(Local.of("В выбранное время устройства — одна отправка в день друзьям с действующим огоньком. Для точного запуска в фоне разрешите будильники и работу без ограничений батареи."));int m=minute();TimeWheel wheel=new TimeWheel(c,skin,m<0?1200:m);p.view(wheel);p.primaryKeepOpen(Local.of("Сохранить"),()->{set(wheel.minuteOfDay());Streaks.setEnabled(true);StreakAlarm.permissions(c);p.close();if(changed!=null)changed.run();});p.quiet(Local.of("Без расписания"),()->{set(-1);if(changed!=null)changed.run();});p.quiet(Text.CLOSE,null);p.show();}
 static void set(int minute){AccountAppearance.prefs(TokkiYou.context()).edit().putInt(KEY,minute).apply();android.content.Context c=TokkiYou.context();if(c!=null){StreakAlarm.schedule(c,false);Streaks.round(c);}}
}
