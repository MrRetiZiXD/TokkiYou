package cat.narezany.margyt;

import android.content.Context;
import android.widget.EditText;
import org.json.*;
import java.util.*;

/** Readable, bounded command plugins. No eval, reflection, network or arbitrary Java. */
public final class ScriptPlugins {
 private ScriptPlugins(){}
 static final String STORE="margyt_scripts";
 static volatile Program[] active=new Program[0];
 static final class Program {
  final JSONObject source;final String id,name;final Map<String,String> names=new HashMap<>();final int[] colourFrom,colourTo;
  Program(JSONObject j)throws Exception{source=j;id=j.getString("id");name=j.getString("name");JSONArray a=j.optJSONArray("names");if(a!=null)for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);names.put(v.getString("uid"),v.getString("value"));}a=j.optJSONArray("colours");int n=a==null?0:a.length();colourFrom=new int[n];colourTo=new int[n];for(int i=0;i<n;i++){JSONObject v=a.getJSONObject(i);colourFrom[i]=android.graphics.Color.parseColor(v.getString("from"));colourTo[i]=android.graphics.Color.parseColor(v.getString("to"));}}
 }
 static void keys(JSONObject j,String... allowed)throws Exception{Set<String> a=new HashSet<>(Arrays.asList(allowed));Iterator<String> k=j.keys();while(k.hasNext())if(!a.contains(k.next()))throw new Exception(Local.of("Неизвестное поле команды"));}
 static String string(JSONObject j,String k,int max)throws Exception{Object o=j.get(k);if(!(o instanceof String)||((String)o).isEmpty()||((String)o).length()>max)throw new Exception(Local.of("Некорректное поле: ")+k);String s=(String)o;for(int i=0;i<s.length();i++)if((Character.isISOControl(s.charAt(i))||Character.getType(s.charAt(i))==Character.FORMAT))throw new Exception(Local.of("Управляющие символы запрещены"));return s;}
 static void depth(String raw)throws Exception{
  int depth=0;boolean quoted=false,escape=false;
  for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>8)throw new Exception(Local.of("Слишком вложенный код"));}else if(c=='}'||c==']')depth--;}
 }
 static Program check(String raw)throws Exception{
  if(raw==null||raw.getBytes("UTF-8").length>65536)throw new Exception(Local.of("Плагин больше 64 КБ"));
  depth(raw);JSONObject j=new JSONObject(raw);keys(j,"schema","id","name","version","author","rows","names","colours","icon");if(!(j.get("schema") instanceof Integer)||((Integer)j.get("schema")).intValue()!=1)throw new Exception(Local.of("Неизвестный формат"));
  if(j.has("icon")){byte[] art=android.util.Base64.decode(j.getString("icon"),android.util.Base64.DEFAULT);if(art.length>32768||art.length<8||art[0]!=(byte)137||art[1]!=80||art[2]!=78||art[3]!=71)throw new Exception(Local.of("Иконка должна быть PNG до 32 КБ"));}
  if(!PluginCatalog.id(string(j,"id",80)))throw new Exception(Local.of("Некорректный ID"));string(j,"name",80);string(j,"version",32);string(j,"author",80);
  for(String key:new String[]{"rows","names","colours"}){
   if(!j.has(key))continue;Object array=j.get(key);if(!(array instanceof JSONArray))throw new Exception(Local.of("Ожидается список: ")+key);JSONArray a=(JSONArray)array;if(a.length()>16)throw new Exception(Local.of("Не больше 16 команд в списке"));
   for(int i=0;i<a.length();i++){JSONObject v=a.getJSONObject(i);
    if(key.equals("rows")){keys(v,"title","action","value");string(v,"title",80);String action=string(v,"action",20);if(!Arrays.asList("message","copy","window").contains(action))throw new Exception(Local.of("Команда не разрешена: ")+action);string(v,"value",1024);}
    else if(key.equals("names")){keys(v,"uid","value");if(!string(v,"uid",32).matches("[0-9]{1,32}"))throw new Exception(Local.of("Нужен ID аккаунта"));string(v,"value",80);}
    else {keys(v,"from","to");for(String c:new String[]{"from","to"})if(!string(v,c,7).matches("#[a-fA-F0-9]{6}"))throw new Exception(Local.of("Цвет: #RRGGBB"));}
   }
  }
  return new Program(j);
 }
 static synchronized void reload(Context c){List<Program> out=new ArrayList<>();Map<String,?> all=c.getSharedPreferences(STORE,0).getAll();List<String> keys=new ArrayList<>(all.keySet());Collections.sort(keys);for(String k:keys)if(k.startsWith("source:")&&out.size()<16&&c.getSharedPreferences(STORE,0).getBoolean("on:"+k.substring(7),false))try{out.add(check((String)all.get(k)));}catch(Exception e){Diary.note("script rejected: "+k);}active=out.toArray(new Program[0]);Accent.reload();}
 static synchronized void save(Context c,String raw,boolean enabled)throws Exception{Program p=check(raw);android.content.SharedPreferences prefs=c.getSharedPreferences(STORE,0);int n=0;for(String k:prefs.getAll().keySet())if(k.startsWith("source:"))n++;if(!prefs.contains("source:"+p.id)&&n>=16)throw new Exception(Local.of("Не больше 16 плагинов"));prefs.edit().putString("source:"+p.id,p.source.toString()).putBoolean("on:"+p.id,enabled).apply();reload(c);}
 public static String name(String uid,String value){for(Program p:active){String v=p.names.get(uid);if(v!=null)value=v;}return value;}
 public static int colour(int value){for(Program p:active)for(int i=0;i<p.colourFrom.length;i++)if(p.colourFrom[i]==value){value=p.colourTo[i];break;}return value;}
 static final String SAMPLE="{\"schema\":1,\"id\":\"my.commands\",\"name\":\"Мои команды\",\"version\":\"1.0\",\"author\":\"Автор\",\"rows\":[{\"title\":\"Привет\",\"action\":\"window\",\"value\":\"Мой плагин работает без сборки\"}]}";
 static List<Program> list(Context c){List<Program> out=new ArrayList<>();Map<String,?> all=c.getSharedPreferences(STORE,0).getAll();List<String> keys=new ArrayList<>(all.keySet());Collections.sort(keys);for(String key:keys)if(key.startsWith("source:")&&out.size()<16)try{out.add(check((String)all.get(key)));}catch(Exception ignored){}return out;}
 static void details(Context c,Program program,Runnable changed){
  Panel p=Panel.with(c,Skin.remembered(c),program.name);
  boolean enabled=c.getSharedPreferences(STORE,0).getBoolean("on:"+program.id,false);
  if(enabled){android.widget.LinearLayout body=new android.widget.LinearLayout(c);body.setOrientation(1);rows(c,body,program);if(body.getChildCount()>0)p.view(body);}
  p.primary(enabled?Local.of("Выключить"):Local.of("Включить"),()->{c.getSharedPreferences(STORE,0).edit().putBoolean("on:"+program.id,!enabled).apply();reload(c);if(changed!=null)changed.run();});
  p.quiet(Local.of("Редактировать"),()->edit(c,program.source.toString(),changed));
  p.quiet(Local.of("Ещё"),()->manage(c,program,changed));p.quiet(Text.CLOSE,null);p.show();
 }
 static void manage(Context c,Program program,Runnable changed){Panel p=Panel.with(c,Skin.remembered(c),program.name);p.primary(Local.of("Отправить на проверку"),()->submit(c,program));p.quiet(Local.of("Копировать исходник"),()->copy(c,program.source.toString()));p.quiet(Local.of("Удалить"),()->{c.getSharedPreferences(STORE,0).edit().remove("source:"+program.id).remove("on:"+program.id).apply();reload(c);if(changed!=null)changed.run();});p.quiet(Text.CLOSE,null);p.show();}
 static void edit(Context c,String raw,Runnable changed){PluginEditorActivity.script(c,raw);}
 static void copy(Context c,String value){android.content.ClipboardManager m=(android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);if(m!=null)m.setPrimaryClip(android.content.ClipData.newPlainText("plugin",value));}
 static void rows(Context c,android.widget.LinearLayout parent,Program p){JSONArray rows=p.source.optJSONArray("rows");if(rows==null)return;for(int i=0;i<rows.length();i++)try{JSONObject row=rows.getJSONObject(i);android.widget.TextView v=new android.widget.TextView(c);v.setText(row.getString("title"));v.setTextColor(Skin.remembered(c).text);v.setTextSize(16);Fonts.apply(v);int pad=(int)(16*c.getResources().getDisplayMetrics().density);v.setPadding(pad,pad,pad,pad);v.setOnClickListener(view->{try{String action=row.getString("action"),value=row.getString("value");if(action.equals("copy")){copy(c,value);Screen.say(Local.of("Скопировано"));}else if(action.equals("window"))Popup.show(c,p.name,value);else Screen.say(value);}catch(Exception e){Diary.note("script action: "+e);}});parent.addView(v);}catch(Exception ignored){}}
 static void submit(Context c,Program p){Popup.show(c,p.name,Text.PLUGIN_PUBLISH_PANEL);}
 static void ui(Runnable r){new android.os.Handler(android.os.Looper.getMainLooper()).post(r);}
}
