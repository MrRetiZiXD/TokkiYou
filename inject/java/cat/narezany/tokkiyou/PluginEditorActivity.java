package cat.narezany.tokkiyou;

import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Plugin editor in three modes:
 *  - script: a command plugin built from blocks (buttons, names, colours), no code;
 *  - binary: metadata of an installed .mtp (its dex is kept);
 *  - file:   the creator picks a .mtp and publishes it to the store without installing it first.
 * Save / publish live in a bar at the bottom of the screen, never at the end of a long scroll.
 */
public final class PluginEditorActivity extends Activity {
 private static final int PICK_ICON=71,PICK_FILE=72;
 private LinearLayout body,commands,namesBox,coloursBox,bar;
 private EditText id,name,version,author,description,tiktok;
 private ImageView image;
 private TextView plus;
 private Skin skin;
 private JSONObject source;
 private Plugins.Info binary;
 private byte[] icon,dex;
 private String note;
 private boolean busy,file;
 private final List<Command> rows=new ArrayList<>();
 private final List<Two> names=new ArrayList<>(),colours=new ArrayList<>();
 private static final class Command{LinearLayout box;EditText title,value;Spinner action;}
 private static final class Two{LinearLayout box;EditText a,b;}
 static void script(Context c,String raw){c.startActivity(new Intent(c,PluginEditorActivity.class).putExtra("source",raw));}
 static void binary(Context c,String id){c.startActivity(new Intent(c,PluginEditorActivity.class).putExtra("binary",id));}
 /** Creator only: pick a .mtp and publish it straight to the store. */
 static void file(Context c){c.startActivity(new Intent(c,PluginEditorActivity.class).putExtra("pick",true));}

 @Override public void onCreate(Bundle state){super.onCreate(state);skin=Skin.remembered(this);
  try{
   if(getIntent().getBooleanExtra("pick",false)){
    if(!AdminAccess.current())throw new Exception(Local.of("Нужен аккаунт создателя"));
    file=true;
    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_FILE);
    return;
   }
   String wanted=getIntent().getStringExtra("binary");
   if(wanted!=null){for(Plugins.Info info:Plugins.list())if(info.id.equals(wanted))binary=info;if(binary==null)throw new Exception(Local.of("Плагин не установлен"));
    source=new JSONObject(new String(PluginAudit.read(new File(binary.folder,"manifest.json"),65536),"UTF-8"));
    File picture=new File(binary.folder,"icon.png");if(picture.isFile())icon=PluginAudit.read(picture,1048576);
   }else{String raw=getIntent().getStringExtra("source");source=new JSONObject(raw==null||raw.isEmpty()?ScriptPlugins.SAMPLE:raw);if(source.has("icon"))icon=android.util.Base64.decode(source.getString("icon"),android.util.Base64.DEFAULT);}
   if(icon!=null)icon=smallIcon(icon);
   build();
  }catch(Exception error){Screen.say(error.getMessage());finish();}
 }

 // ---------------------------------------------------------------- small helpers
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private int onAccent(){int c=Accent.colour();return (((c>>16)&255)*299+((c>>8)&255)*587+(c&255)*114)/1000>140?0xFF10221F:0xFFFFFFFF;}
 private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(skin.text);v.setTextSize(size);Fonts.apply(v);v.setPadding(0,dp(8),0,dp(4));return v;}
 private TextView small(String value){TextView v=text(value,12);v.setTextColor(skin.muted());v.setPadding(0,0,0,0);return v;}
 private LinearLayout card(LinearLayout parent){
  LinearLayout c=new LinearLayout(this);c.setOrientation(1);GradientDrawable g=new GradientDrawable();g.setColor(skin.card);g.setCornerRadius(skin.radius);c.setBackground(g);c.setPadding(dp(10),dp(10),dp(10),dp(4));
  parent.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;
 }
 private EditText input(String hint,String value,int max,boolean multi){
  EditText v=new EditText(this);v.setHint(hint);v.setTextColor(skin.text);v.setHintTextColor(skin.muted());v.setTextSize(15);v.setSingleLine(!multi);v.setMaxLines(multi?3:1);
  v.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});v.setText(value);Fonts.apply(v);v.setPadding(dp(12),dp(10),dp(12),dp(10));
  GradientDrawable g=new GradientDrawable();g.setColor(0x22888888);g.setCornerRadius(dp(10));v.setBackground(g);return v;
 }
 private EditText field(LinearLayout parent,String hint,String value,int max,boolean multi){EditText v=input(hint,value,max,multi);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);parent.addView(v,p);return v;}
 private LinearLayout line(LinearLayout parent){LinearLayout r=new LinearLayout(this);r.setOrientation(0);r.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);parent.addView(r,p);return r;}
 private void cell(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.rightMargin=dp(6);row.addView(v,p);}
 private TextView x(Runnable run){TextView v=text("\u2715",16);v.setTextColor(skin.muted());v.setPadding(dp(8),dp(8),dp(4),dp(8));v.setOnClickListener(w->run.run());Motion.press(v);return v;}
 private TextView pill(String label,boolean primary,Runnable run){
  TextView v=text(label,15);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(16),0,dp(16),0);
  GradientDrawable g=new GradientDrawable();g.setCornerRadius(dp(24));g.setColor(primary?Accent.colour():skin.card);v.setBackground(g);v.setTextColor(primary?onAccent():Accent.colour());
  v.setOnClickListener(w->{if(!busy)run.run();});Motion.press(v);return v;
 }
 private void addPill(LinearLayout parent,TextView v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.rightMargin=dp(6);p.leftMargin=dp(6);parent.addView(v,p);}
 private static String hex(String s){s=s.trim();return s.startsWith("#")?s:"#"+s;}
 private static void tint(View v,String s){try{v.setBackgroundColor(Color.parseColor(hex(s)));}catch(Exception e){v.setBackgroundColor(0x33888888);}}
 /** A titled block with a round + on the right; the card under it holds the items. */
 private LinearLayout section(String title,String about,Runnable add){
  LinearLayout h=new LinearLayout(this);h.setOrientation(0);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(dp(4),dp(14),0,dp(4));
  LinearLayout t=new LinearLayout(this);t.setOrientation(1);TextView a=text(title,17);a.setPadding(0,0,0,0);t.addView(a);t.addView(small(about));h.addView(t,new LinearLayout.LayoutParams(0,-2,1f));
  TextView p=pill("+",false,add);p.setMinHeight(dp(40));p.setMinWidth(dp(56));p.setTextSize(22);h.addView(p);body.addView(h);
  LinearLayout c=card(body);TextView e=small(Local.of("Пока пусто — нажмите +"));e.setPadding(dp(4),dp(4),0,dp(10));c.addView(e);return c;
 }
 private static void sync(LinearLayout c){c.getChildAt(0).setVisibility(c.getChildCount()>1?View.GONE:View.VISIBLE);}

 // ---------------------------------------------------------------- screen
 private void build(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(skin.page);
  ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(14),dp(4),dp(14),dp(20));scroll.addView(body);
  bar=new LinearLayout(this);bar.setOrientation(0);bar.setPadding(dp(8),dp(6),dp(8),dp(10));bar.setBackgroundColor(skin.page);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));setContentView(root);
  getWindow().setStatusBarColor(skin.page);getWindow().setNavigationBarColor(skin.page);

  LinearLayout top=new LinearLayout(this);top.setOrientation(0);top.setGravity(Gravity.CENTER_VERTICAL);
  TextView back=text("\u2190",24);back.setPadding(dp(4),dp(8),dp(16),dp(8));back.setOnClickListener(v->finish());Motion.press(back);top.addView(back);
  top.addView(text(file?Local.of("Добавить в магазин"):binary!=null?Local.of("Данные плагина"):Local.of("Новый плагин"),20));body.addView(top);
  if(file&&note!=null){TextView n=small(note);n.setPadding(dp(4),0,0,dp(8));body.addView(n);}
  else if(binary!=null){TextView n=small(Local.of("Код сохраняется. После изменения плагин нужно включить заново."));n.setPadding(dp(4),0,0,dp(8));body.addView(n);}

  // icon on the left, the essentials on the right
  LinearLayout head=card(body);LinearLayout row=new LinearLayout(this);row.setOrientation(0);head.addView(row);
  LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(92),dp(92));ip.rightMargin=dp(10);row.addView(iconBox(),ip);
  LinearLayout right=new LinearLayout(this);right.setOrientation(1);row.addView(right,new LinearLayout.LayoutParams(0,-2,1f));
  name=field(right,Local.of("Название"),binary==null?source.optString("name"):binary.name,80,false);
  LinearLayout vr=line(right);version=input(Local.of("Версия"),source.optString("version"),32,false);cell(vr,version);author=input(Local.of("Автор"),source.optString("author"),80,false);cell(vr,author);
  id=field(head,Local.of("ID: латиница, например my.plugin"),source.optString("id"),80,false);if(binary!=null||file)id.setEnabled(false);
  if(binary!=null||file)description=field(head,Local.of("Описание, до 240 символов"),binary!=null?binary.description:source.optString("description"),240,true);
  if(file)tiktok=field(head,Local.of("Версия TikTok (необязательно)"),"",32,false);
  paintIcon();

  if(binary==null&&!file){
   commands=section(Local.of("Кнопки"),Local.of("Сообщение, копирование текста или окно. До 16."),()->{if(rows.size()>=16){Screen.say(Local.of("Не больше 16 кнопок"));return;}addCommand(null);});
   JSONArray list=source.optJSONArray("rows");if(list!=null)for(int i=0;i<list.length();i++)addCommand(list.optJSONObject(i));
   namesBox=section(Local.of("Имена"),Local.of("Другое имя у аккаунта. UID — число из ссылки на профиль."),()->{if(names.size()>=16){Screen.say(Local.of("Не больше 16 имён"));return;}addPair(namesBox,names,null,true);});
   list=source.optJSONArray("names");if(list!=null)for(int i=0;i<list.length();i++)addPair(namesBox,names,list.optJSONObject(i),true);
   coloursBox=section(Local.of("Цвета"),Local.of("Один цвет заменяется другим по всему приложению."),()->{if(colours.size()>=16){Screen.say(Local.of("Не больше 16 цветов"));return;}addPair(coloursBox,colours,null,false);});
   list=source.optJSONArray("colours");if(list!=null)for(int i=0;i<list.length();i++)addPair(coloursBox,colours,list.optJSONObject(i),false);
   sync(commands);sync(namesBox);sync(coloursBox);
   TextView json=text(Local.of("Показать JSON"),14);json.setTextColor(skin.muted());json.setPadding(dp(4),dp(16),0,dp(8));Motion.press(json);
   json.setOnClickListener(w->{try{String value=value().toString(2);Panel p=Panel.with(this,skin,Local.of("Исходник"));EditText code=p.field("JSON");code.setSingleLine(false);code.setMaxLines(10);code.setText(value);p.primaryKeepOpen(Local.of("Применить"),()->{try{ScriptPlugins.Program checked=ScriptPlugins.check(code.getText().toString());script(this,checked.source.toString());p.close();finish();}catch(Exception e){code.setError(e.getMessage());}});p.quiet(Text.CLOSE,null);p.show();}catch(Exception e){Screen.say(e.getMessage());}});
   body.addView(json);
  }
  if(!AdminAccess.current()&&!file){TextView n=small(Local.of("Публикация в магазине доступна только аккаунту создателя."));n.setPadding(dp(4),dp(8),0,0);body.addView(n);}

  if(file)addPill(bar,pill(Local.of("Опубликовать в магазине"),true,()->save(true)));
  else{addPill(bar,pill(Local.of("Сохранить"),!AdminAccess.current(),()->save(false)));if(AdminAccess.current())addPill(bar,pill(Local.of("В магазин"),true,()->save(true)));}
 }

 /** Grey rounded square with a plus; shows the picture once there is one. */
 private FrameLayout iconBox(){
  FrameLayout f=new FrameLayout(this);GradientDrawable g=new GradientDrawable();g.setColor(0x33888888);g.setCornerRadius(dp(20));f.setBackground(g);
  image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(dp(10),dp(10),dp(10),dp(10));f.addView(image,new FrameLayout.LayoutParams(-1,-1));
  plus=new TextView(this);plus.setText("+");plus.setTextSize(40);plus.setTextColor(skin.muted());plus.setGravity(Gravity.CENTER);f.addView(plus,new FrameLayout.LayoutParams(-1,-1));
  f.setContentDescription(Local.of("Иконка плагина"));Motion.press(f);
  f.setOnClickListener(v->{if(busy)return;if(icon==null){pickIcon();return;}
   Panel.with(this,skin,Local.of("Иконка")).primary(Local.of("Выбрать другую"),()->pickIcon()).quiet(Local.of("Убрать"),()->{icon=null;paintIcon();}).quiet(Text.CLOSE,null).show();});
  return f;
 }
 private void pickIcon(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),PICK_ICON);}catch(ActivityNotFoundException e){Screen.say(Local.of("На устройстве нет выбора файлов"));}}

 private void addCommand(JSONObject row){
  Command c=new Command();c.box=new LinearLayout(this);c.box.setOrientation(1);c.box.setPadding(0,0,0,dp(6));commands.addView(c.box);
  LinearLayout r=line(c.box);
  c.title=input(Local.of("Название кнопки"),row==null?"":row.optString("title"),80,false);cell(r,c.title);
  c.action=new Spinner(this);String[] options={Local.of("Сообщение"),Local.of("Копировать"),Local.of("Окно")};c.action.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));String action=row==null?"message":row.optString("action");c.action.setSelection(action.equals("window")?2:action.equals("copy")?1:0);
  r.addView(c.action,new LinearLayout.LayoutParams(dp(130),-2));
  r.addView(x(()->{rows.remove(c);commands.removeView(c.box);sync(commands);}));
  c.value=field(c.box,Local.of("Текст"),row==null?"":row.optString("value"),1024,true);
  rows.add(c);sync(commands);
 }

 /** One line: a name replacement (uid, name) or a colour replacement (from, to). */
 private void addPair(LinearLayout parent,List<Two> list,JSONObject row,boolean isName){
  Two t=new Two();t.box=line(parent);
  if(isName){
   t.a=input(Local.of("UID аккаунта"),row==null?"":row.optString("uid"),32,false);t.a.setInputType(InputType.TYPE_CLASS_NUMBER);cell(t.box,t.a);
   t.b=input(Local.of("Показывать как"),row==null?"":row.optString("value"),80,false);cell(t.box,t.b);
  }else{
   View s1=new View(this),s2=new View(this);
   LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(22),dp(22));sp.rightMargin=dp(6);t.box.addView(s1,sp);
   t.a=input("#RRGGBB",row==null?"#":row.optString("from"),7,false);cell(t.box,t.a);
   TextView arrow=text("\u2192",16);arrow.setPadding(0,0,dp(6),0);t.box.addView(arrow);
   LinearLayout.LayoutParams sq=new LinearLayout.LayoutParams(dp(22),dp(22));sq.rightMargin=dp(6);t.box.addView(s2,sq);
   t.b=input("#RRGGBB",row==null?"#":row.optString("to"),7,false);cell(t.box,t.b);
   tint(s1,t.a.getText().toString());tint(s2,t.b.getText().toString());
   t.a.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){tint(s1,e.toString());}});
   t.b.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){tint(s2,e.toString());}});
  }
  t.box.addView(x(()->{list.remove(t);parent.removeView(t.box);sync(parent);}));list.add(t);sync(parent);
 }

 // ---------------------------------------------------------------- value / save
 private JSONObject value()throws Exception{
  JSONObject value=new JSONObject(source.toString());value.put("id",id.getText().toString().trim()).put("name",name.getText().toString().trim()).put("version",version.getText().toString().trim()).put("author",author.getText().toString().trim());
  if(binary==null&&!file){
   JSONArray list=new JSONArray();String[] actions={"message","copy","window"};
   for(Command c:rows){String t=c.title.getText().toString(),v=c.value.getText().toString();if(t.trim().isEmpty()&&v.trim().isEmpty())continue;list.put(new JSONObject().put("title",t).put("action",actions[c.action.getSelectedItemPosition()]).put("value",v));}
   value.put("rows",list);
   JSONArray n=new JSONArray();for(Two t:names){String u=t.a.getText().toString().trim(),v=t.b.getText().toString();if(u.isEmpty()&&v.trim().isEmpty())continue;n.put(new JSONObject().put("uid",u).put("value",v));}value.put("names",n);
   JSONArray k=new JSONArray();for(Two t:colours){String f=t.a.getText().toString().trim(),o=t.b.getText().toString().trim();if((f.isEmpty()||f.equals("#"))&&(o.isEmpty()||o.equals("#")))continue;k.put(new JSONObject().put("from",hex(f)).put("to",hex(o)));}value.put("colours",k);
   if(icon==null)value.remove("icon");else value.put("icon",android.util.Base64.encodeToString(icon,android.util.Base64.NO_WRAP));
   ScriptPlugins.check(value.toString());
  }else{value.put("description",description.getText().toString().trim());if(value.optString("name").isEmpty()||value.optString("version").isEmpty()||value.optString("author").isEmpty())throw new Exception(Local.of("Заполните название, версию и автора"));}
  return value;
 }
 private static String b64(byte[] b){return android.util.Base64.encodeToString(b,android.util.Base64.NO_WRAP);}
 private static void entry(ZipOutputStream zip,String name,byte[] value)throws Exception{zip.putNextEntry(new ZipEntry(name));zip.write(value);zip.closeEntry();}
 private void save(boolean publish){
  if(publish&&!AdminAccess.current()){Screen.say(Local.of("Нужен аккаунт создателя"));return;}
  try{
   JSONObject value=value();byte[] art=icon;String store=tiktok==null?"":tiktok.getText().toString().trim();busy=true;
   Net.away("plugin editor",()->{File temporary=null;try{
    if(file){
     ByteArrayOutputStream bytes=new ByteArrayOutputStream();
     try(ZipOutputStream zip=new ZipOutputStream(bytes)){entry(zip,"manifest.json",value.toString().getBytes("UTF-8"));entry(zip,"classes.dex",dex);if(art!=null)entry(zip,"icon.png",art);}
     byte[] archive=bytes.toByteArray();if(archive.length>1048576)throw new Exception(Local.of("Для магазина размер .mtp должен быть не больше 1 МБ"));
     JSONObject metadata=new JSONObject().put("id",value.getString("id")).put("name",value.getString("name")).put("version",value.getString("version")).put("author",value.getString("author")).put("about",value.optString("description")).put("tiktok",store);
     JSONObject request=new JSONObject().put("action","publish-package").put("metadata",metadata).put("archive",b64(archive));if(art!=null)request.put("icon",b64(art));PluginAdmin.call(this,request);
    }else if(binary==null){ScriptPlugins.save(this,value.toString(),true);if(publish)PluginAdmin.call(this,new JSONObject().put("action","publish-script").put("source",value));}
    else{
     byte[] archive=PluginAdmin.pack(binary,value,art);if(publish && archive.length>1048576)throw new Exception(Local.of("Для магазина размер .mtp должен быть не больше 1 МБ"));temporary=File.createTempFile("plugin-edit-",".mtp",getCacheDir());try(FileOutputStream out=new FileOutputStream(temporary)){out.write(archive);}
     Plugins.install(this,Uri.fromFile(temporary));
     if(publish){JSONObject metadata=new JSONObject().put("id",binary.id).put("name",value.getString("name")).put("version",value.getString("version")).put("author",value.getString("author")).put("about",value.optString("description")).put("tiktok",value.optString("tiktok"));JSONObject request=new JSONObject().put("action","publish-package").put("metadata",metadata).put("archive",b64(archive));if(art!=null)request.put("icon",b64(art));PluginAdmin.call(this,request);}
    }
    runOnUiThread(()->{busy=false;Screen.say(publish?Local.of("Опубликовано в магазине"):Local.of("Сохранено"));finish();});
   }catch(Exception e){runOnUiThread(()->{busy=false;Popup.show(this,Local.of("Не сохранено"),e.getMessage());});}finally{if(temporary!=null)temporary.delete();}});
  }catch(Exception e){Popup.show(this,Local.of("Проверьте поля"),e.getMessage());}
 }

 // ---------------------------------------------------------------- icon and file
 private void paintIcon(){image.setImageBitmap(icon==null?null:BitmapFactory.decodeByteArray(icon,0,icon.length));plus.setVisibility(icon==null?View.VISIBLE:View.GONE);}
 private static byte[] smallIcon(byte[] raw)throws Exception{
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,bounds);
  if(bounds.outWidth<=0||bounds.outHeight<=0)throw new Exception(Local.of("Не удалось прочитать иконку"));
  BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(bounds.outWidth,bounds.outHeight)/128);
  Bitmap original=BitmapFactory.decodeByteArray(raw,0,raw.length,options);if(original==null)throw new Exception(Local.of("Не удалось прочитать иконку"));
  float scale=64f/Math.max(original.getWidth(),original.getHeight());Bitmap resized=Bitmap.createScaledBitmap(original,Math.max(1,Math.round(original.getWidth()*scale)),Math.max(1,Math.round(original.getHeight()*scale)),true);
  ByteArrayOutputStream out=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.PNG,100,out);if(resized!=original)resized.recycle();original.recycle();
  if(out.size()>32768)throw new Exception(Local.of("Иконка слишком большая"));return out.toByteArray();
 }
 private static byte[] readStream(InputStream in,int max)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
  while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new Exception(Local.of("Файл больше ")+(max>=1048576?(max/1048576)+Local.of(" МБ"):(max/1024)+Local.of(" КБ"))+Local.of(" — магазин такой не примет"));out.write(buffer,0,n);}
  return out.toByteArray();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
  if(request==PICK_FILE){
   if(result!=RESULT_OK||data==null||data.getData()==null){finish();return;}
   Uri uri=data.getData();busy=true;
   Net.away("plugin file",()->{File temporary=null;try{
    byte[] raw;try(InputStream in=getContentResolver().openInputStream(uri)){raw=readStream(in,1048576);}
    Map<String,byte[]> files=new HashMap<>();
    try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(raw))){ZipEntry e;while((e=in.getNextEntry())!=null){String n=e.getName();
     if(!(n.equals("manifest.json")||n.equals("classes.dex")||n.equals("icon.png")))throw new Exception(Local.of("В файле лишнее: ")+n+Local.of(". Нужны только manifest.json, classes.dex и icon.png"));
     if(files.containsKey(n))throw new Exception(Local.of("Файл повреждён"));files.put(n,readStream(in,1048576));}}
    if(!files.containsKey("manifest.json")||!files.containsKey("classes.dex"))throw new Exception(Local.of("Это не плагин: нет manifest.json или classes.dex"));
    JSONObject manifest=new JSONObject(new String(files.get("manifest.json"),"UTF-8"));
    if(!PluginCatalog.id(manifest.optString("id")))throw new Exception(Local.of("В manifest.json неверный ID"));
    temporary=File.createTempFile("plugin-publish-",".mtp",getCacheDir());try(FileOutputStream out=new FileOutputStream(temporary)){out.write(raw);}
    PluginAudit.Report report=Plugins.inspect(this,Uri.fromFile(temporary));if(report.blocked)throw new Exception(report.summary());
    byte[] pic=files.get("icon.png");byte[] small=pic==null?null:smallIcon(pic);
    String found=report.findings.isEmpty()?Local.of("Ничего особенного не найдено."):report.summary();
    runOnUiThread(()->{busy=false;source=manifest;dex=files.get("classes.dex");icon=small;note=found;build();});
   }catch(Exception e){runOnUiThread(()->{busy=false;Screen.say(e.getMessage());finish();});}finally{if(temporary!=null)temporary.delete();}});
   return;
  }
  if(request!=PICK_ICON||result!=RESULT_OK||data==null||data.getData()==null)return;
  busy=true;Net.away("plugin icon",()->{try(InputStream in=getContentResolver().openInputStream(data.getData())){
   byte[] next=smallIcon(readStream(in,2097152));
   runOnUiThread(()->{busy=false;icon=next;paintIcon();});
  }catch(Exception e){runOnUiThread(()->{busy=false;Screen.say(e.getMessage());});}});
 }
}
