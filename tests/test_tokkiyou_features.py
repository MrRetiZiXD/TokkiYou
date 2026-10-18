"""Regression checks for TokkiYou branding, updater metadata and new toggles."""

import json
import os
import unittest

from PIL import Image


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(relative):
    with open(os.path.join(ROOT, relative), encoding="utf-8") as source:
        return source.read()


class TokkiYouMetadataTest(unittest.TestCase):
    def test_launcher_icons_preserve_the_supplied_artwork(self):
        source_path = os.path.join(ROOT, "assets", "tokkiyou-source.png")
        icon_path = os.path.join(ROOT, "icon_out", "play", "ic_launcher-512.png")
        with Image.open(source_path) as source, Image.open(icon_path) as icon:
            self.assertEqual(icon.size, (512, 512))
            self.assertEqual(icon.getpixel((0, 0))[3], 255)
            self.assertEqual(icon.convert("RGBA").getpixel((256, 256)),
                             source.convert("RGBA").resize((358, 358), Image.Resampling.LANCZOS)
                             .getpixel((179, 179)))
            self.assertEqual(icon.getpixel((70, 70)), icon.getpixel((0, 0)))
        from tokkiyou import artwork
        self.assertEqual(artwork.GLYPH[0][1].count("M"), 2)

    def test_release_metadata_and_links(self):
        with open(os.path.join(ROOT, "version.json"), encoding="utf-8") as source:
            release = json.load(source)
        self.assertEqual(release["version"], "1.0.0")
        self.assertEqual(
            release["url"],
            "https://github.com/MrRetiZiXD/TokkiYou/releases/latest/download/tokkiyou.apk",
        )
        self.assertIs(release["force_update"], False)
        self.assertTrue(release["features"])
        self.assertTrue(release["features_ru"])
        self.assertIn("features_uk", release)

    def test_build_defaults_keep_the_java_package(self):
        build = read("tokkiyou/build.py")
        cli = read("tokkiyou/__main__.py")
        self.assertIn('PACKAGE = "cat.narezany.tokkiyou"', build)
        self.assertIn('"%s.tokkiyou" % package', build)
        self.assertIn('"tokkiyou.apk"', cli)
        self.assertIn('prog="tokkiyou"', cli)

    def test_launcher_has_only_the_three_tokkiyou_choices(self):
        build = read("tokkiyou/build.py")
        launcher = read("inject/java/cat/narezany/tokkiyou/Launcher.java")
        icon_list = build[build.index("    ICONS = ["):build.index("    ICON_PACKAGE")]
        self.assertIn('("yellow", "Жёлтая")', icon_list)
        self.assertIn('("neon", "Неоновая")', icon_list)
        self.assertEqual(icon_list.count('("'), 2)
        self.assertIn('if ("yellow".equals(Shots.KEYS[at])) return Text.ICON_YELLOW;', launcher)
        self.assertIn('if ("neon".equals(Shots.KEYS[at])) return Text.ICON_NEON;', launcher)
        self.assertIn("out[0] = DEFAULT", launcher)
        for name in ("yellow", "neon"):
            path = os.path.join(ROOT, "icons", "launcher", name + ".png")
            with Image.open(path) as icon:
                self.assertEqual(icon.size, (512, 512))

    def test_nunito_is_the_default_and_is_packaged_with_both_weights(self):
        fonts = read("inject/java/cat/narezany/tokkiyou/Fonts.java")
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        build = read("tokkiyou/build.py")
        self.assertIn('public static final String NUNITO = "nunito";', fonts)
        self.assertIn("prefs.getString(KEY, NUNITO)", fonts)
        self.assertIn("return (style & Typeface.BOLD) != 0 ? nunito(context, true)", fonts)
        self.assertIn("Nunito-Medium.ttf", build)
        self.assertIn("Nunito-Black.ttf", build)
        self.assertIn("Fonts.NUNITO.equals(which)", settings)
        for weight in ("Nunito-Medium.ttf", "Nunito-Black.ttf"):
            self.assertTrue(os.path.isfile(os.path.join(ROOT, "assets", "tokkiyou", "fonts", weight)))

    def test_avatar_default_and_tiktok_theme_reset_are_available(self):
        shape = read("inject/java/cat/narezany/tokkiyou/AvatarShape.java")
        themes = read("inject/java/cat/narezany/tokkiyou/Themes.java")
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        self.assertIn('DEFAULT = "rounded"', shape)
        self.assertIn("prefs.getString(KEY, DEFAULT)", shape)
        self.assertIn("Text.THEME_PRESET_DARK, Text.THEME_PRESET_BLUE", settings)
        self.assertIn("Text.THEME_RESET", settings)
        self.assertIn("Themes.reset()", settings)
        self.assertIn("AppearanceColors.reset()", themes)

    def test_removed_features_are_not_in_the_settings_or_icon_picker(self):
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        watch = read("inject/java/cat/narezany/tokkiyou/Watch.java")
        build = read("tokkiyou/build.py")
        self.assertNotIn("DepCalc.show", settings)
        self.assertNotIn("fatCat()", settings)
        self.assertNotIn("FUN_SCREAMERS", settings)
        self.assertNotIn("Screamer.attach", watch)
        self.assertNotIn("add_widget(", build)
        self.assertNotIn("tilcayo", build)
        self.assertNotIn("tokkiyoucraft", build)

    def test_diagnostics_sections_are_removed_and_secret_cat_is_local_silent_gif(self):
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        cats = read("inject/java/cat/narezany/tokkiyou/Cats.java")
        build = read("tokkiyou/build.py")
        self.assertNotIn("column.addView(section(Text.DIAGNOSTICS))", settings)
        self.assertNotIn("column.addView(section(Text.DIARY))", settings)
        self.assertNotIn("developerTap();", settings)
        self.assertNotIn("meow(", cats)
        self.assertIn('open("tokkiyou/secret-cat.gif")', cats)
        self.assertIn("Это же секретная котость...", cats)
        self.assertIn("Принёс @mrretizixd", cats)
        self.assertIn('("assets/tokkiyou/secret-cat.gif"', build)
        self.assertNotIn('("assets/tokkiyou/meow.ogg"', build)
        gif_path = os.path.join(ROOT, "assets", "tokkiyou", "secret-cat.gif")
        with open(gif_path, "rb") as source:
            self.assertEqual(source.read(6), b"GIF89a")

    def test_profile_settings_group_and_tutorial_step_are_removed(self):
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        text = read("inject/java/cat/narezany/tokkiyou/Text.java")
        self.assertNotIn('case 2: return Local.of("Профиль")', settings)
        self.assertNotIn('column.addView(section(Text.GRADIENT))', settings)
        self.assertNotIn('column.addView(section(Text.BADGES))', settings)
        self.assertNotIn('column.addView(section(Text.ACCOUNT))', settings)
        self.assertNotIn("Local.of(\"Значки, градиент и аккаунт\")", settings)
        self.assertNotIn("Значки и градиент", text)
        self.assertNotIn("Badges and a gradient", text)
        self.assertNotIn("Badges and the gradient on your name live under Profile", text)

    def test_thanks_hides_requested_credits_and_card_copy_but_keeps_empty_yoomoney(self):
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        text = read("inject/java/cat/narezany/tokkiyou/Text.java")
        self.assertNotIn('person("Claude Opus 5"', settings)
        self.assertNotIn("person(\"someone's mf\"", settings)
        self.assertNotIn("cardRow()", settings)
        self.assertNotIn("CARD_NUMBER", settings)
        self.assertNotIn("THANKS_CLAUDE", text)
        self.assertNotIn("THANKS_TTCUZ", text)
        self.assertIn('linkRow("star", Text.YOOMONEY, Text.YOOMONEY_NOTE, YOOMONEY)', settings)
        self.assertIn('private static final String YOOMONEY = "";', settings)
        link_row = settings[settings.index("private View linkRow("):settings.index("/** The channel card")]
        self.assertIn("url == null || url.length() == 0", link_row)
        self.assertIn("arrow.setVisibility(View.GONE)", link_row)

    def test_gradient_does_not_require_supporter_status_and_badge_is_proof_gated(self):
        service = read("server/badges.py")
        gradient = service[service.index("def gradient(body, ip):"):service.index("#: address ->")]
        claim = service[service.index("def claim(uid, ip):"):service.index("# ------------------------------------------------- proving")]
        text = read("inject/java/cat/narezany/tokkiyou/Text.java")
        messages = text[text.index("static final String GRADIENT_ONLY"):text.index("// ------------------------------------------------ proving")]
        self.assertNotIn("supporter(db", gradient)
        self.assertIn("if proved:", claim)
        self.assertIn("TOKKIYOU_BADGE", claim)
        self.assertIn('"tokkiyou-" + str(secrets.randbelow(900000) + 100000)', service)
        self.assertNotIn("supporter", messages.lower())
        self.assertNotIn("поддержавш", messages.lower())
        self.assertIn("gradientWait", messages)

    def test_updater_handles_localized_feature_arrays_and_mandatory_updates(self):
        updater = read("inject/java/cat/narezany/tokkiyou/Updater.java")
        self.assertIn('root.optBoolean("force_update", false)', updater)
        self.assertIn('"features_uk"', updater)
        self.assertIn('"features_ru"', updater)
        self.assertIn("Popup.askUpdate", updater)
        self.assertIn("forceUpdate ? null : Text.UPDATE_LATER", updater)
        self.assertIn("forceUpdate ? null : Text.UPDATE_NEVER", updater)

    def test_video_options_default_on_and_are_localized(self):
        tokkiyou = read("inject/java/cat/narezany/tokkiyou/TokkiYou.java")
        settings = read("inject/java/cat/narezany/tokkiyou/SettingsActivity.java")
        text = read("inject/java/cat/narezany/tokkiyou/Text.java")
        self.assertIn('KEY_ZEN_MODE = "tokkiyou_zen_mode"', tokkiyou)
        self.assertIn('KEY_CLEAN_LINKS = "tokkiyou_clean_links"', tokkiyou)
        self.assertIn('KEY_COPY_METADATA = "tokkiyou_copy_metadata"', tokkiyou)
        self.assertIn("p.getBoolean(key, true)", tokkiyou)
        for key in ("KEY_ZEN_MODE", "KEY_CLEAN_LINKS", "KEY_COPY_METADATA"):
            self.assertIn(key, settings)
        for language in ("pick(", "Удалять параметры отслеживания", "Видаляти параметри відстеження", "Remove tracking parameters"):
            self.assertIn(language, text)

    def test_video_actions_cover_the_three_enabled_features(self):
        actions = read("inject/java/cat/narezany/tokkiyou/VideoActions.java")
        self.assertIn("toggleZen(anchor)", actions)
        self.assertIn('"_t".equalsIgnoreCase(key)', actions)
        self.assertIn('"_r".equalsIgnoreCase(key)', actions)
        self.assertIn("copyMetadata(c,post)", actions)
        self.assertIn("disableZen()", actions)
