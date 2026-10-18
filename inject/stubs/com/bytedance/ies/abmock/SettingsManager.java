package com.bytedance.ies.abmock;

/**
 * TikTok's A/B facade: every feature it ships is behind a named flag, and the
 * value of each is whatever the server decided for this account.
 *
 * The class name is real. The method names are not -- they are what the
 * obfuscator produced for 46.9.42, and a later release will spell them
 * differently. That costs nothing: the rewrite matches them by name, so a
 * renamed method simply is not found, no call site changes, and the overrides
 * quietly do nothing. Nothing here is called unless its call site was rewritten
 * in the first place.
 */
public class SettingsManager {

    public static boolean LIZ(String key, boolean fallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static int LJ(String key, int fallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static long LJFF(String key, long fallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static String LJI(String key, String fallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static float LIZJ(String key, float fallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static double LIZIZ(String key, double fallback) {
        throw new UnsupportedOperationException("stub");
    }
}
