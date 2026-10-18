import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.JZlib;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;

/**
 * Deflates every file named N.in in a folder into N.out beside it.
 *
 * With JZlib rather than the zlib of whatever machine this runs on, because
 * the point is the bytes that come out: the patcher on a phone has to deflate
 * the same file to the same bytes, and two builds of zlib do not promise that.
 * One compressor, the same on both sides, does.
 */
public final class Deflate {

    /** The level both sides use. Changing it changes every apk that is patched to. */
    static final int LEVEL = 6;

    public static void main(String[] args) throws Exception {
        File folder = new File(args[0]);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".in"));
        if (files == null) throw new IllegalStateException("no such folder: " + folder);
        java.util.Arrays.stream(files).parallel().forEach(file -> {
            try {
                byte[] out = raw(Files.readAllBytes(file.toPath()));
                String name = file.getName();
                Files.write(new File(folder, name.substring(0, name.length() - 3) + ".out").toPath(), out);
            } catch (Exception broken) {
                throw new RuntimeException(broken);
            }
        });
    }

    /** A raw deflate stream, no header and no checksum: what a zip entry holds. */
    static byte[] raw(byte[] data) throws Exception {
        Deflater deflater = new Deflater(LEVEL, true);
        byte[] buffer = new byte[1 << 16];
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        deflater.setInput(data, 0, data.length, false);
        while (true) {
            deflater.setOutput(buffer, 0, buffer.length);
            int answer = deflater.deflate(JZlib.Z_FINISH);
            out.write(buffer, 0, buffer.length - deflater.avail_out);
            if (answer == JZlib.Z_STREAM_END) break;
            if (answer != JZlib.Z_OK && answer != JZlib.Z_BUF_ERROR) {
                throw new IllegalStateException("deflate answered " + answer);
            }
        }
        deflater.end();
        return out.toByteArray();
    }
}
