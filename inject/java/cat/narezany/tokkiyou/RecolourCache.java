package cat.narezany.tokkiyou;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Recoloured pictures and animations kept on disk.
 *
 * Recolouring is done once per (source, colour). The answer is written here
 * and every later start, and every later load, reads it back instead of
 * repeating the work. The cache is dropped when TikTok is updated (the stamp
 * changes), when a texture pack changes, and old entries are trimmed once
 * the folder grows past {@link #LIMIT}.
 */
final class RecolourCache {
    private RecolourCache() {}

    /** Bump when the recolouring itself changes, so stale answers are not reused. */
    static final String ALGORITHM_VERSION = "2";
    private static final String VERSION = ALGORITHM_VERSION;
    private static final long LIMIT = 96L * 1024 * 1024;
    private static final int LIKE_COLOURS = 6;

    /** The answer "nothing to recolour here" - remembered too. */
    static final byte[] NONE = new byte[0];

    private static volatile File folder;
    private static volatile String stamp;
    private static volatile int writes;
    private static volatile long epoch;
    private static volatile boolean clearing;

    private static final ExecutorService writer =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable job) {
                    Thread thread = new Thread(job, "tokkiyou-recolour-write");
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static synchronized File folder() {
        File known = folder;
        if (known != null) return known;
        Context context = TokkiYou.context();
        if (context == null) return null;
        try {
            File dir = new File(context.getFilesDir(), "tokkiyou/recolour-cache");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            // A different TikTok build means different source art.
            long updated = 0;
            try {
                updated = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            } catch (Throwable ignored) { }
            stamp = VERSION + "|" + updated;
            File mark = new File(dir, "stamp");
            String saved = readText(mark);
            if (!stamp.equals(saved)) {
                wipe(dir);
                writeText(mark, stamp);
            }
            folder = dir;
            return dir;
        } catch (Throwable error) {
            return null;
        }
    }

    private static String name(String key) {
        return hex(digest((stamp + "|" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    static String likeKey(String animation, int colour) {
        return animation + ":" + Integer.toHexString(colour & 0xFFFFFF)
                + ":" + ALGORITHM_VERSION;
    }

    /** Keep all frames of one colour together so the six-colour LRU is exact. */
    private static File entry(File dir, String key, boolean create) {
        if (key.startsWith("png|")) {
            String[] parts = key.split("\\|", 4);
            if (parts.length == 4 && parts[1].matches("[0-9a-fA-F]{1,6}") && parts[2].equals(ALGORITHM_VERSION)) {
                File shelf = new File(new File(dir, "like"), parts[1].toLowerCase(java.util.Locale.ROOT));
                if (create) shelf.mkdirs();
                return new File(shelf, name(key));
            }
        }
        if (!key.startsWith("lottie|")) return new File(dir, name(key));
        int version = key.lastIndexOf(':');
        int colour = key.lastIndexOf(':', version - 1);
        if (version < 0 || colour < 0 || !key.substring(version + 1).equals(ALGORITHM_VERSION))
            return new File(dir, name(key));
        String rgb = key.substring(colour + 1, version);
        if (!rgb.matches("[0-9a-fA-F]{1,6}")) return new File(dir, name(key));
        File shelf = new File(new File(dir, "like"), rgb.toLowerCase(java.util.Locale.ROOT));
        if (create) shelf.mkdirs();
        return new File(shelf, name(key));
    }

    /** The saved bytes, {@link #NONE} for "unchanged", or null if never seen. */
    static byte[] get(String key) {
        if (clearing) return null;
        try {
            File dir = folder();
            if (dir == null) return null;
            File file = entry(dir, key, false);
            if (!file.isFile()) return null;
            file.getParentFile().setLastModified(System.currentTimeMillis());
            byte[] all = new byte[(int) file.length()];
            FileInputStream in = new FileInputStream(file);
            try {
                int at = 0;
                for (int read; at < all.length && (read = in.read(all, at, all.length - at)) > 0;)
                    at += read;
                if (at != all.length) return null;
            } finally {
                try { in.close(); } catch (Throwable ignored) { }
            }
            if (all.length == 0) return null;
            if (all[0] == 0) return NONE;
            byte[] data = new byte[all.length - 1];
            System.arraycopy(all, 1, data, 0, data.length);
            return data;
        } catch (Throwable error) {
            return null;
        }
    }

    /** Save an answer; null means "nothing to recolour". Written off the caller's thread. */
    static void put(final String key, final byte[] data) {
        if (clearing) return;
        final long generation = epoch;
        final byte[] copy = data == null ? null : data.clone();
        try {
            writer.execute(new Runnable() {
                @Override public void run() { if (generation == epoch && !clearing) write(key, copy); }
            });
        } catch (Throwable ignored) { }
    }

    private static void write(String key, byte[] data) {
        try {
            File dir = folder();
            if (dir == null) return;
            File file = entry(dir, key, true);
            File temp = new File(dir, file.getName() + ".tmp");
            FileOutputStream out = new FileOutputStream(temp);
            try {
                out.write(data == null ? 0 : 1);
                if (data != null) out.write(data);
            } finally {
                out.close();
            }
            if (!temp.renameTo(file)) {
                file.delete();
                if (!temp.renameTo(file)) temp.delete();
            }
            if (key.startsWith("lottie|") || key.startsWith("png|")) trimLikes(dir);
            if (++writes % 20 == 0) trim(dir);
        } catch (Throwable ignored) { }
    }

    /** Forget everything: the art it was made from has changed. */
    static void clear() {
        try {
            final File dir = folder != null ? folder : null;
            if (dir == null) return;
            final long generation = ++epoch;
            clearing = true;
            writer.execute(new Runnable() {
                @Override public void run() {
                    try { wipe(dir); writeText(new File(dir, "stamp"), stamp); }
                    finally { if (generation == epoch) clearing = false; }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File file : files) if (file.isFile()) total += file.length();
        if (total <= LIMIT) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (File file : files) {
            if (total <= LIMIT * 3 / 4) break;
            if (!file.isFile() || file.getName().equals("stamp")) continue;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }

    private static void trimLikes(File dir) {
        File[] colours = new File(dir, "like").listFiles(File::isDirectory);
        if (colours == null || colours.length <= LIKE_COLOURS) return;
        java.util.Arrays.sort(colours, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < colours.length - LIKE_COLOURS; i++) wipeTree(colours[i]);
    }

    private static void wipe(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) wipeTree(file);
    }

    private static void wipeTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) wipeTree(child);
        }
        file.delete();
    }

    private static String readText(File file) {
        try {
            if (!file.isFile()) return null;
            FileInputStream in = new FileInputStream(file);
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] block = new byte[256];
                for (int read; (read = in.read(block)) != -1;) bytes.write(block, 0, read);
                return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (Throwable error) {
            return null;
        }
    }

    private static void writeText(File file, String text) {
        try {
            if (text == null) return;
            FileOutputStream out = new FileOutputStream(file);
            try { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            finally { out.close(); }
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ helpers

    private static byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Throwable error) {
            return new byte[] {(byte) java.util.Arrays.hashCode(data)};
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 15, 16))
                .append(Character.forDigit(b & 15, 16));
        return out.toString();
    }

    static String hash(byte[] data) {
        return hex(digest(data));
    }

    static String hash(int[] pixels, int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4 + 8);
        buffer.putInt(width).putInt(height);
        buffer.asIntBuffer().put(pixels);
        return hex(digest(buffer.array()));
    }

    static byte[] png(Bitmap bitmap) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) ? out.toByteArray() : null;
        } catch (Throwable error) {
            return null;
        }
    }

    static Bitmap decode(byte[] png) {
        try {
            return BitmapFactory.decodeByteArray(png, 0, png.length);
        } catch (Throwable error) {
            return null;
        }
    }
}
