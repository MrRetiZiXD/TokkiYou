package cat.narezany.tokkiyou;
import android.content.Context;
import java.net.URI;

/** The store has its own endpoint, independent of profile/badge infrastructure. */
final class PluginCatalog {
    private PluginCatalog() {}
    private static final String DEFAULT=Badges.SERVER+"/plugins";
    static String endpoint(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences("tokkiyou_store",0);
        if(prefs.contains("catalog_enabled")&&!prefs.getBoolean("catalog_enabled",true))return "";
        String address=prefs.getString("catalog",null);
        return address==null||address.isEmpty()?DEFAULT:address;
    }
    static void endpoint(Context context,String address)throws Exception{
        address=address.trim();if(!address.isEmpty())https(address);
        context.getSharedPreferences("tokkiyou_store",0).edit().putString("catalog",address)
                .putBoolean("catalog_enabled",!address.isEmpty()).apply();
    }
    static String resolve(String base,String path)throws Exception{
        if(path==null||path.isEmpty())throw new Exception(Local.of("Нет ссылки на файл плагина"));
        // our own server answers under a prefix and names its files from the
        // prefix down, the way 1.x read them
        if(path.startsWith("/")&&!path.startsWith("//")&&base.startsWith(Badges.SERVER+"/"))return https(Badges.SERVER+path);
        return https(new URI(https(base)).resolve(path).toString());
    }
    static String https(String address)throws Exception{
        if(address.length()>2048)throw new Exception(Local.of("Слишком длинный адрес каталога"));
        URI uri=new URI(address);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new Exception(Local.of("Каталог и файлы должны использовать HTTPS без логина в адресе"));
        return uri.toString();
    }
    static boolean id(String id){return id!=null&&id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");}
    static boolean digest(String digest){return digest!=null&&digest.matches("[a-fA-F0-9]{64}");}
}
