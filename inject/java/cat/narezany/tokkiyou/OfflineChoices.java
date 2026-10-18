package cat.narezany.tokkiyou;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/** Extra rows use the native offline radio model and native selection state. */
public final class OfflineChoices {
 private OfflineChoices() {}
 public static List<?> counts(List<?> original) {
  ArrayList<Object> result=new ArrayList<Object>(original);
  if(!result.contains(500))result.add(500);
  if(!result.contains(1000))result.add(1000);
  if(!result.contains(-2))result.add(-2);
  if(!result.contains(-3))result.add(-3);
  if(!result.contains(-4))result.add(-4);
  return result;
 }
 public static boolean validCount(List<?> original,Object value){return original.contains(value)||(value instanceof Integer&&(Integer)value>=1&&(Integer)value<=10000);}
 private static Object field(Object value,String name)throws Exception{return value.getClass().getField(name).get(value);}
 private static void select(Object vm,int count)throws Exception {vm.getClass().getMethod("t43",int.class).invoke(vm,count);}
 public static Object row(Object owner,Object original) {
  try {
   int count=(Integer)field(owner,"LLJJJ");
   if(count==-3||count==-4){
    Activity activity=(Activity)field(owner,"LLJJJIL");
    View.OnClickListener click=view->{if(activity==null||activity.isFinishing())return;if(count==-3)OfflineComments.settings(activity);else OfflineComments.library(activity);};
    return Class.forName("X.0lhy").getConstructor(boolean.class,String.class,Class.forName("X.0GtW"),String.class,View.OnClickListener.class,int.class).newInstance(false,count==-3?Local.of("Сохранять комментарии"):Local.of("Сохранённые комментарии"),null,"",click,0x1b7a);
   }
   if(count!=500&&count!=1000&&count!=-2)return original;
   Activity activity=(Activity)field(owner,"LLJJJIL");
   Object vm=field(owner,"LLJJJJ");
   Object state=vm.getClass().getMethod("getState").invoke(vm);
   int selected=(Integer)field(state,"LLJJIJIL");
   boolean automatic=(Boolean)field(state,"LLJJJ");
   boolean checked=!automatic&&(count==-2?selected>0&&selected!=500&&selected!=1000&&!nativeCount(selected):selected==count);
   String label=count==-2?Local.of("Своё значение")+(checked?" · "+selected+Local.of(" видео"):""):count+Local.of(" видео");
   View.OnClickListener click=view->{
    if(count!=-2){try{select(vm,count);}catch(Exception e){Diary.note("offline select: "+e.getClass().getSimpleName());}return;}
    if(activity==null||activity.isFinishing())return;
    EditText input=new EditText(activity);
    input.setInputType(InputType.TYPE_CLASS_NUMBER);
    input.setSingleLine(true);input.setHint(Local.of("Количество видео"));
    if(selected>0)input.setText(String.valueOf(selected));
    int pad=(int)(20*activity.getResources().getDisplayMetrics().density);
    input.setPadding(pad,pad,pad,pad);
    AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(Local.of("Своё значение")).setView(input).setNegativeButton(Local.of("Отмена"),null).setPositiveButton(Local.of("Выбрать"),null).create();
    dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
     int value=parse(input.getText().toString());
     if(value<1){input.setError(Local.of("Введите число от 1 до 10000"));return;}
     try{select(vm,value);dialog.dismiss();}catch(Exception e){input.setError(Local.of("Не удалось выбрать количество"));Diary.note("offline custom: "+e.getClass().getSimpleName());}
    }));dialog.show();
   };
   Class<?> model=Class.forName("X.0lhy");
   Constructor<?> constructor=model.getConstructor(boolean.class,String.class,Class.forName("X.0GtW"),String.class,View.OnClickListener.class,int.class);
   return constructor.newInstance(checked,label,null,"",click,0x1b7a);
  }catch(Throwable e){Diary.note("offline row: "+e.getClass().getSimpleName());return original;}
 }
 static int parse(String text){try{int value=Integer.parseInt(text.trim());return value>=1&&value<=10000?value:-1;}catch(Exception e){return -1;}}
 private static boolean nativeCount(int value){
  try{Object[] values=(Object[])Class.forName("X.0wiM").getMethod("values").invoke(null);for(Object item:values)if(((Integer)item.getClass().getMethod("getCount").invoke(item))==value)return true;}catch(Exception ignored){}
  return false;
 }
 /** Older native sheet layouts do not instantiate the PowerList radio models. */
 public static void legacy(Object owner){
  try{
   Object rootValue=NativeRead.get(owner,"LJJIZ");if(!(rootValue instanceof View))return;View root=(View)rootValue;
   View recycler=root.findViewById(0x7f0a57b4);if(recycler!=null&&recycler.getVisibility()==View.VISIBLE)return;
   Object container=field(owner,"LLJJJJ");if(!(container instanceof LinearLayout))return;LinearLayout list=(LinearLayout)container;
   if(list.findViewWithTag("tokkiyou-offline-extra")!=null)return;
   Object vm=NativeRead.get(owner,"Jp");Object host=Class.forName("X.0dx4").getMethod("LIZ",Class.forName("androidx.lifecycle.LifecycleOwner")).invoke(null,owner);if(!(host instanceof Activity))return;Activity activity=(Activity)host;
   LinearLayout extra=new LinearLayout(activity);extra.setTag("tokkiyou-offline-extra");extra.setOrientation(1);
   for(String label:new String[]{Local.of("500 видео"),Local.of("1000 видео"),Local.of("Своё значение"),Local.of("Сохранять комментарии"),Local.of("Сохранённые комментарии")}){
    TextView button=new TextView(activity);button.setText(label);button.setTextColor(Skin.of(root).text);button.setTextSize(15);int pad=(int)(16*activity.getResources().getDisplayMetrics().density);button.setPadding(pad,pad/2,pad,pad/2);
    button.setOnClickListener(v->{try{
     if(label.equals(Local.of("Сохранять комментарии"))){OfflineComments.settings(activity);return;}if(label.equals(Local.of("Сохранённые комментарии"))){OfflineComments.library(activity);return;}
     if(label.startsWith("500")){select(vm,500);return;}if(label.startsWith("1000")){select(vm,1000);return;}
     EditText input=new EditText(activity);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint(Local.of("Количество видео: 1–10000"));
     AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(label).setView(input).setNegativeButton(Local.of("Отмена"),null).setPositiveButton(Local.of("Выбрать"),null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(w->{int n=parse(input.getText().toString());if(n<1){input.setError(Local.of("Введите число от 1 до 10000"));return;}try{select(vm,n);dialog.dismiss();}catch(Exception e){input.setError(Local.of("Не удалось выбрать количество"));}}));dialog.show();
    }catch(Exception e){Diary.note("offline legacy choice: "+e.getClass().getSimpleName());}});extra.addView(button);
   }
   list.addView(extra);
  }catch(Throwable e){Diary.note("offline legacy list: "+e.getClass().getSimpleName());}
 }
}
