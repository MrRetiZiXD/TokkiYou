package cat.narezany.margyt;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.Adler32;

/**
 * A look at what a plugin's code reaches for. It says what it found and stops nobody.
 *
 * It used to refuse plugins outright -- for reflection, for short names, for a long string -- and to ask
 * for the rest to be approved again each time they were switched on, which in practice meant a plugin
 * from the mod's own store could not be installed, or once on could not be turned off. None of that made
 * anybody safer: a plugin is code running as TikTok whatever it is called, and every plugin that does
 * anything worth having reaches into TikTok by reflection. What protects people is where the plugin came
 * from, and the store is filled by hand.
 */
public final class PluginAudit {
    private PluginAudit() {}
    public static final int MAX_DEX=16*1024*1024;
    public static final class Report {
        public final Set<String> findings=new LinkedHashSet<String>();
        public boolean blocked;
        public String digest="";
        public String pluginId="";
        private void add(String text,boolean deny){findings.add(text);}
        public String summary(){
            StringBuilder text=new StringBuilder(Text.PLUGIN_AUDIT_HEAD);
            for(String item:findings)text.append("\n• ").append(item);
            if(findings.isEmpty())text.append("\n").append(Text.PLUGIN_AUDIT_CLEAN);
            return text.append("\n\n").append(Text.PLUGIN_AUDIT_NOTE).toString();
        }
    }
    public static byte[] read(File file,int limit)throws IOException{
        try(InputStream in=new FileInputStream(file)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n,total=0;
            while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException(Local.of("Превышен размер файла плагина"));out.write(b,0,n);}return out.toByteArray();}
    }
    public static String sha256(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return b.toString();}
    public static Report folder(File folder)throws Exception{
        byte[] dex=read(new File(folder,"classes.dex"),MAX_DEX);
        Report report=dex(dex);
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(String name:new String[]{"manifest.json","classes.dex","icon.png"}){
            hash.update(name.getBytes("UTF-8"));File file=new File(folder,name);
            if(file.isFile())hash.update(MessageDigest.getInstance("SHA-256").digest(read(file,name.equals("classes.dex")?MAX_DEX:name.equals("icon.png")?1024*1024:65536)));
        }
        report.digest=hex(hash.digest());return report;
    }
    /**
     * What a dex reaches for. A dex this cannot read is said to be one it could not read, and is not
     * refused for it: whether the code runs is for the loader to find out, and a look that fails is no
     * reason to keep a plugin off somebody's phone.
     */
    public static Report dex(byte[] data)throws Exception{
        try{return read(data);}
        catch(IOException unreadable){
            Report report=new Report();report.add(Local.of("Код плагина не удалось разобрать: ")+unreadable.getMessage(),false);
            report.digest=sha256(data);return report;
        }
    }
    private static Report read(byte[] data)throws Exception{
        Report report=new Report();Dex d=new Dex(data);
        for(int i=0;i<d.typeCount;i++){
            String type=d.type(i);
            if(type.startsWith("Ldalvik/system/") && (type.contains("ClassLoader")||type.contains("DexFile")))report.add(Local.of("Загрузка дополнительного исполняемого кода"),true);
            if(type.startsWith("Ljava/lang/reflect/")||type.startsWith("Ljava/lang/invoke/"))report.add(Local.of("Рефлексия: часть действий нельзя установить статически"),false);
            if(type.startsWith("Ljava/net/")||type.startsWith("Lokhttp")||type.startsWith("Lretrofit"))report.add(Local.of("Доступ к сети"),false);
            if(type.equals("Landroid/webkit/WebView;"))report.add(Local.of("WebView и возможное выполнение JavaScript"),false);
            if(type.startsWith("Landroid/telephony/")||type.startsWith("Landroid/provider/Contacts")||type.startsWith("Landroid/accounts/"))report.add(Local.of("Доступ к личным данным устройства"),false);
            if(type.equals("Landroid/content/ClipboardManager;"))report.add(Local.of("Доступ к буферу обмена"),false);
            if(type.startsWith("Ljava/io/")||type.startsWith("Ljava/nio/file/"))report.add(Local.of("Прямой доступ к файлам приложения"),false);
        }
        for(int i=0;i<d.methodCount;i++){
            int p=d.methodOff+i*8;String owner=d.type(d.u16(p));String name=d.string(d.u32(p+4));
            if((owner.equals("Ljava/lang/Runtime;")&&name.equals("exec"))||(owner.equals("Ljava/lang/ProcessBuilder;")&&name.equals("start")))report.add(Local.of("Запуск системных команд"),true);
            if((owner.equals("Ljava/lang/System;")||owner.equals("Ljava/lang/Runtime;"))&&(name.equals("load")||name.equals("loadLibrary")))report.add(Local.of("Загрузка нативных библиотек"),true);
            if(owner.equals("Ljava/lang/Class;")&&name.equals("forName"))report.add(Local.of("Поиск классов по имени: возможна скрытая загрузка API"),false);
            if((owner.equals("Lcat/narezany/margyt/plugin/PluginContext;")&&(name.equals("fetch")||name.equals("download")))||owner.equals("Lcat/narezany/margyt/Net;"))report.add(Local.of("Сеть через API мода"),false);
            if((owner.equals("Lcat/narezany/margyt/plugin/PluginContext;")&&name.equals("context"))||(owner.equals("Lcat/narezany/margyt/Margy;")&&name.equals("context")))report.add(Local.of("Доступ к контексту TikTok"),false);
            if(owner.equals("Ljava/lang/ClassLoader;")&&name.equals("loadClass"))report.add(Local.of("Загрузка классов по имени"),false);
        }
        // Native method flags in class_data, rather than matching words in strings.
        int count=d.u32(96),off=d.u32(100);d.table(off,count,32);
        for(int i=0;i<count;i++){
            int at=d.u32(off+i*32+24);if(at==0)continue;
            int[] cursor={at};int sf=d.uleb(cursor),inf=d.uleb(cursor),dm=d.uleb(cursor),vm=d.uleb(cursor);
            if((long)sf+inf+dm+vm>1000000)throw new IOException(Local.of("Некорректная таблица DEX"));
            for(int j=0;j<sf+inf;j++){d.uleb(cursor);d.uleb(cursor);}
            for(int j=0;j<dm+vm;j++){d.uleb(cursor);int flags=d.uleb(cursor);d.uleb(cursor);if((flags&256)!=0)report.add(Local.of("Нативные методы вне проверки Java-кода"),true);}
        }
        report.digest=sha256(data);return report;
    }
    private static final class Dex {
        final byte[] b;private final Map<Integer,String> decoded=new HashMap<Integer,String>();private int textBytes,ulebReads;final int stringCount,stringOff,typeCount,typeOff,methodCount,methodOff;
        Dex(byte[] bytes)throws Exception{
            b=bytes;
            if(b.length<112||b.length>MAX_DEX||b[0]!='d'||b[1]!='e'||b[2]!='x'||b[3]!=10||b[7]!=0)throw new IOException(Local.of("Некорректный DEX"));
            String version=new String(b,4,3,"US-ASCII");if(!Arrays.asList("035","037","038","039","040").contains(version))throw new IOException(Local.of("Неподдерживаемая версия DEX"));
            if(u32(32)!=b.length||u32(36)!=112||u32(40)!=0x12345678)throw new IOException(Local.of("Некорректный заголовок DEX"));
            MessageDigest sha=MessageDigest.getInstance("SHA-1");sha.update(b,32,b.length-32);
            if(!Arrays.equals(sha.digest(),Arrays.copyOfRange(b,12,32)))throw new IOException(Local.of("Повреждён DEX: SHA-1"));
            Adler32 checksum=new Adler32();checksum.update(b,12,b.length-12);
            if(checksum.getValue()!=(raw32(8)&0xffffffffL))throw new IOException(Local.of("Повреждён DEX: checksum"));
            stringCount=u32(56);stringOff=u32(60);typeCount=u32(64);typeOff=u32(68);methodCount=u32(88);methodOff=u32(92);
            table(stringOff,stringCount,4);table(typeOff,typeCount,4);table(methodOff,methodCount,8);
            // Ensure all string entries are bounded, including unused entries.
            for(int i=0;i<stringCount;i++)string(i);
        }
        int raw32(int p)throws IOException{range(p,4);return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|((b[p+3]&255)<<24);}
        int u32(int p)throws IOException{int value=raw32(p);if(value<0)throw new IOException(Local.of("Слишком большое значение DEX"));return value;}
        int u16(int p)throws IOException{range(p,2);return (b[p]&255)|((b[p+1]&255)<<8);}
        void range(int p,long n)throws IOException{if(p<0||n<0||(long)p+n>b.length)throw new IOException(Local.of("Таблица за границами DEX"));}
        void table(int off,int count,int size)throws IOException{if(count>100000||(count==0)!=(off==0))throw new IOException(Local.of("Некорректная таблица DEX"));range(off,(long)count*size);}
        int uleb(int[] pos)throws IOException{if(++ulebReads>1000000)throw new IOException(Local.of("Слишком сложный DEX для проверки"));long value=0;for(int i=0;i<5;i++){range(pos[0],1);int v=b[pos[0]++]&255;value|=(long)(v&127)<<(i*7);if((v&128)==0){if(value>Integer.MAX_VALUE)throw new IOException(Local.of("Переполнение DEX"));return (int)value;}}throw new IOException(Local.of("Некорректный ULEB128"));}
        String string(int index)throws IOException{
            if(index<0||index>=stringCount)throw new IOException(Local.of("Индекс строки DEX"));
            int offset=u32(stringOff+index*4);String ready=decoded.get(offset);if(ready!=null)return ready;
            int[] pos={offset};uleb(pos);int start=pos[0];
            while(true){range(pos[0],1);if(b[pos[0]++]==0)break;
            // One string may be as long as it likes: a plugin that carries a picture carries it as text, and
            // the dex compiler joins the pieces of one back together however the author split them.
            if(++textBytes>MAX_DEX)throw new IOException(Local.of("Слишком большие строки DEX"));}
            ready=new String(b,start,pos[0]-start-1,java.nio.charset.StandardCharsets.UTF_8);decoded.put(offset,ready);return ready;
        }
        String type(int index)throws IOException{if(index<0||index>=typeCount)throw new IOException(Local.of("Индекс типа DEX"));return string(u32(typeOff+index*4));}
    }
}
