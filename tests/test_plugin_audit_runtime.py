"""Run the actual non-executing DEX inspector and archive boundary checks."""
import hashlib, pathlib, shutil, struct, subprocess, tempfile, unittest, zlib
ROOT=pathlib.Path(__file__).resolve().parents[1]

def dex(owner='Ljava/lang/String;',method='valueOf',literal='',source=None,define=False):
    strings=[owner,method,literal,'V'];
    if source is not None:strings.append(source)
    data=bytearray(112+len(strings)*4+8+8+12)
    so=112;to=so+len(strings)*4;mo=to+8;po=mo+8
    for i,text in enumerate(strings):
        struct.pack_into('<I',data,so+i*4,len(data));raw=text.encode();length=len(raw);prefix=bytearray()
        while length>=128:prefix.append((length&127)|128);length>>=7
        prefix.append(length);data+=prefix+raw+b'\0'
    struct.pack_into('<II',data,to,0,3)
    struct.pack_into('<HHI',data,mo,0,0,1)
    struct.pack_into('<III',data,po,3,1,0)
    if define:
        class_off=len(data);data+=struct.pack('<IIIIIIII',0,1,0xffffffff,0,4 if source is not None else 0xffffffff,0,0,0)
        struct.pack_into('<II',data,96,1,class_off)
    data[:8]=b'dex\n035\0'
    for offset,value in {32:len(data),36:112,40:0x12345678,56:len(strings),60:so,64:2,68:to,72:1,76:po,88:1,92:mo,104:len(data)-(po+12),108:po+12}.items():struct.pack_into('<I',data,offset,value)
    data[12:32]=hashlib.sha1(data[32:]).digest();struct.pack_into('<I',data,8,zlib.adler32(data[12:])&0xffffffff)
    return data

HARNESS=r'''package cat.narezany.tokkiyou;
import java.io.*;import java.util.zip.*;
class AuditHarness {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static PluginAudit.Report scan(String path)throws Exception{return PluginAudit.dex(PluginAudit.read(new File(path),PluginAudit.MAX_DEX));}
 static byte[] pack(String name,int amount)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry(name));byte[] block=new byte[4096];for(int i=0;i<amount;i+=block.length)zip.write(block,0,Math.min(block.length,amount-i));zip.closeEntry();}return out.toByteArray();}
 static void denied(byte[] zip,File dir)throws Exception {try{PluginPackage.unpack(new ByteArrayInputStream(zip),dir);throw new AssertionError("archive accepted");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  check(!scan(args[0]).blocked&&scan(args[0]).findings.isEmpty(),"ordinary strings must not flag literal exec");
  // what it finds is said and nothing is refused: a plugin is code running as TikTok whatever it calls
  check(!scan(args[1]).blocked&&!scan(args[1]).findings.isEmpty(),"Runtime.exec said, not refused");check(!scan(args[2]).blocked&&!scan(args[2]).findings.isEmpty(),"dynamic loader said, not refused");
  check(!scan(args[3]).blocked&&!scan(args[3]).findings.isEmpty(),"network reported, not automatically malicious");
  check(!scan(args[4]).findings.isEmpty(),"reflection needs review");check(!scan(args[5]).findings.isEmpty(),"host API network reported");
  byte[] broken=PluginAudit.read(new File(args[0]),PluginAudit.MAX_DEX);broken[broken.length-2]^=1;
  check(!PluginAudit.dex(broken).findings.isEmpty()&&!PluginAudit.dex(broken).blocked,"a damaged DEX is said to be unreadable, and that is all");
  for(int i=7;i<=11;i++)check(!scan(args[i]).blocked,"short names, missing sources and long strings are nobody's business");
  check(scan(args[1]).summary().contains("What the plugin"),"the summary is written in the phone's language");
  File dir=new File(args[6]);denied(pack("../classes.dex",1),new File(dir,"path"));denied(pack("lib.so",1),new File(dir,"native"));denied(pack("manifest.json",65537),new File(dir,"bomb"));denied(pack("classes.dex",1),new File(dir,"missing"));
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){
   for(String name:new String[]{"manifest.json","classes.dex"}){zip.putNextEntry(new ZipEntry(name));zip.write(name.equals("classes.dex")?PluginAudit.read(new File(args[0]),PluginAudit.MAX_DEX):"{}".getBytes("UTF-8"));zip.closeEntry();}}
  File valid=new File(dir,"valid");PluginPackage.unpack(new ByteArrayInputStream(bytes.toByteArray()),valid);String before=PluginAudit.folder(valid).digest;
  try(FileOutputStream out=new FileOutputStream(new File(valid,"manifest.json"))){out.write("{\"id\":\"changed\"}".getBytes("UTF-8"));}
  check(!before.equals(PluginAudit.folder(valid).digest),"review digest covers manifest as well as DEX");
  check(PluginCatalog.resolve("https://example.com/catalog.json","files/demo.ctt").equals("https://example.com/files/demo.ctt"),"relative catalog URL");
  for(String bad:new String[]{"http://example.com/a","https://name:pass@example.com/a","file:///data/a"})try{PluginCatalog.https(bad);throw new AssertionError("unsafe URL accepted");}catch(Exception expected){}
  check(!PluginCatalog.id("../demo")&&!PluginCatalog.digest("123"),"catalog metadata validation");
 }
}'''

class PluginAuditRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_inspector_archive_and_catalog_boundaries(self):
  with tempfile.TemporaryDirectory() as directory:
   work=pathlib.Path(directory);base=ROOT/'inject/java/cat/narezany/tokkiyou'
   sources={f'cat/narezany/tokkiyou/{name}.java':(base/f'{name}.java').read_text(encoding='utf-8') for name in ('PluginAudit','PluginPackage','PluginCatalog')}
   sources.update({'cat/narezany/tokkiyou/AuditHarness.java':HARNESS,
    'android/content/Context.java':'package android.content;public class Context {public SharedPreferences getSharedPreferences(String n,int m){return null;}}',
    'android/content/SharedPreferences.java':'package android.content;public interface SharedPreferences {String getString(String k,String d);boolean getBoolean(String k,boolean d);boolean contains(String k);Editor edit();interface Editor {Editor putString(String k,String v);Editor putBoolean(String k,boolean v);void apply();}}',
    'cat/narezany/tokkiyou/Text.java':'package cat.narezany.tokkiyou;final class Text{static final String PLUGIN_AUDIT_HEAD="What the plugin\'s code does:",PLUGIN_AUDIT_CLEAN="Nothing.",PLUGIN_AUDIT_NOTE="Runs as TikTok.";}',
    'cat/narezany/tokkiyou/Badges.java':'package cat.narezany.tokkiyou;final class Badges {static final String SERVER="https://example.com/api";} '})
   __import__("android_doubles").with_mod(sources)
   for name,code in sources.items():p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8')
   built=subprocess.run(['javac','-source','8','-target','8','-encoding','UTF-8','-d',directory]+[str(work/name) for name in sources],capture_output=True,text=True);self.assertEqual(0,built.returncode,built.stderr)
   cases=[dex(literal='Runtime.exec is documentation'),dex('Ljava/lang/Runtime;','exec'),dex('Ldalvik/system/DexClassLoader;','loadClass'),dex('Ljava/net/URL;','openConnection'),dex('Ljava/lang/reflect/Method;','invoke'),dex('Lcat/narezany/tokkiyou/plugin/PluginContext;','fetch')]
   paths=[]
   for i,data in enumerate(cases):p=work/f'{i}.dex';p.write_bytes(data);paths.append(str(p))
   extra_cases=[dex('Lsample/DemoPlugin;','onStart',source='DemoPlugin.java',define=True),dex('Lsample/a;','onStart',source='DemoPlugin.java',define=True),dex('Lsample/DemoPlugin;','onStart',define=True),dex('Lsample/DemoPlugin;','a',source='DemoPlugin.java',define=True),dex('Lsample/DemoPlugin;','onStart',literal='Z'*300,source='DemoPlugin.java',define=True)]
   extra_paths=[]
   for i,data in enumerate(extra_cases):p=work/f'extra{i}.dex';p.write_bytes(data);extra_paths.append(str(p))
   run=subprocess.run(['java','-cp',directory,'cat.narezany.tokkiyou.AuditHarness']+paths+[str(work/'archives')]+extra_paths,capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
