package cat.narezany.margyt;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** A small, flat .mtp archive. Limits apply to inflated bytes, not ZIP metadata. */
final class PluginPackage {
    private PluginPackage() {}
    static void unpack(InputStream input,File folder)throws IOException{
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException(Local.of("Не удалось подготовить плагин"));
        Set<String> seen=new HashSet<String>();long total=0;
        try(ZipInputStream zip=new ZipInputStream(input)){
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();
                if(entry.isDirectory()||!Arrays.asList("manifest.json","classes.dex","icon.png").contains(name)||!seen.add(name))throw new IOException(Local.of("Плагин содержит лишние, вложенные или повторяющиеся файлы"));
                int limit=name.equals("classes.dex")?PluginAudit.MAX_DEX:name.equals("icon.png")?1024*1024:65536;
                File out=new File(folder,name);
                if(!out.getCanonicalPath().startsWith(folder.getCanonicalPath()+File.separator))throw new IOException(Local.of("Недопустимый путь плагина"));
                try(OutputStream sink=new FileOutputStream(out)){
                    int n,count=0;while((n=zip.read(buffer))!=-1){count+=n;total+=n;if(count>limit||total>PluginAudit.MAX_DEX+1024*1024+65536)throw new IOException(Local.of("Плагин превышает ограничение размера"));sink.write(buffer,0,n);}
                }
            }
        }
        if(!seen.contains("manifest.json")||!seen.contains("classes.dex"))throw new IOException(Local.of("В плагине нет manifest.json или classes.dex"));
    }
}
