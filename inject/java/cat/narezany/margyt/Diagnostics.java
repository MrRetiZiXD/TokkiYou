package cat.narezany.margyt;

/** Status that can be copied without account credentials or session data. */
final class Diagnostics {
    private Diagnostics() {}

    /**
     * Everything a developer wants in one paste: the device, the build, what the
     * hooks did, every plugin, and the whole diary. Never the account id, a
     * token or any session data.
     */
    /**
     * What the phone is told to say about where it is, and what it would say
     * by itself: countries only, which is as much as the choice in the mod's
     * own settings gives away. A profile or a search that will not load for
     * some people and loads for others may come down to these two disagreeing.
     */
    private static String region(android.content.Context context) {
        StringBuilder out = new StringBuilder();
        try {
            out.append("told to TikTok: ").append(Margy.isEnabled() ? Margy.current()[Margy.ISO] + " (" + Margy.current()[Margy.CARRIER] + ")" : "nothing, the mod's region is off").append('\n');
            android.telephony.TelephonyManager phone = context == null ? null
                    : (android.telephony.TelephonyManager) context.getSystemService(android.content.Context.TELEPHONY_SERVICE);
            if (phone == null) return out.append("the phone itself: unknown\n").toString();
            boolean card = false;
            try { card = phone.getSimState() == android.telephony.TelephonyManager.SIM_STATE_READY; } catch (Throwable ignored) { }
            out.append("the phone itself: ").append(card ? "a card from " + said(phone.getSimCountryIso()) : "no card")
                    .append(", on a network in ").append(said(phone.getNetworkCountryIso())).append('\n');
            out.append("system language and country: ").append(java.util.Locale.getDefault()).append('\n');
        } catch (Throwable error) {
            out.append("could not be read: ").append(error.getClass().getSimpleName()).append('\n');
        }
        return out.toString();
    }

    private static String said(String iso) {
        return iso == null || iso.isEmpty() ? "nowhere it will name" : iso;
    }

    static String fullReport() {
        StringBuilder out = new StringBuilder();
        out.append("== TokkiYou full report ==\n");
        out.append("time: ").append(new java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss Z", java.util.Locale.US).format(new java.util.Date()))
                .append('\n');
        out.append("note: no account id, tokens or session data\n\n");

        out.append("== device ==\n");
        out.append("model: ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append('\n');
        out.append("android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (sdk ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        out.append("abi: ").append(android.os.Build.SUPPORTED_ABIS.length > 0
                ? android.os.Build.SUPPORTED_ABIS[0] : "?").append('\n');
        out.append("locale: ").append(java.util.Locale.getDefault()).append('\n');
        android.content.Context context = Margy.context();
        if (context != null) {
            out.append("package: ").append(context.getPackageName());
            try {
                out.append(" ").append(context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).versionName);
            } catch (Throwable ignored) { }
            out.append('\n');
        }

        out.append("\n== build ==\n");
        out.append("mod: ").append(Version.MOD).append('\n');
        out.append("tiktok: ").append(Version.TIKTOK).append('\n');
        out.append("plugin api: ").append(cat.narezany.margyt.plugin.MargyPlugin.API).append('\n');
        out.append("rewrites: ").append(BuildInfo.REWRITES).append(" in ")
                .append(BuildInfo.PATCHED_DEX).append('/').append(BuildInfo.TOTAL_DEX)
                .append(" dex, ").append(BuildInfo.STATIC_TARGETS)
                .append(" version-specific targets\n");

        out.append("\n== lag ==\n").append(Lag.report());

        out.append("\n== region ==\n").append(region(context));

        out.append("\n== appearance ==\n");
        out.append("accent: #").append(String.format(java.util.Locale.US, "%06X",
                Accent.colour() & 0xFFFFFF)).append(" (built with #")
                .append(String.format(java.util.Locale.US, "%06X", Accent.BUILT_WITH & 0xFFFFFF))
                .append(Accent.isDefault() ? ", default" : ", changed").append(")\n");
        out.append("theme engine: ").append(Themes.isEnabled() ? "on" : "off")
                .append(Themes.isMaterial() ? ", material" : "").append('\n');
        out.append("anti-burn-in: ").append(Dim.isEnabled() ? "on" : "off").append('\n');

        out.append("\n== summary ==\n").append(report()).append('\n');

        out.append("\n== plugins ==\n");
        java.util.List<Plugins.Info> plugins = Plugins.list();
        if (plugins.isEmpty()) out.append("none\n");
        for (Plugins.Info plugin : plugins) {
            int rows = 0;
            for (Plugins.Row row : Plugins.rows()) if (row.plugin.equals(plugin.id)) rows++;
            out.append(plugin.id).append(' ').append(plugin.version)
                    .append(" min_api=").append(plugin.minApi)
                    .append(Plugins.isEnabled(plugin.id) ? " on" : " off")
                    .append(" settings_rows=").append(rows);
            if (plugin.trouble != null) out.append(" trouble=").append(plugin.trouble);
            out.append('\n');
        }

        java.util.List<String> lines = Diary.lines();
        out.append("\n== diary (").append(lines.size()).append(" lines) ==\n");
        for (String line : lines) out.append(line).append('\n');
        return out.toString();
    }

    static String report() {
        StringBuilder out = new StringBuilder();
        out.append("TokkiYou ").append(Version.MOD)
                .append(" / TikTok ").append(Version.TIKTOK).append('\n');
        boolean startup = false, activityWatch = false, hookFailure = false;
        for (String line : Diary.lines()) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            startup |= lower.contains("start-up hook ran");
            activityWatch |= lower.contains("watching for the settings screen");
            hookFailure |= lower.contains("hook failed");
        }
        out.append("Hooks: ").append(BuildInfo.REWRITES).append(" bytecode rewrites in ")
                .append(BuildInfo.PATCHED_DEX).append('/').append(BuildInfo.TOTAL_DEX)
                .append(" dex; ").append(BuildInfo.STATIC_TARGETS)
                .append(" version-specific targets\n");
        out.append("Runtime start: ").append(startup ? "seen" : "not recorded")
                .append("; activity watcher: ")
                .append(activityWatch ? "registered" : "not recorded")
                .append(hookFailure ? "; startup hook error recorded" : "")
                .append('\n');
        out.append("Theme hooks: color interception built; ")
                .append(Themes.isEnabled() ? "theme engine enabled" : "theme engine disabled")
                .append("; ").append(AppearanceColors.ROLES.length)
                .append(" manual colour roles\n");
        android.content.Context context = Margy.context();
        EmojiPackManager.Pack pack = EmojiPackManager.find(context, Fonts.emoji());
        out.append("Emoji: ").append(pack == null ? "system" : pack.name)
                .append(pack == null || Fonts.SYSTEM.equals(pack.id)
                        ? "" : " v" + pack.version)
                .append("; ").append(Fonts.emojiReady(context) ? "ready" : "system fallback")
                .append("; Android 10+ required for custom fallback\n");
        out.append("Anti-burn-in: ").append(Dim.isEnabled() ? "enabled" : "disabled")
                .append("; only named UI leaves are eligible; media exclusions need device check\n");
        int loaded = 0, disabled = 0, failed = 0, incompatible = 0;
        for (Plugins.Info plugin : Plugins.list()) {
            if (plugin.trouble != null) {
                if (plugin.trouble.contains("TikTok") || plugin.trouble.contains("api"))
                    incompatible++;
                else failed++;
            } else if (Plugins.isEnabled(plugin.id)) loaded++;
            else disabled++;
        }
        out.append("Plugins: ").append(loaded).append(" loaded, ")
                .append(disabled).append(" disabled, ")
                .append(incompatible).append(" incompatible, ")
                .append(failed).append(" failed\n");
        out.append("Streak auto-renew: ")
                .append(Streaks.isEnabled() ? "enabled; delivery cannot be confirmed"
                        : "disabled")
                .append('\n');
        String last = "none";
        for (String line : Diary.lines()) {
            String lower = line.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("error") || lower.contains("failed")
                    || lower.contains("exception")) {
                last = lower.contains("plugin") ? "plugin"
                        : lower.contains("streak") ? "streak"
                        : lower.contains("theme") ? "theme"
                        : lower.contains("emoji") ? "emoji" : "other";
            }
        }
        out.append("Last error: ").append(last);
        return out.toString();
    }
}
