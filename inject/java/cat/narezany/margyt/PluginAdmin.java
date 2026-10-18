package cat.narezany.margyt;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packing a plugin back into one file after its details were edited.
 *
 * Publishing is not done from here: see AdminAccess. What is left of the
 * store's side says so plainly rather than pretending to try.
 */
final class PluginAdmin {

    private PluginAdmin() {}

    static JSONObject call(Context context, JSONObject body) throws Exception {
        throw new Exception(Text.PLUGIN_PUBLISH_PANEL);
    }

    static void show(Context context) {
        Screen.say(Text.PLUGIN_PUBLISH_PANEL);
    }

    static byte[] pack(Plugins.Info info, JSONObject manifest, byte[] icon) throws Exception {
        PluginAudit.Report report = PluginAudit.folder(info.folder);
        if (report.blocked) throw new Exception(report.summary());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            entry(zip, "manifest.json", manifest.toString().getBytes("UTF-8"));
            entry(zip, "classes.dex",
                    PluginAudit.read(new File(info.folder, "classes.dex"), PluginAudit.MAX_DEX));
            if (icon != null) entry(zip, "icon.png", icon);
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, byte[] value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value);
        zip.closeEntry();
    }
}
