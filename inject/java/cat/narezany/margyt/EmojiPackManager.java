package cat.narezany.margyt;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Manifest-backed emoji font packs with system fallback and cached metadata. */
public final class EmojiPackManager {
    private EmojiPackManager() {}

    public static final class Pack {
        public final String id;
        public final String name;
        public final String version;
        public final String asset;
        public final int minAndroid;

        Pack(String id, String name, String version, String asset, int minAndroid) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.asset = asset;
            this.minAndroid = minAndroid;
        }
    }

    private static volatile List<Pack> cached;

    public static List<Pack> packs(Context context) {
        if (context == null) context = Margy.context();
        List<Pack> known = cached;
        if (known != null) return known;
        ArrayList<Pack> found = new ArrayList<Pack>();
        found.add(new Pack(Fonts.SYSTEM, "System", "device", null, 1));
        if (context != null) {
            try {
                InputStream input = context.getAssets().open("margyt/emoji/packs.json");
                byte[] raw;
                try {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] block = new byte[4096];
                    int read;
                    while ((read = input.read(block)) != -1) {
                        if (bytes.size() + read > 65536) throw new IllegalArgumentException("emoji manifest too large");
                        bytes.write(block, 0, read);
                    }
                    raw = bytes.toByteArray();
                } finally { input.close(); }
                JSONArray entries = new JSONObject(new String(raw, "UTF-8"))
                        .optJSONArray("packs");
                if (entries != null) for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "");
                    String name = item.optString("name", "");
                    String version = item.optString("version", "");
                    String asset = item.optString("asset", "");
                    int min = item.optInt("minAndroid", 29);
                    if (!id.matches("[a-z0-9_-]{1,32}") || name.length() < 1
                            || name.length() > 48 || version.length() > 24
                            || !validAsset(asset) || min < 1 || min > 100) continue;
                    if (contains(found, id)) continue;
                    found.add(new Pack(id, name, version, asset, min));
                }
            } catch (Throwable error) {
                Diary.note("emoji manifest: " + error.getClass().getSimpleName());
            }
        }
        found.add(new Pack(Fonts.EMOJI_FILE, "Custom font", "user", null, 29));
        known = Collections.unmodifiableList(found);
        if (context != null) cached = known;
        return known;
    }

    public static Pack find(Context context, String id) {
        for (Pack pack : packs(context)) if (pack.id.equals(id)) return pack;
        return null;
    }

    public static String asset(Context context, String id) {
        Pack pack = find(context, id);
        return pack == null || pack.asset == null ? null : "margyt/" + pack.asset;
    }

    public static boolean ready(Context context, String id) {
        Pack pack = find(context, id);
        if (pack == null) return false;
        if (Fonts.EMOJI_FILE.equals(id))
            return context != null && Fonts.emojiFile(context, id).isFile();
        if (pack.asset == null) return true;
        if (android.os.Build.VERSION.SDK_INT < pack.minAndroid || context == null) return false;
        InputStream input = null;
        try {
            input = context.getAssets().open("margyt/" + pack.asset);
            return true;
        } catch (Throwable ignored) { return false; }
        finally { if (input != null) try { input.close(); } catch (Throwable ignored) {} }
    }

    public static String displayName(Context context, String id) {
        Pack pack = find(context, id);
        return pack == null ? "System" : pack.name;
    }

    public static void clearCache() { cached = null; }

    private static boolean contains(List<Pack> packs, String id) {
        for (Pack pack : packs) if (pack.id.equals(id)) return true;
        return false;
    }

    private static boolean validAsset(String path) {
        return path != null && path.length() <= 120
                && path.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*\\.(?:ttf|otf)")
                && !path.contains("..") && !path.startsWith("/");
    }
}
