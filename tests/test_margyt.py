"""The build's own tests.

    python3 -m unittest discover tests

No toolchain and no network: everything runs against tests/data/fixture.apk, a
seven-kilobyte apk built by aapt2 from tests/fixture/ and checked in. It has
what the real one has -- a label from a string resource, an adaptive icon whose
layers are vectors, the icon at two densities, a launcher entry that is an
alias -- and nothing else.
"""

import os
import json
import shutil
import struct
import tempfile
import re
import unittest

import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from margyt import (accent as accent_module, artwork, dexpatch, icon as icon_module,
                    manifest as manifest_module, palette, png, vector)
from margyt.apkzip import Apk, STORED
from margyt.build import emoji_asset_entries
from margyt.arsc import Arsc, ArscError
from margyt.axml import Axml, TYPE_REFERENCE, TYPE_STRING

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
FIXTURE = os.path.join(HERE, "data", "fixture.apk")


def fixture() -> Apk:
    return Apk(FIXTURE)


class ReleaseVersionTest(unittest.TestCase):
    def test_install_code_stays_numeric(self):
        apk = fixture()
        manifest = Axml.parse(apk.read("AndroidManifest.xml"))
        current = manifest_module.version_code(manifest)
        manifest_module.set_version_code(manifest, current + 1001)
        written = Axml.parse(manifest.build())
        self.assertEqual(manifest_module.version_code(written), current + 1001)
        apk.close()


class EmojiAssetManifestTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="emoji-assets-")
        os.makedirs(os.path.join(self.root, "emoji"))
        os.makedirs(os.path.join(self.root, "emoji", "more"))
        with open(os.path.join(self.root, "emoji", "more", "pack.otf"), "wb") as out:
            out.write(b"font")
        self.manifest = os.path.join(self.root, "emoji", "packs.json")

    def tearDown(self):
        shutil.rmtree(self.root)

    def write_manifest(self, asset):
        with open(self.manifest, "w", encoding="utf-8") as out:
            json.dump({"version": 1, "packs": [{"id": "test-pack", "name": "Test",
                      "version": "1", "asset": asset}]}, out)

    def test_manifest_maps_nested_font_to_ttcuz_asset(self):
        self.write_manifest("emoji/more/pack.otf")
        entries = emoji_asset_entries(self.root)
        self.assertEqual(entries[0][0], "assets/margyt/emoji/more/pack.otf")
        self.assertEqual(entries[1][0], "assets/margyt/emoji/packs.json")

    def test_manifest_rejects_traversal(self):
        self.write_manifest("../outside.ttf")
        with self.assertRaises(ValueError):
            emoji_asset_entries(self.root)


class AxmlTest(unittest.TestCase):
    def setUp(self):
        self.apk = fixture()
        self.raw = self.apk.read("AndroidManifest.xml")

    def tearDown(self):
        self.apk.close()

    def test_round_trip_is_byte_for_byte(self):
        self.assertEqual(Axml.parse(self.raw).build(), self.raw)

    def test_reading_the_manifest(self):
        axml = Axml.parse(self.raw)
        self.assertEqual(manifest_module.package_name(axml), "cat.narezany.fixture")
        self.assertEqual(manifest_module.application_class(axml), "cat.narezany.fixture.App")
        self.assertEqual(len(manifest_module.icon_ids(axml)), 1)  # icon and roundIcon agree

    def test_min_sdk_comes_from_the_manifest(self):
        self.assertEqual(manifest_module.min_sdk(Axml.parse(self.raw)), 24)

    def test_the_launcher_entry_is_found_through_the_alias(self):
        axml = Axml.parse(self.raw)
        found = manifest_module.launcher_elements(axml)
        self.assertEqual(len(found), 1)
        self.assertEqual(axml.attr_string(found[0], "name"), "cat.narezany.fixture.Splash")

    def test_label_becomes_an_inline_string(self):
        axml = Axml.parse(self.raw)
        manifest_module.set_label(axml, "MargyT")
        again = Axml.parse(axml.build())
        application = manifest_module.application(again)
        attr = again.attr(application, "label")
        self.assertEqual(attr.kind, TYPE_STRING)
        self.assertEqual(again.pool.get(attr.data), "MargyT")

    def test_added_activity_survives_a_rebuild(self):
        """The mod's screen: declared, exported, and off the home screen.

        It is opened from the row in TikTok's own settings, so it has no
        launcher entry -- and therefore no task of its own either, which is
        what lets the back button return to the settings it was opened from.
        """
        axml = Axml.parse(self.raw)
        manifest_module.set_label(axml, "MargyT")
        manifest_module.add_activity(axml, "cat.narezany.margyt.SettingsActivity",
                                     "MargyT settings", 0x0103012C)
        again = Axml.parse(axml.build())

        names = [again.attr_string(node, "name") for node in again.elements("activity")]
        self.assertIn("cat.narezany.margyt.SettingsActivity", names)
        self.assertIn("cat.narezany.fixture.MainActivity", names)

        added = [n for n in again.elements("activity")
                 if again.attr_string(n, "name") == "cat.narezany.margyt.SettingsActivity"][0]
        self.assertEqual(again.attr_string(added, "label"), "MargyT settings")
        self.assertEqual(again.attr(added, "theme").data, 0x0103012C)
        self.assertEqual(again.attr(added, "exported").data, 0xFFFFFFFF)
        self.assertIsNone(again.attr(added, "taskAffinity"))
        self.assertIsNone(again.attr(added, "launchMode"))

        # the app's own launcher entry is untouched, and ours is not one
        launchers = [again.attr_string(n, "name") for n in manifest_module.launcher_elements(again)]
        self.assertNotIn("cat.narezany.margyt.SettingsActivity", launchers)
        self.assertIn("cat.narezany.fixture.Splash", launchers)

    def test_a_launcher_activity_gets_a_task_of_its_own(self):
        """Asked for an entry on the home screen, it comes with what that needs."""
        axml = Axml.parse(self.raw)
        manifest_module.add_activity(axml, "cat.narezany.margyt.SettingsActivity",
                                     "MargyT settings", 0x0103012C, "cat.narezany.margyt",
                                     launcher=True)
        again = Axml.parse(axml.build())

        added = [n for n in again.elements("activity")
                 if again.attr_string(n, "name") == "cat.narezany.margyt.SettingsActivity"][0]
        self.assertEqual(again.attr_string(added, "taskAffinity"), "cat.narezany.margyt")
        self.assertEqual(again.attr(added, "launchMode").data,
                         manifest_module.LAUNCH_SINGLE_TASK)
        launchers = [again.attr_string(n, "name") for n in manifest_module.launcher_elements(again)]
        self.assertIn("cat.narezany.margyt.SettingsActivity", launchers)
        self.assertIn("cat.narezany.fixture.Splash", launchers)

    def test_a_provider_can_be_declared(self):
        axml = Axml.parse(self.raw)
        manifest_module.add_provider(axml, "cat.narezany.margyt.MargyProvider",
                                     "cat.narezany.fixture.margyt")
        again = Axml.parse(axml.build())
        providers = {again.attr_string(n, "name"): n for n in again.elements("provider")}
        self.assertIn("cat.narezany.margyt.MargyProvider", providers)
        added = providers["cat.narezany.margyt.MargyProvider"]
        self.assertEqual(again.attr_string(added, "authorities"), "cat.narezany.fixture.margyt")
        self.assertEqual(again.attr(added, "exported").data, 0)
        self.assertIn("cat.narezany.fixture.P", providers)  # the ones already there stay

    def test_the_screen_the_row_goes_on_is_the_one_the_mod_looks_for(self):
        """The build refuses an apk whose settings screen has been renamed."""
        from margyt.build import TIKTOK_SETTINGS
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsRow.java")
        with open(source, encoding="utf-8") as handle:
            self.assertIn('"%s"' % TIKTOK_SETTINGS, handle.read())
        self.assertFalse(manifest_module.has_activity(Axml.parse(self.raw), TIKTOK_SETTINGS))
        self.assertTrue(manifest_module.has_activity(Axml.parse(self.raw),
                                                     "cat.narezany.fixture.MainActivity"))
        # an alias counts too: the launcher entry of the real apk is one
        self.assertTrue(manifest_module.has_activity(Axml.parse(self.raw),
                                                     "cat.narezany.fixture.Splash"))

    def test_only_the_authorities_the_package_does_not_cover_move(self):
        axml = Axml.parse(self.raw)
        package = manifest_module.package_name(axml)
        shared = manifest_module.shared_authorities(axml, package)
        self.assertEqual(shared, ["com.example.shared.provider1233"])

        renames = {old: old + ".margyt" for old in shared}
        manifest_module.rename_authorities(axml, renames)
        again = Axml.parse(axml.build())

        authorities = [again.attr_string(n, "authorities") for n in again.elements("provider")]
        self.assertIn("com.example.shared.provider1233.margyt", authorities)
        self.assertIn("cat.narezany.fixture.p", authorities)  # named after the package, untouched
        self.assertEqual(manifest_module.shared_authorities(again, package),
                         ["com.example.shared.provider1233.margyt"])

    def test_inserting_a_string_moves_every_index_that_follows(self):
        """The bug this test exists for: a pool insert renumbers the pool.

        Adding an attribute the file has never used puts its name in the middle
        of the pool, where the resource map ends. Every index above it moves --
        including the ones in elements that have been made but not inserted yet,
        which is how an <activity> once came out as an <action>.
        """
        axml = Axml.parse(self.raw)
        before = [axml.pool.get(node.name) for node in axml.nodes if node.kind == 0x0102]
        activity = axml.make_element("activity")
        axml.set_attr(activity, "configChanges", 0x10, 0xFFF)  # never used by the fixture
        after = [axml.pool.get(node.name) for node in axml.nodes if node.kind == 0x0102]
        self.assertEqual(before, after)
        self.assertEqual(axml.pool.get(activity.name), "activity")

    def test_an_adaptive_icon_reads_back(self):
        axml = Axml.parse(self.apk.read("res/mipmap-anydpi-v26/ic_app.xml"))
        layers = {}
        for layer in ("background", "foreground"):
            node = axml.elements(layer)[0]
            attr = axml.attr(node, "drawable")
            self.assertEqual(attr.kind, TYPE_REFERENCE)
            layers[layer] = attr.data
        self.assertNotEqual(layers["background"], layers["foreground"])


class ArscTest(unittest.TestCase):
    def setUp(self):
        self.apk = fixture()
        self.arsc = Arsc(self.apk.read("resources.arsc"))
        self.manifest = Axml.parse(self.apk.read("AndroidManifest.xml"))

    def tearDown(self):
        self.apk.close()

    def test_the_icon_resolves_to_files_in_the_apk(self):
        res_id = manifest_module.icon_ids(self.manifest)[0]
        paths = [self.arsc.file_path(v) for v in self.arsc.values(res_id)]
        self.assertIn("res/mipmap-mdpi-v4/ic_app.png", paths)
        self.assertIn("res/mipmap-hdpi-v4/ic_app.png", paths)
        self.assertIn("res/mipmap-anydpi-v26/ic_app.xml", paths)
        for path in paths:
            self.assertTrue(self.apk.has(path), path)

    def test_densities_come_out_of_the_config(self):
        res_id = manifest_module.icon_ids(self.manifest)[0]
        densities = sorted(v.density for v in self.arsc.values(res_id))
        self.assertEqual(densities, [160, 240, 0xFFFE])

    def test_a_same_length_string_can_be_swapped_in_place(self):
        res_id = manifest_module.icon_ids(self.manifest)[0]
        value = [v for v in self.arsc.values(res_id) if v.density == 160][0]
        size = len(self.arsc.data)
        self.arsc.replace_string(value.data, "res/mipmap-mdpi-v4/ic_zzz.png")
        self.assertEqual(len(self.arsc.data), size)
        self.assertEqual(self.arsc.strings.get(value.data), "res/mipmap-mdpi-v4/ic_zzz.png")

    def test_a_different_length_string_is_refused(self):
        res_id = manifest_module.icon_ids(self.manifest)[0]
        value = self.arsc.values(res_id)[0]
        with self.assertRaises(ArscError):
            self.arsc.replace_string(value.data, "res/short.png")

    def test_a_value_can_be_repainted_without_moving_a_byte(self):
        colour = self.find_colour()
        size = len(self.arsc.data)
        self.arsc.set_value(colour, colour.kind, artwork.MINT)
        self.assertEqual(len(self.arsc.data), size)
        again = Arsc(self.arsc.build())
        repainted = [v for v in again.values(self.colour_id) if v.offset == colour.offset][0]
        self.assertEqual(repainted.data, artwork.MINT)

    def find_colour(self):
        for package in self.arsc.packages:
            for type_id in package.types:
                name = package.type_names.get(type_id - 1)
                if name != "color":
                    continue
                for entry in range(4):
                    res_id = (package.id << 24) | (type_id << 16) | entry
                    values = self.arsc.values(res_id)
                    if values:
                        self.colour_id = res_id
                        return values[0]
        self.fail("the fixture has no colour resource")


class ApkZipTest(unittest.TestCase):
    def setUp(self):
        self.room = tempfile.mkdtemp()
        self.copy = os.path.join(self.room, "fixture.apk")
        shutil.copy(FIXTURE, self.copy)

    def tearDown(self):
        shutil.rmtree(self.room, ignore_errors=True)

    def test_a_rewrite_keeps_every_entry(self):
        source = fixture()
        original = {name: source.read(name) for name in source.names()}
        out = os.path.join(self.room, "out.apk")
        source.write(out)
        source.close()

        written = Apk(out)
        self.assertEqual(sorted(written.names()), sorted(original))
        for name, data in original.items():
            self.assertEqual(written.read(name), data, name)
        written.close()

    def test_edits_land_and_stored_entries_stay_aligned(self):
        apk = Apk(self.copy)
        apk.replace("resources.arsc", apk.read("resources.arsc"), STORED)
        apk.add("classes2.dex", b"not really a dex, but it is only bytes here")
        apk.remove("res/drawable/ic_back.xml")
        out = os.path.join(self.room, "out.apk")
        apk.write(out)
        apk.close()

        written = Apk(out)
        self.assertTrue(written.has("classes2.dex"))
        self.assertFalse(written.has("res/drawable/ic_back.xml"))
        self.assertEqual(written.index["resources.arsc"].method, STORED)
        for entry, offset in self.data_offsets(out, written):
            if entry.method == STORED:
                self.assertEqual(offset % 4, 0, entry.name)
        written.close()

    def test_the_old_signature_is_dropped(self):
        apk = Apk(self.copy)
        apk.add("META-INF/CERT.SF", b"x")
        apk.add("META-INF/CERT.RSA", b"x")
        apk.add("META-INF/MANIFEST.MF", b"x")
        apk.add("META-INF/services/keep.me", b"x")
        gone = apk.drop_signature()
        self.assertEqual(len(gone), 3)
        self.assertTrue(apk.has("META-INF/services/keep.me"))
        apk.close()

    @staticmethod
    def data_offsets(path, apk):
        with open(path, "rb") as handle:
            for entry in apk.entries:
                handle.seek(entry.source_offset)
                head = struct.unpack("<IHHHHHIIIHH", handle.read(30))
                yield entry, entry.source_offset + 30 + head[9] + head[10]


class PngTest(unittest.TestCase):
    def setUp(self):
        with open(os.path.join(ROOT, artwork.MASTER_PNG), "rb") as handle:
            self.master = png.decode(handle.read())

    def test_the_master_is_what_it_claims(self):
        self.assertEqual((self.master.width, self.master.height), (512, 512))

    def test_resizing_keeps_the_source_artwork_and_square(self):
        for size in (48, 56, 192):
            small = self.master.resized(size)
            self.assertEqual(small.width, size)
            at = ((size // 8) * size + size // 2) * 4
            self.assertNotEqual(tuple(small.pixels[at:at + 4]),
                                (0xC6, 0xA4, 0xFF, 0xFF))
            self.assertEqual(small.pixels[at + 3], 0xFF)
            self.assertEqual(small.pixels[3], 0xFF)  # square artwork is not cropped

    def test_encode_decode_is_lossless(self):
        small = self.master.resized(32)
        again = png.decode(png.encode(small))
        self.assertEqual(again.width, 32)
        self.assertEqual(bytes(again.pixels), bytes(small.pixels))

    def test_size_without_decoding(self):
        data = png.encode(self.master.resized(64))
        self.assertEqual(png.size_of(data), (64, 64))


class SwitchSourceTest(unittest.TestCase):
    """The settings rebuild must not erase the switch that was just tapped."""

    def test_switch_keeps_its_interpolated_position_when_tapped_again(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "M3Switch.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("ANIMATION_DURATION_MS = 300L", java)
        self.assertIn("interruptingAnimation = true", java)
        self.assertIn("!interruptingAnimation && animation == animator", java)

    def test_settings_wait_for_a_switch_transition_before_rebuilding(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsActivity.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        start = java.index("private void markChanged()")
        end = java.index("// ----------------------------------------------------------- the rows", start)
        changed = java[start:end]
        self.assertIn("showRestartBar();", changed)
        self.assertNotIn("pending = true;\n        rebuild();", changed)
        self.assertIn("column.postDelayed(delayedRebuild, M3Switch.ANIMATION_DURATION_MS)", changed)


class AccentSourceTest(unittest.TestCase):
    """A chosen accent has to reach both ordinary views and the Lottie heart."""

    def test_changing_the_accent_invalidates_the_last_colour_translation(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Accent.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        start = java.index("public static void set(int colour)")
        end = java.index("public static boolean isDefault()", start)
        self.assertIn("forget();", java[start:end])

    def test_the_like_animation_accent_is_recoloured_without_touching_packs(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Accent.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("Pattern.compile(\"(?i)#(?:[0-9a-f]{2})?fe2c55\")", java)
        self.assertIn("return recolourResource(resources, id, resources.openRawResource(id));", java)
        self.assertIn("return recolourLottie(source, like, doubleTap);", java)
        self.assertIn("return recolourLottie(source, like,", java)
        self.assertIn('lower.contains("double_tap")', java)
        self.assertIn("private static InputStream recolourLikePng", java)
        self.assertIn("LikeColors.colour()", java)

    def test_feed_like_assets_are_recoloured_at_the_asset_manager_hook(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Accent.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("public static java.io.InputStream open(android.content.res.AssetManager", java)
        self.assertIn("String name, int accessMode", java)
        self.assertIn("openRawResource(Resources resources, int id,", java)
        self.assertIn("recolourLottie(source, like,", java)
        self.assertIn("lower.endsWith(\".png\")", java)

    def test_feed_lottie_animation_names_are_recoloured_before_loading(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Accent.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("public static void setAnimation(Object animation, String name)", java)
        self.assertIn("setAnimationFromJson", java)
        self.assertIn("recolourLottie(source, like,", java)
        with open(os.path.join(ROOT, "margyt", "dexpatch.py"), encoding="utf-8") as handle:
            patcher = handle.read()
        self.assertIn("tt_feed_like_tap_tux_lottie.json", patcher)
        self.assertIn("tt_feed_like_tap_quick_tux_lottie.json", patcher)

    def test_profile_capture_uses_real_account_model_and_is_throttled(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Account.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("service.getCurUser()", java)
        self.assertIn("Avatars.rememberCurrent(user)", java)
        self.assertIn("now - currentUserCapturedAt >= 120000L", java)

    def test_full_accent_picker_covers_the_marked_tiktok_controls(self):
        root = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt")
        with open(os.path.join(root, "SettingsActivity.java"), encoding="utf-8") as handle:
            settings = handle.read()
        with open(os.path.join(root, "AppearanceColors.java"), encoding="utf-8") as handle:
            colors = handle.read()
        self.assertIn("private static final class AccentColourPicker extends View", settings)
        self.assertIn("Color.colorToHSV(initial, hsv)", settings)
        self.assertIn("Color.HSVToColor(hsv)", settings)
        for term in ('"follow"', '"unread"', '"error"', '"friend"', '"inbox"'):
            self.assertIn(term, colors)
        self.assertIn("Accent.tintTextCursor((TextView) view)", colors)
        self.assertIn("if (interactiveAccentName(name)) return false", colors)
        self.assertIn('"follow_add"', colors)
        self.assertIn('"unread_badge"', colors)

    def test_settings_rebuild_restores_scroll_and_keeps_icon_columns_aligned(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsActivity.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("final int previousScroll = page == null ? 0 : page.getScrollY()", java)
        self.assertIn("addOnPreDrawListener(scrollRestoreListener)", java)
        self.assertIn("page.scrollTo(0, requestedScrollY)", java)
        self.assertIn("row.setGravity(Gravity.CENTER_VERTICAL)", java)
        self.assertIn("new LinearLayout.LayoutParams(dp(32), dp(32))", java)

    def test_theme_role_list_is_collapsed_and_sections_use_grouped_surfaces(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsActivity.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("if (themeColoursOpen)", java)
        self.assertIn("private View themeColoursHead()", java)
        card = java[java.index("private LinearLayout card()"):java.index("private View wrap(")]
        self.assertIn("new Expressive.Group(this, skin.card", card)
        self.assertNotIn("setStroke(", card)
        with open(os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                               "Expressive.java"), encoding="utf-8") as handle:
            self.assertNotIn("setStroke(", handle.read())

    def test_feed_and_comment_likes_repaint_after_accent_change(self):
        root = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt")
        with open(os.path.join(root, "Accent.java"), encoding="utf-8") as handle:
            accent = handle.read()
        with open(os.path.join(root, "LikeColors.java"), encoding="utf-8") as handle:
            likes = handle.read()
        self.assertIn("refreshLikeViews();", accent)
        self.assertIn("likeAnimations", accent)
        self.assertIn("likeResourceSources", accent)
        self.assertIn('if (name.contains("dislike")) return false;', accent)
        self.assertIn("Accent.refreshLikeViews();", likes)
        self.assertIn("view.isSelected() || view.isActivated()", likes)

    def test_burn_in_fade_walks_mixed_video_and_chrome_subtrees(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Dim.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        mixed = java[java.index("private static void fadeChrome("):java.index("private static void fade(")]
        self.assertIn("if (!hasMedia(view, 0, new int[] {10000})", mixed)
        self.assertIn("!mediaSizedContainer(view) && !hasFadedDescendant(view, 0)", mixed)
        self.assertIn("fadeChrome(group.getChildAt(i), alpha, depth + 1)", mixed)

    def test_theme_reset_and_accent_role_share_the_actual_accent_preference(self):
        root = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt")
        with open(os.path.join(root, "AppearanceColors.java"), encoding="utf-8") as handle:
            colors = handle.read()
        with open(os.path.join(root, "Themes.java"), encoding="utf-8") as handle:
            themes = handle.read()
        self.assertIn("if (ACCENT.equals(role)) Accent.set(normalized)", colors)
        reset = themes[themes.index("public static void reset()"):themes.index(
            "public static void preset(")]
        self.assertIn("AppearanceColors.reset()", reset)

    def test_settings_entry_uses_the_default_mod_icon(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsRow.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("Launcher.preview(Launcher.DEFAULT)", java)
        self.assertNotIn('glyph.setText("♪")', java)


class ProfileAnimationSourceTest(unittest.TestCase):
    """The optional nickname gradient moves without rebuilding settings."""


class DimSourceTest(unittest.TestCase):
    """The anti-burn pass may dim controls, never corrupt their own alpha."""

    def test_only_a_player_under_the_screen_centre_is_dimmed(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Dim.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("root.getLocationOnScreen(rootOnScreen);", java)
        self.assertIn("if (where.contains(middleX, middleY)) return one;", java)
        self.assertIn("return null;", java[java.index("private static View surface"):])

    def test_a_recreated_top_bar_is_searched_for_again(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Dim.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("private static final long SEARCH_AGAIN = 800;", java)
        self.assertIn("if (known==null && remembered==null && now - lastLook < SEARCH_AGAIN) return;", java)
        self.assertNotIn("private static volatile boolean searched;", java)

    def test_both_navigation_rails_are_dimmed_but_player_subtrees_are_not(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Dim.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("topBar(root, alpha);", java)
        self.assertIn("bottomBar(root, alpha);", java)
        self.assertIn("!hasPlayer(child, 0)", java)

    def test_pixel_shift_is_removed_including_saved_preferences(self):
        with open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Dim.java"), encoding="utf-8") as source:
            java = source.read()
        self.assertIn("isShiftEnabled() { return false; }", java)
        self.assertNotIn("setTranslationX", java)
        self.assertNotIn("setTranslationY", java)


class BackupSourceTest(unittest.TestCase):
    def test_backup_is_allowlisted_and_never_exports_account_tokens(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Backup.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("private static final String[] SAFE", java)
        self.assertNotIn("Account.KEY_ID", java)
        self.assertNotIn("badge_token", java)
        self.assertIn("MOST_BYTES = 512 * 1024", java)

    def test_import_requires_a_review_before_preferences_are_applied(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsActivity.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("final Backup.Review review = Backup.review(this, source);", java)
        self.assertIn("Backup.apply(this, review)", java)

    def test_reset_uses_explicit_groups_not_a_preferences_clear(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Backup.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        reset = java[java.index("public static void reset"):]
        self.assertIn("if (group == 1)", reset)
        self.assertIn("else if (group == 2)", reset)
        self.assertNotIn("edit.clear()", reset)


class SettingsSearchSourceTest(unittest.TestCase):
    def test_search_filters_to_known_settings_shortcuts(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "SettingsActivity.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("if (searchQuery.length() > 0)", java)
        self.assertIn("searchResults();", java)
        self.assertIn("matchesSearch(title + \" \" + words)", java)
        self.assertIn("EditorInfo.IME_ACTION_SEARCH", java)


class VectorTest(unittest.TestCase):
    def test_a_generated_vector_parses_as_one(self):
        data = vector.build(artwork.VIEWPORT, artwork.COMBINED)
        axml = Axml.parse(data)
        self.assertEqual(axml.build(), data)

        root = axml.elements("vector")[0]
        self.assertEqual(axml.attr(root, "width").data, (108 << 8) | 1)  # 108dp
        self.assertEqual(axml.attr(root, "viewportWidth").data, vector.float_bits(108.0))

        paths = axml.elements("path")
        self.assertEqual(len(paths), len(artwork.COMBINED))
        colours = [axml.attr(p, "fillColor").data for p in paths]
        self.assertEqual(colours, [colour for colour, _data in artwork.COMBINED])
        for element, (_colour, data_string) in zip(paths, artwork.COMBINED):
            self.assertEqual(axml.pool.get(axml.attr(element, "pathData").data), data_string)

    def test_the_attribute_ids_are_the_ones_the_platform_uses(self):
        # read back out of the fixture, which aapt2 compiled from real source
        apk = fixture()
        axml = Axml.parse(apk.read("res/drawable/ic_back.xml"))
        apk.close()
        by_name = dict(zip([axml.pool.get(i) for i in range(len(axml.resource_map))],
                           axml.resource_map))
        for name, expected in zip(vector.ATTR_NAMES, vector.ATTR_IDS):
            if name in by_name:
                self.assertEqual(by_name[name], expected, name)


class IconTest(unittest.TestCase):
    def setUp(self):
        self.room = tempfile.mkdtemp()
        self.apk = fixture()
        self.arsc = Arsc(self.apk.read("resources.arsc"))
        self.manifest = Axml.parse(self.apk.read("AndroidManifest.xml"))
        with open(os.path.join(ROOT, artwork.MASTER_PNG), "rb") as handle:
            self.master = handle.read()

    def tearDown(self):
        self.apk.close()
        shutil.rmtree(self.room, ignore_errors=True)

    def test_every_file_behind_the_icon_is_replaced(self):
        sizes_before = {
            path: png.size_of(self.apk.read(path))
            for path in ("res/mipmap-mdpi-v4/ic_app.png", "res/mipmap-hdpi-v4/ic_app.png")
        }
        icon_module.replace_everywhere(self.apk, self.arsc, self.manifest, self.master)

        for path, size in sizes_before.items():
            self.assertEqual(png.size_of(self.apk.read(path)), size)
            image = png.decode(self.apk.read(path))
            expected = png.decode(self.master).resized(max(size))
            at = ((image.height // 8) * image.width + image.width // 2) * 4
            self.assertEqual(tuple(image.pixels[at:at + 4]),
                             tuple(expected.pixels[at:at + 4]))

        # Adaptive icon layers now use the supplied full image, not a traced
        # foreground mark drawn independently at the adaptive-icon safe size.
        adaptive = Axml.parse(self.apk.read("res/mipmap-anydpi-v26/ic_app.xml"))
        self.assertEqual(len(adaptive.elements("adaptive-icon")), 1)
        reparsed_arsc = Arsc(self.arsc.build())
        for layer_name, expected_path in (("background", "res/drawable/ic_back.png"),
                                          ("foreground", "res/drawable/ic_front.png")):
            node = adaptive.elements(layer_name)[0]
            resource = adaptive.attr(node, "drawable").data
            paths = [reparsed_arsc.file_path(value)
                     for value in reparsed_arsc.values(resource)]
            self.assertIn(expected_path, paths)
            image = png.decode(self.apk.read(expected_path))
            self.assertEqual((image.width, image.height), (512, 512))
            if layer_name == "background":
                self.assertEqual(bytes(image.pixels),
                                 bytes(png.decode(self.master).resized(512).pixels))
            else:
                self.assertFalse(any(image.pixels[3::4]))

    def test_the_resource_table_is_not_disturbed(self):
        before = bytes(self.arsc.data)
        icon_module.replace_everywhere(self.apk, self.arsc, self.manifest, self.master)
        self.assertNotEqual(bytes(self.arsc.data), before)
        self.assertTrue(self.arsc.dirty)


class AccentTest(unittest.TestCase):
    """The fixture's colour is #FF0050, and its vector is filled with #161823."""

    PINK = 0xFFFF0050
    INK = 0xFF161823
    MINT = 0xFF8DD1B0

    def setUp(self):
        self.room = tempfile.mkdtemp()
        self.copy = os.path.join(self.room, "fixture.apk")
        shutil.copy(FIXTURE, self.copy)
        self.apk = Apk(self.copy)
        self.arsc = Arsc(self.apk.read("resources.arsc"))

    def tearDown(self):
        self.apk.close()
        shutil.rmtree(self.room, ignore_errors=True)

    def test_a_colour_resource_is_repainted_where_it_lies(self):
        size = len(self.arsc.data)
        report = accent_module.bake(self.apk, self.arsc, self.PINK, self.MINT)
        self.assertEqual(len(self.arsc.data), size)
        self.assertIn("resource entries: 1", report)

        again = Arsc(self.arsc.build())
        values = [v for v in self.values_of_every_colour(again)]
        self.assertIn(self.MINT, values)
        self.assertNotIn(self.PINK, values)

    def test_a_vector_fill_is_repainted_too(self):
        accent_module.bake(self.apk, self.arsc, self.INK, self.MINT)
        vector_xml = Axml.parse(self.apk.read("res/drawable/ic_back.xml"))
        fills = [vector_xml.attr(p, "fillColor").data for p in vector_xml.elements("path")]
        self.assertEqual(fills, [self.MINT])

    def test_nothing_happens_when_the_colour_is_already_the_one(self):
        before = bytes(self.arsc.data)
        report = accent_module.bake(self.apk, self.arsc, self.PINK, self.PINK)
        self.assertEqual(bytes(self.arsc.data), before)
        # nothing is written, but the build still has to say what it left alone:
        # those places are exactly the ones the runtime palette cannot reach
        self.assertIn("nothing to bake", report[0])
        self.assertTrue(any("stay as they are" in line for line in report))
        self.assertTrue(any("--accent" in line for line in report))

    def values_of_every_colour(self, arsc):
        for package in arsc.packages:
            for type_id in package.types:
                if package.type_names.get(type_id - 1) != "color":
                    continue
                for entry in range(8):
                    res_id = (package.id << 24) | (type_id << 16) | entry
                    for value in arsc.values(res_id):
                        yield value.data


class DexPatchTest(unittest.TestCase):
    SAMPLE = """\
.method public static a(Landroid/content/Context;)Ljava/lang/String;
    .locals 2
    invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v5, v0}, Landroid/telephony/TelephonyManager;->getSimState(I)I
    invoke-virtual/range {v10 .. v10}, Landroid/telephony/TelephonyManager;->hasIccCard()Z
    invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getDataNetworkType()I
    invoke-virtual {v0}, Lcom/example/Other;->getSimCountryIso()Ljava/lang/String;
    return-object v1
.end method
"""

    def rewrite(self, text):
        for _label, pattern, target in dexpatch.rules():
            text = pattern.sub(target, text)
        return text

    def test_the_calls_that_should_move_move(self):
        out = self.rewrite(self.SAMPLE)
        self.assertIn(
            "invoke-static {v0}, Lcat/narezany/margyt/Region;->"
            "getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;", out)
        self.assertIn(
            "invoke-static {v5, v0}, Lcat/narezany/margyt/Region;->"
            "getSimState(Landroid/telephony/TelephonyManager;I)I", out)
        self.assertIn(
            "invoke-static/range {v10 .. v10}, Lcat/narezany/margyt/Region;->"
            "hasIccCard(Landroid/telephony/TelephonyManager;)Z", out)

    def test_the_calls_that_should_not_move_stay(self):
        out = self.rewrite(self.SAMPLE)
        self.assertIn(
            "invoke-virtual {v0}, Landroid/telephony/TelephonyManager;->getDataNetworkType()I", out)
        self.assertIn(
            "invoke-virtual {v0}, Lcom/example/Other;->getSimCountryIso()Ljava/lang/String;", out)

    def test_every_target_has_a_method_to_land_in(self):
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt", "Region.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        for name, _original, _replacement in dexpatch.TARGETS:
            self.assertIn(name + "(TelephonyManager tm", java, name)

    def test_a_renamed_authority_moves_in_the_bytecode_too(self):
        import tempfile as tf
        room = tf.mkdtemp()
        try:
            with open(os.path.join(room, "a.smali"), "w", encoding="utf-8") as handle:
                handle.write(
                    '    const-string v0, "com.example.shared.provider1233"\n'
                    '    const-string v1, "com.example.shared.provider1233.suffix"\n'
                    '    const-string v2, "untouched"\n'
                )
            counts = dexpatch.rewrite_literals(
                room, {"com.example.shared.provider1233": "com.example.shared.provider1233.margyt"})
            self.assertEqual(counts, {"com.example.shared.provider1233": 1})
            with open(os.path.join(room, "a.smali"), encoding="utf-8") as handle:
                out = handle.read()
            self.assertIn('"com.example.shared.provider1233.margyt"', out)
            # a longer string that merely starts the same is not a match
            self.assertIn('"com.example.shared.provider1233.suffix"', out)
            self.assertIn('"untouched"', out)
        finally:
            shutil.rmtree(room, ignore_errors=True)

    def test_feed_lottie_call_is_rewritten_without_telephony_reference(self):
        import tempfile as tf
        room = tf.mkdtemp()
        try:
            path = os.path.join(room, "FeedLike.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(
                    ".class public LX/0dQv;\n"
                    ".method public onTouch()V\n"
                    "    .locals 1\n"
                    "    invoke-virtual {v0}, LX/1HkT;->setAnimation(Ljava/lang/String;)V\n"
                    "    return-void\n"
                    ".end method\n")
            counts = dexpatch.rewrite_accent(room)
            self.assertEqual(counts.get("feed Lottie setAnimation"), 1)
            with open(path, encoding="utf-8") as handle:
                out = handle.read()
            self.assertIn(
                "invoke-static {v0}, Lcat/narezany/margyt/Accent;"
                "->setAnimation(Ljava/lang/Object;Ljava/lang/String;)V", out)
        finally:
            shutil.rmtree(room, ignore_errors=True)


class FeedSpeedLockSourceTest(unittest.TestCase):
    def test_the_mod_no_longer_locks_the_speed_itself(self):
        path = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                            "SpeedGesture.java")
        with open(path, encoding="utf-8") as handle:
            source = handle.read()
        # it still learns when TikTok starts speeding, for the rate people set
        self.assertIn('call(ability, "R62")', source)
        self.assertIn("PlayerTools.applyHoldRate()", source)
        # and takes no touch away, stops nothing, and draws nothing
        self.assertNotIn("return true", source)
        self.assertNotIn('"Hj"', source)
        self.assertNotIn("requestDisallowInterceptTouchEvent", source)
        self.assertNotIn("addView", source)

    def test_tiktoks_flag_reader_asks_the_mod_first(self):
        reader = (".class public final LX/09tK;\n.super Ljava/lang/Object;\n\n"
                  ".method public final LJIIJJI(IILjava/lang/String;Z)I\n"
                  "    .registers 13\n\n    .prologue\n    .line 1\n    move-object v6, p3\n\n"
                  "    const/4 v0, 0x0\n\n    return v0\n.end method\n\n"
                  ".method public final LJIILIIL(IJLjava/lang/String;Z)J\n"
                  "    .registers 16\n\n    const-wide/16 v0, 0x0\n\n    return-wide v0\n.end method\n\n"
                  ".method public final LJIJJ(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;\n"
                  "    .registers 4\n\n    return-object p2\n.end method\n")
        with tempfile.TemporaryDirectory() as room:
            path = os.path.join(room, "09tK.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(reader)
            with open(os.path.join(room, "Other.smali"), "w", encoding="utf-8") as handle:
                handle.write(reader.replace("LX/09tK;", "LX/Other;"))
            self.assertEqual(dexpatch.rewrite_flag_reader(room), {dexpatch.FLAG_LABEL: 2})
            with open(path, encoding="utf-8") as handle:
                patched = handle.read()
            # the key is the fourth register of one and, past a long, the fifth of the other
            self.assertIn("    .prologue\n    invoke-static/range {p3 .. p3}, Lcat/narezany/margyt/Flags;"
                          "->forcedInt(Ljava/lang/String;)Ljava/lang/Integer;", patched)
            self.assertIn("invoke-static/range {p4 .. p4}, Lcat/narezany/margyt/Flags;"
                          "->forcedLong(Ljava/lang/String;)Ljava/lang/Long;", patched)
            self.assertIn("    move-result-wide v0\n\n    return-wide v0\n\n    :margyt_flag\n", patched)
            # an object is not something the mod can answer with
            self.assertEqual(patched.count("\n    :margyt_flag\n"), 2)
            with open(os.path.join(room, "Other.smali"), encoding="utf-8") as handle:
                self.assertNotIn("margyt", handle.read())
            # a second pass finds it done and says so, rather than doing it again
            self.assertEqual(dexpatch.rewrite_flag_reader(room), {dexpatch.FLAG_LABEL: 2})
            with open(path, encoding="utf-8") as handle:
                self.assertEqual(handle.read(), patched)
            # a dex that only names the reader is not searched for it
            self.assertEqual(dexpatch.rewrite_flag_reader(room, b"LX/09tK; but not here"), {})

    def test_a_dex_holding_a_renamed_authority_is_taken_apart(self):
        self.assertFalse(dexpatch.interesting(b"nothing", {"com.example.p": "x"}))
        self.assertTrue(dexpatch.interesting(b"...com.example.p...", {"com.example.p": "x"}))

    def test_a_forced_method_keeps_its_modifiers_and_loses_its_body(self):
        import tempfile as tf
        room = tf.mkdtemp()
        try:
            class_name, signature = dexpatch.FORCED_FALSE[0]
            path = os.path.join(room, *class_name.split("/")) + ".smali"
            os.makedirs(os.path.dirname(path))
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(
                    ".class public L%s;\n" % class_name
                    + ".method public final %s\n" % signature
                    + "    .registers 4\n\n"
                    + "    invoke-static {}, Lsomething/Expensive;->check()Z\n\n"
                    + "    move-result v0\n\n    return v0\n.end method\n"
                    + ".method public final other()V\n    return-void\n.end method\n"
                )
            counts = dexpatch.force_false(room)
            self.assertEqual(sum(counts.values()), 1)
            with open(path, encoding="utf-8") as handle:
                out = handle.read()
            self.assertIn(".method public final %s\n    .registers 1" % signature, out)
            self.assertNotIn("Expensive", out)
            self.assertIn("other()V", out)  # nothing else touched
        finally:
            shutil.rmtree(room, ignore_errors=True)

    def test_a_forced_method_that_moved_stops_the_build(self):
        import tempfile as tf
        room = tf.mkdtemp()
        try:
            class_name, _signature = dexpatch.FORCED_FALSE[0]
            path = os.path.join(room, *class_name.split("/")) + ".smali"
            os.makedirs(os.path.dirname(path))
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(".class public L%s;\n" % class_name)
            with self.assertRaises(RuntimeError):
                dexpatch.force_false(room)
        finally:
            shutil.rmtree(room, ignore_errors=True)

    def test_the_dex_holding_a_forced_class_is_taken_apart(self):
        class_name = dexpatch.FORCED_FALSE[0][0]
        self.assertTrue(dexpatch.interesting(("L%s;" % class_name).encode()))
        self.assertFalse(dexpatch.interesting(b"some other app entirely"))

    def test_the_accent_constant_becomes_a_call(self):
        text = ("    const v1, -0x1d3ab\n\n"
                "    invoke-virtual {v2, v1}, Landroid/graphics/Paint;->setColor(I)V\n\n"
                "    const v4, -0x1d3ac\n")
        for _label, pattern, target in dexpatch.accent_rules():
            text = pattern.sub(target, text)
        # accent(), not colour(): the constants go through the plugins and the
        # mod's own screen does not
        self.assertIn("invoke-static {}, Lcat/narezany/margyt/Accent;->accent()I", text)
        self.assertIn("move-result v1", text)
        self.assertIn("const v4, -0x1d3ac", text)  # a colour that is not the accent

    def test_the_save_button_is_pointed_at_the_clean_address(self):
        """Both names are TikTok's own, and both return the same type."""
        text = ("    invoke-virtual {v3}, Lcom/ss/android/ugc/aweme/feed/model/Video;"
                "->getDownloadAddr()Lcom/ss/android/ugc/aweme/base/model/UrlModel;\n"
                "    invoke-virtual {v3}, Lcom/example/Other;"
                "->getDownloadAddr()Lcom/ss/android/ugc/aweme/base/model/UrlModel;\n")
        for _label, pattern, target in dexpatch.model_rules():
            text = pattern.sub(target, text)
        self.assertIn("invoke-static {v3}, Lcat/narezany/margyt/Download;->getDownloadAddr("
                      "Lcom/ss/android/ugc/aweme/feed/model/Video;)"
                      "Lcom/ss/android/ugc/aweme/base/model/UrlModel;", text)
        # somebody else's method of the same name is not ours to move
        self.assertIn("Lcom/example/Other;->getDownloadAddr()", text)

    def test_a_dex_without_the_model_is_left_alone(self):
        self.assertFalse(dexpatch.touches_a_model(b"nothing here"))
        self.assertFalse(dexpatch.touches_a_model(
            b"Lcom/ss/android/ugc/aweme/feed/model/Video;\x00getPlayAddr"))
        self.assertTrue(dexpatch.touches_a_model(
            b"Lcom/ss/android/ugc/aweme/feed/model/Video;\x00getDownloadAddr"))
        self.assertTrue(dexpatch.touches_a_model(
            b"Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;\x00getItems"))

    def test_the_feed_page_comes_through_the_mod(self):
        """The ads are dropped where the page is read, not hidden per screen."""
        text = ("    invoke-virtual {v3}, Lcom/ss/android/ugc/aweme/feed/model/"
                "FeedItemList;->getItems()Ljava/util/List;\n")
        for _label, pattern, target in dexpatch.model_rules():
            text = pattern.sub(target, text)
        self.assertIn("Lcat/narezany/margyt/Feed;->getItems("
                      "Lcom/ss/android/ugc/aweme/feed/model/FeedItemList;)"
                      "Ljava/util/List;", text)

    def test_a_field_read_becomes_a_call_and_a_move(self):
        """allowDownload is a field, so one instruction has to become two."""
        text = ("    iget-object v2, v5, Lcom/ss/android/ugc/aweme/feed/model/"
                "VideoControl;->allowDownload:Ljava/lang/Boolean;\n")
        for _label, pattern, target in dexpatch.model_rules():
            text = pattern.sub(target, text)
        self.assertIn("invoke-static {v5}, Lcat/narezany/margyt/Download;->allowDownload("
                      "Lcom/ss/android/ugc/aweme/feed/model/VideoControl;)"
                      "Ljava/lang/Boolean;", text)
        self.assertIn("move-result-object v2", text)
        # the receiver is read into the call, the result lands in the original
        self.assertLess(text.index("invoke-static"), text.index("move-result-object"))

    def test_a_colour_being_applied_is_redirected_too(self):
        """Reading a colour is half of it; the other half is using one."""
        text = ("    invoke-virtual {v2, v1}, Landroid/graphics/Paint;->setColor(I)V\n"
                "    invoke-virtual {v3, v1}, Landroid/widget/TextView;->setTextColor(I)V\n"
                "    invoke-static {v1}, Landroid/content/res/ColorStateList;"
                "->valueOf(I)Landroid/content/res/ColorStateList;\n"
                "    invoke-virtual {v4, v1}, Lcom/example/Own;->setColor(I)V\n")
        for _label, pattern, target in dexpatch.accent_rules():
            text = pattern.sub(target, text)
        self.assertIn("Lcat/narezany/margyt/Accent;->setColor(Landroid/graphics/Paint;I)V", text)
        self.assertIn("Lcat/narezany/margyt/Accent;->setTextColor("
                      "Landroid/widget/TextView;I)V", text)
        # a static keeps its shape exactly: no receiver to move
        self.assertIn("invoke-static {v1}, Lcat/narezany/margyt/Accent;->valueOf(I)"
                      "Landroid/content/res/ColorStateList;", text)
        self.assertIn("Lcom/example/Own;->setColor(I)V", text)  # not ours to touch

    def test_the_stamp_is_only_dropped_in_the_class_that_draws_it(self):
        """Every drawBitmap in the apk is not ours to touch -- one of them is."""
        call = ("    invoke-virtual {v6, v3, v0, v0, v5}, Landroid/graphics/Canvas;"
                "->drawBitmap(Landroid/graphics/Bitmap;FFLandroid/graphics/Paint;)V\n")

        marked = '    const-string v1, "[tiktok_logo]"\n' + call
        other = "    const-string v1, \"something else\"\n" + call
        for anchor, _label, pattern, target in dexpatch.anchored_rules():
            if anchor in marked:
                marked = pattern.sub(target, marked)
            if anchor in other:
                other = pattern.sub(target, other)

        self.assertIn("Lcat/narezany/margyt/Watermark;->drawBitmap("
                      "Landroid/graphics/Canvas;Landroid/graphics/Bitmap;FF"
                      "Landroid/graphics/Paint;)V", marked)
        self.assertIn("invoke-static {v6, v3, v0, v0, v5}", marked)  # same registers
        self.assertNotIn("margyt", other)  # a class without the marker is left alone

    def test_a_dex_without_the_marker_is_left_alone(self):
        self.assertTrue(dexpatch.carries_an_anchor(b"...[tiktok_logo]..."))
        self.assertTrue(dexpatch.carries_an_anchor(
            b"Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;"))
        self.assertTrue(dexpatch.carries_an_anchor(
            b"LY/ATListenerS452S0100000_16;"))
        self.assertFalse(dexpatch.carries_an_anchor(b"nothing of the sort"))

    def test_feed_speed_hook_is_inserted_only_at_the_single_pointer_touch_entry(self):
        source = (".class public final LY/ATListenerS452S0100000_16;\n"
                  + dexpatch.SPEED_TOUCH_METHOD + "\n"
                  "    .registers 13\n"
                  "    if-lt v0, v3, :cond_27\n"
                  "    :cond_27\n"
                  "    invoke-static {}, Lexample/Original;->keepGoing()V\n"
                  ".end method\n")
        with tempfile.TemporaryDirectory() as room:
            path = os.path.join(room, "Touch.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(source)
            self.assertEqual(dexpatch.rewrite_speed_touch(room),
                             {"hold speed, feed touch": 1})
            with open(path, encoding="utf-8") as handle:
                patched = handle.read()
            self.assertIn(dexpatch.SPEED_TOUCH_TARGET, patched)
            self.assertIn(":margyt_speed_touch_continue", patched)
            self.assertEqual(dexpatch.rewrite_speed_touch(room),
                             {"hold speed, feed touch": 1})
            self.assertEqual(patched.count(dexpatch.SPEED_TOUCH_TARGET), 1)
        self.assertIn(dexpatch.SPEED_TOUCH_TARGET, dexpatch.rewrite_targets())

    def test_swipe_down_does_not_rewrite_a_different_listener(self):
        with tempfile.TemporaryDirectory() as room:
            path = os.path.join(room, "Other.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(".class public final LX/Other;\n" + dexpatch.SPEED_TOUCH_METHOD)
            self.assertEqual(dexpatch.rewrite_speed_touch(room), {})

    def test_comment_long_press_is_wrapped_only_in_its_anchored_cell(self):
        call = ("    invoke-virtual {v2, v1}, Landroid/view/View;"
                "->setOnLongClickListener(Landroid/view/View$OnLongClickListener;)V\n")
        marked = (".class Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/"
                  "BaseCommentCell;\n" + call)
        other = call
        for anchor, _label, pattern, target in dexpatch.anchored_rules():
            if anchor in marked:
                marked = pattern.sub(target, marked)
            if anchor in other:
                other = pattern.sub(target, other)
        self.assertIn("Lcat/narezany/margyt/Comments;->setOnLongClickListener("
                      "Landroid/view/View;Landroid/view/View$OnLongClickListener;)V", marked)
        self.assertEqual(other, call)

    def test_a_synthetic_reference_to_the_comment_cell_is_not_an_anchor(self):
        call = ("    invoke-virtual {v2, v1}, Landroid/view/View;"
                "->setOnLongClickListener(Landroid/view/View$OnLongClickListener;)V\n")
        base = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;"
        with tempfile.TemporaryDirectory() as room:
            path = os.path.join(room, "Synthetic.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(".class LX/Synthetic;\nconst-class v0, " + base + "\n" + call)
            dexpatch.rewrite_anchored(room)
            with open(path, encoding="utf-8") as handle:
                self.assertIn(call, handle.read())

    def test_a_colour_asked_of_the_framework_is_redirected(self):
        text = ("    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getColor(I)I\n"
                "    invoke-virtual {v0, v1}, Lcom/example/Own;->getColor(I)I\n")
        for _label, pattern, target in dexpatch.accent_rules():
            text = pattern.sub(target, text)
        self.assertIn("invoke-static {v0, v1}, Lcat/narezany/margyt/Accent;->"
                      "getColor(Landroid/content/res/Resources;I)I", text)
        self.assertIn("Lcom/example/Own;->getColor(I)I", text)  # someone else's method

    def test_the_pink_is_looked_for_as_an_instruction(self):
        import struct
        colour = struct.pack("<I", dexpatch.TIKTOK_PINK)
        self.assertTrue(dexpatch.holds_the_pink(b"\x14\x02" + colour))   # const v2, pink
        self.assertFalse(dexpatch.holds_the_pink(b"some string " + colour))

    def test_the_dex_format_is_read_off_the_header(self):
        self.assertEqual(dexpatch.dex_format(b"dex\n035\x00rest"), "035")
        self.assertEqual(dexpatch.dex_format(b"dex\n039\x00rest"), "039")

    def test_the_new_dex_continues_the_run(self):
        self.assertEqual(dexpatch.next_dex_name(["classes.dex", "AndroidManifest.xml"]),
                         "classes2.dex")
        self.assertEqual(
            dexpatch.next_dex_name(["classes.dex", "classes2.dex", "classes3.dex"]),
            "classes4.dex")

    def test_a_dex_without_telephony_is_left_alone(self):
        self.assertFalse(dexpatch.interesting(b"nothing to see here"))
        self.assertFalse(dexpatch.interesting(b"Landroid/telephony/TelephonyManager;getDataState"))
        self.assertTrue(
            dexpatch.interesting(b"Landroid/telephony/TelephonyManager;\x00getSimCountryIso"))


class PaletteTest(unittest.TestCase):
    """The zone the accent takes over, and the step it moves it by."""

    PINK = 0xFFFE2C55
    MINT = 0xFF8DD1B0
    CYAN = 0xFF25F4EE  # TikTok's other brand colour, and it has to stay put

    def test_the_reference_lands_exactly_on_the_accent(self):
        self.assertEqual(palette.map_colour(self.PINK, self.PINK, self.MINT), self.MINT)

    def test_alpha_is_kept_and_the_rest_follows(self):
        """Half-transparent pink is the same pink: the first build missed all of it."""
        for alpha in (0x00, 0x1A, 0x80, 0xD9):
            faded = (alpha << 24) | (self.PINK & 0xFFFFFF)
            moved = palette.map_colour(faded, self.PINK, self.MINT)
            self.assertEqual(moved >> 24 & 0xFF, alpha)
            self.assertEqual(moved & 0xFFFFFF, self.MINT & 0xFFFFFF)

    def test_the_neighbours_of_the_family_come_along(self):
        # the shades the apk actually holds, counted off 46.9.42
        for neighbour in (0xFFFF1764, 0xFFED3495, 0xFFF43F5E, 0xFFFF3B5C, 0xFFFB1E70):
            self.assertTrue(palette.captures(neighbour, self.PINK))
            self.assertNotEqual(palette.map_colour(neighbour, self.PINK, self.MINT),
                                neighbour)

    def test_hot_and_pastel_pink_are_mapped_without_losing_alpha(self):
        for colour in (0xFFFF00FF,0xFFF343C9,0xFFFFE5F4,0x80FFE5F4,0xFFF8BBDD):
            self.assertTrue(palette.captures(colour,self.PINK))
            changed=palette.map_colour(colour,self.PINK,self.MINT)
            self.assertNotEqual(changed,colour)
            self.assertEqual(changed>>24,colour>>24)

    def test_what_is_not_the_family_is_not_touched(self):
        for other in (self.CYAN, 0xFF000000, 0xFFFFFFFF, 0xFF808080,
                      0xFF4C8DFF, 0xFF35C759, 0xFF1C2C24):
            self.assertFalse(palette.captures(other, self.PINK))
            self.assertEqual(palette.map_colour(other, self.PINK, self.MINT), other)

    def test_a_lighter_member_stays_lighter(self):
        """Gradients have two ends, and they have to still have two."""
        light = palette.map_colour(0xFFFF96B8, self.PINK, self.MINT)
        dark = palette.map_colour(0xFF801D35, self.PINK, self.MINT)
        self.assertGreater(palette._split(light)[3], palette._split(dark)[3])

    def test_asking_for_the_colour_it_already_is_changes_nothing(self):
        for colour in (self.PINK, 0xFFFF1764, self.CYAN):
            self.assertEqual(palette.map_colour(colour, self.PINK, self.PINK), colour)

    def test_the_java_side_was_given_the_same_numbers(self):
        """Two implementations of one formula, and a seam if they disagree."""
        source = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt",
                              "Palette.java")
        with open(source, encoding="utf-8") as handle:
            java = handle.read()
        self.assertIn("HUE = %gf" % palette.HUE, java)
        self.assertIn("MIN_SATURATION = %gf" % palette.MIN_SATURATION, java)
        self.assertIn("MIN_VALUE = %gf" % palette.MIN_VALUE, java)


class TheIconsAreDeliberatelyReduced(unittest.TestCase):
    """Only the TokkiYou-provided alternate icons remain selectable."""

    EXPECTED = ["yellow", "neon"]

    def test_only_requested_tokkiyou_icons_ship(self):
        from margyt.build import Build
        keys = [key for key, _label in Build.ICONS]
        self.assertEqual(keys, self.EXPECTED)
class DeveloperModeSourceTest(unittest.TestCase):
    def _read(self, name):
        path = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt", name)
        with open(path, encoding="utf-8") as handle:
            return handle.read()

    def test_repeated_heading_taps_show_the_cat_without_enabling_developer_mode(self):
        cats = self._read("Cats.java")
        self.assertIn("NEEDED = 5", cats)
        settings = self._read("SettingsActivity.java")
        self.assertIn("view.setOnClickListener(v -> Cats.tapped(view));", settings)
        self.assertNotIn("developerTap();", settings)
        self.assertNotIn("DevMode.set(true)", settings)

    def test_the_full_report_never_carries_the_account(self):
        report = self._read("Diagnostics.java")
        body = report[report.index("static String fullReport()"):report.index("static String report()")]
        self.assertNotIn("Account.", body)
        self.assertNotIn("getAll()", body)
        self.assertIn("Diary.lines()", body)

    def test_an_ordinary_diary_keeps_its_size(self):
        diary = self._read("Diary.java")
        self.assertIn("KEEP = 40;", diary)
        self.assertIn("DevMode.isOn() ? KEEP_DEV : KEEP", diary)


class ProfileVerificationSourceTest(unittest.TestCase):
    def _read(self, name):
        path = os.path.join(ROOT, "inject", "java", "cat", "narezany", "margyt", name)
        with open(path, encoding="utf-8") as handle:
            return handle.read()

    def test_switches_follow_the_card_so_on_is_never_white_on_white(self):
        switch = self._read("M3Switch.java")
        self.assertIn("luma > 0.55f", switch)
        self.assertIn("trackOn = 0xFF111111", switch)
        self.assertNotIn("TRACK_ON", switch)


if __name__ == "__main__":
    unittest.main()


class PrerecolouredLikeAnimationTest(unittest.TestCase):
    def test_like_animations_are_cached_and_prewarmed_off_the_ui_thread(self):
        java = open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Accent.java"), encoding="utf-8").read()
        self.assertIn("likeAnimationJson(", java)
        self.assertIn("prewarmLikeAnimations", java)
        self.assertIn("margyt-like-prewarm", java)
        margy = open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Margy.java"), encoding="utf-8").read()
        self.assertIn("Accent.prewarmLikeAnimations(null)", margy)


class PrerecolouredAtBuildTest(unittest.TestCase):
    def test_palette_matches_the_app(self):
        from margyt import prerecolour
        accent = open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Accent.java"),
                      encoding="utf-8").read()
        found = re.search(r"PALETTE=\{([^}]*)\}", accent).group(1)
        values = [int(x, 16) for x in re.findall(r"0x([0-9A-Fa-f]{8})", found)]
        self.assertEqual(values, prerecolour.PALETTE)

    def test_heart_is_recoloured_for_every_colour(self):
        import json
        from margyt import png, prerecolour
        pink = png.Image(2, 1, bytearray([0xFE, 0x2C, 0x55, 255, 0, 0, 0, 0]))
        frames = {"img/f0.png": png.encode(pink)}
        lottie = json.dumps({
            "assets": [{"id": "i", "u": "img/", "p": "f0.png"}],
            "layers": [{"ks": {"k": [0.99607843, 0.17254902, 0.33333333, 1]},
                        "c": "#FE2C55"}]})
        names = prerecolour.candidates(["assets/like_heart.json", "assets/margyt/x.json",
                                        "assets/dislike.json", "assets/other.json"])
        self.assertEqual(names, [("assets/like_heart.json", "like_heart.json")])
        read = lambda path: frames.get(path)
        for colour in prerecolour.COLOURS:
            done = prerecolour.make("like_heart.json", lottie.encode(), colour, read)
            body = json.loads(done)
            self.assertTrue(body["assets"][0]["p"].startswith("data:image/png;base64,"))
            self.assertEqual(body["assets"][0]["u"], "")
            if colour != prerecolour.TIKTOK:
                self.assertNotIn("FE2C55", done.decode().upper())
                self.assertNotEqual(body["layers"][0]["ks"]["k"][:3],
                                    [0.99607843, 0.17254902, 0.33333333])
        java = open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Accent.java"),
                    encoding="utf-8").read()
        self.assertIn('"margyt/rc/"', java)
        self.assertTrue(prerecolour.shipped_path("Like_Heart.json", 0xFF6750A4)
                        .startswith("assets/margyt/rc/6750A4/"))


class CustomColourReadyTest(unittest.TestCase):
    def test_custom_colour_hearts_never_build_on_the_ui_thread(self):
        java = open(os.path.join(ROOT, "inject/java/cat/narezany/margyt/Accent.java"),
                    encoding="utf-8").read()
        body = java[java.index("public static void setAnimation"):java.index("// Pre-recoloured like animations.")]
        self.assertIn("likeAnimationReady(", body)
        self.assertIn("scheduleLikeBuild(", body)
        self.assertNotIn("likeAnimationJson(", body)
        self.assertIn("discoverLikeNames(", java)
        self.assertIn("HashMap<Integer, Integer> memo", java)
