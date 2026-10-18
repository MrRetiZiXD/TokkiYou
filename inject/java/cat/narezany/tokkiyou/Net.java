package cat.narezany.tokkiyou;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetching things, off the main thread and without dragging in a library.
 *
 * The mod needs very little of this -- a json file it reads every few minutes,
 * a picture now and then -- and `HttpURLConnection` is already in every
 * Android. Anything larger would mean shipping a networking stack inside
 * somebody else's apk, which is a great deal of weight for two requests.
 */
public final class Net {

    private Net() {}

    private static final int TIMEOUT = 15000;
    private static final int LIMIT = 4 * 1024 * 1024;

    private static boolean secure(String address) {
        try {
            java.net.URI uri = new java.net.URI(address);
            boolean okay = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null;
            if (!okay) Diary.note("network: refused non-HTTPS endpoint");
            return okay;
        } catch (Throwable error) {
            Diary.note("network: invalid endpoint");
            return false;
        }
    }

    /** Run something on a thread of the mod's own, named so it is findable. */
    public static void away(final String what, final Runnable work) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work.run();
                } catch (Throwable error) {
                    Diary.note(what + ": " + error);
                }
            }
        }, "tokkiyou-" + what);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.start();
    }

    /** The bytes at a url, or null. Never throws, never runs on the main thread. */
    /** What came back, and whether it was worth coming back at all. */
    public static final class Answer {
        public final byte[] body;
        public final String tag;
        public final boolean unchanged;

        Answer(byte[] body, String tag, boolean unchanged) {
            this.body = body;
            this.tag = tag;
            this.unchanged = unchanged;
        }
    }

    /**
     * Fetch, saying what was fetched last time.
     *
     * A server that keeps an ETag can answer "the same as before" in a few
     * bytes, and a list of badges asked for every couple of minutes is the
     * same as before almost every time. Without this the mod would download
     * the whole list all day to learn nothing.
     */
    public static Answer fetch(String url, String tag) {
        java.net.HttpURLConnection link = null;
        try {
            if (!secure(url)) return null;
            link = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            link.setInstanceFollowRedirects(false);
            link.setConnectTimeout(15000);
            link.setReadTimeout(20000);
            link.setRequestProperty("User-Agent", "TokkiYou");
            if (tag != null && tag.length() > 0) {
                link.setRequestProperty("If-None-Match", tag);
            }
            int code = link.getResponseCode();
            if (code == 304) return new Answer(null, tag, true);
            if (code != 200) return null;

            String fresh = link.getHeaderField("ETag");
            java.io.InputStream in = link.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int got;
            while ((got = in.read(buffer)) > 0) {
                if (out.size() + got > LIMIT) return null;
                out.write(buffer, 0, got);
            }
            in.close();
            return new Answer(out.toByteArray(), fresh, false);
        } catch (Throwable error) {
            Diary.note("fetch: " + error);
            return null;
        } finally {
            if (link != null) link.disconnect();
        }
    }

    /**
     * Send some json and read the answer.
     *
     * Used for the few things a phone tells the server: which account it is,
     * and what that account wants shown.
     */
    public static String post(String url, String json) {
        return postWire(url, json, false);
    }

    /** Return bounded JSON errors too; callers still check the server's ok flag. */
    public static String postResult(String url, String json) {
        return postWire(url, json, true);
    }

    private static String postWire(String url, String json, boolean includeErrors) {
        return postWire(url,json,includeErrors,65536);
    }
    private static String postWire(String url, String json, boolean includeErrors,int limit) {
        java.net.HttpURLConnection link = null;
        try {
            if (!secure(url) || json == null || json.length() > limit) return null;
            link = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            link.setInstanceFollowRedirects(false);
            link.setConnectTimeout(15000);
            link.setReadTimeout(20000);
            link.setRequestMethod("POST");
            link.setDoOutput(true);
            link.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            link.setRequestProperty("User-Agent", "TokkiYou");

            byte[] body = json.getBytes("UTF-8");
            link.setFixedLengthStreamingMode(body.length);
            java.io.OutputStream out = link.getOutputStream();
            out.write(body);
            out.close();

            int code = link.getResponseCode();
            java.io.InputStream in = code >= 400 ? link.getErrorStream()
                    : link.getInputStream();
            if (in == null) return null;
            java.io.ByteArrayOutputStream read = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int got;
            while ((got = in.read(buffer)) > 0) {
                if (read.size() + got > LIMIT) return null;
                read.write(buffer, 0, got);
            }
            in.close();
            String said = new String(read.toByteArray(), "UTF-8");
            if (code != 200) Diary.note("post " + url + ": " + code + " " + said);
            return code == 200 || includeErrors ? said : null;
        } catch (Throwable error) {
            Diary.note("post: " + error);
            return null;
        } finally {
            if (link != null) link.disconnect();
        }
    }

    /** What a request came back with, when the refusal itself matters. */
    public static final class Said {
        public final int code;
        public final String body;

        Said(int code, String body) {
            this.code = code;
            this.body = body;
        }

        public boolean ok() {
            return code == 200;
        }
    }

    /**
     * Send json and keep the answer whatever it was.
     *
     * `post` throws refusals away, which is right for saving a setting and
     * wrong for proving an account: "the code is not in your bio yet" and
     * "tiktok did not answer" need different things said to the person.
     */
    public static Said talk(String url, String json) {
        java.net.HttpURLConnection link = null;
        try {
            link = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            link.setConnectTimeout(15000);
            link.setReadTimeout(40000);
            link.setRequestMethod("POST");
            link.setDoOutput(true);
            link.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            link.setRequestProperty("User-Agent", "TokkiYou");

            byte[] body = json.getBytes("UTF-8");
            link.setFixedLengthStreamingMode(body.length);
            java.io.OutputStream out = link.getOutputStream();
            out.write(body);
            out.close();

            int code = link.getResponseCode();
            java.io.InputStream in = code >= 400 ? link.getErrorStream()
                    : link.getInputStream();
            String said = "";
            if (in != null) {
                java.io.ByteArrayOutputStream read = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int got;
                while ((got = in.read(buffer)) > 0) read.write(buffer, 0, got);
                in.close();
                said = new String(read.toByteArray(), "UTF-8");
            }
            return new Said(code, said);
        } catch (Throwable error) {
            Diary.note("talk: " + error);
            return new Said(0, "");
        } finally {
            if (link != null) link.disconnect();
        }
    }

    /** A POST whose body is the thing itself, not json wrapped around it. */
    public static String send(String url, String kind, byte[] body) {
        Said said = deliver(url, kind, body);
        return said.ok() ? said.body : null;
    }

    /** The same, keeping the refusal for whoever has to explain it. */
    public static Said deliver(String url, String kind, byte[] body) {
        java.net.HttpURLConnection link = null;
        try {
            link = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            link.setConnectTimeout(15000);
            link.setReadTimeout(60000);
            link.setRequestMethod("POST");
            link.setDoOutput(true);
            link.setRequestProperty("Content-Type", kind);
            link.setRequestProperty("User-Agent", "TokkiYou");
            link.setFixedLengthStreamingMode(body.length);
            java.io.OutputStream out = link.getOutputStream();
            out.write(body);
            out.close();

            int code = link.getResponseCode();
            java.io.InputStream in = code >= 400 ? link.getErrorStream() : link.getInputStream();
            java.io.ByteArrayOutputStream read = new java.io.ByteArrayOutputStream();
            if (in != null) {
                byte[] buffer = new byte[8192];
                int got;
                while ((got = in.read(buffer)) > 0) read.write(buffer, 0, got);
                in.close();
            }
            return new Said(code, new String(read.toByteArray(), "UTF-8"));
        } catch (Throwable error) {
            Diary.note("net: " + error);
            return new Said(0, "");
        } finally {
            if (link != null) link.disconnect();
        }
    }

    public static byte[] bytes(String url) {
        HttpURLConnection connection = null;
        try {
            if (!secure(url)) return null;
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "TokkiYou");
            if (connection.getResponseCode() / 100 != 2) return null;

            InputStream in = connection.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read, total = 0;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > LIMIT) return null;  // nothing the mod wants is this big
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static String text(String url) {
        byte[] raw = bytes(url);
        if (raw == null) return null;
        try {
            return new String(raw, "UTF-8");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Told how far along a download is, in whole percent. */
    public interface Along {
        void at(int percent, long got, long total);
    }

    /**
     * Fetch straight to a file, saying how it is going.
     *
     * An apk is tens of megabytes, which is the one thing here worth watching
     * and the one thing not to hold in memory.
     */
    public static boolean download(String url, File into, Along along) {return download(url,into,along,null);}
    public static boolean download(String url,File into,Along along,DownloadTasks.Control control) {
        File partial=new File(into.getParentFile(),into.getName()+".part");
        HttpURLConnection connection = null;
        try {
            if(control!=null && control.cancelled())return false;
            String address = url;
            for (int hop = 0; ; hop++) {
                if (!secure(address)) return false;
                connection = (HttpURLConnection) new URL(address).openConnection();
                if(control!=null){control.connection=connection;if(control.cancelled())return false;}
                connection.setConnectTimeout(TIMEOUT);
                connection.setReadTimeout(TIMEOUT);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "TokkiYou");
                int code = connection.getResponseCode();
                if (code / 100 == 2) break;
                if (!(code == 301 || code == 302 || code == 303
                        || code == 307 || code == 308) || hop >= 5) return false;
                String location = connection.getHeaderField("Location");
                if (location == null || location.isEmpty()) return false;
                address = new URL(new URL(address), location).toString();
                connection.disconnect();
                connection = null;
            }

            long total = connection.getContentLength();
            if(control!=null && control.limit>0 && total>control.limit)throw new java.io.IOException("Download size limit");
            File parent = into.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();

            InputStream in = connection.getInputStream();
            OutputStream out = new FileOutputStream(partial);
            try {
                byte[] buffer = new byte[65536];
                long got = 0;
                int read, told = -1;
                while ((read = in.read(buffer)) != -1) {
                    if(control!=null && control.cancelled())return false;
                    if(control!=null && control.limit>0 && got+read>control.limit)throw new java.io.IOException("Download size limit");
                    out.write(buffer, 0, read);
                    got += read;
                    int percent = total > 0 ? (int) (got * 100 / total) : -1;
                    if (along != null && percent != told) {
                        told = percent;
                        along.at(percent, got, total);
                    }
                }
                if(total>0 && got<total)throw new java.io.IOException("Incomplete download");
            } finally {
                in.close();
                out.close();
            }
            return (control==null || !control.cancelled()) && partial.renameTo(into);
        } catch (Throwable error) {
            Diary.note("download: " + error);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
            if(control!=null)control.connection=null;partial.delete();
        }
    }

    /** Write bytes where they will still be after a restart. */
    public static boolean save(File file, byte[] data) {
        if (data == null) return false;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            OutputStream out = new FileOutputStream(file);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static byte[] read(File file) {
        try {
            if (!file.isFile()) return null;
            java.io.InputStream in = new java.io.FileInputStream(file);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}
