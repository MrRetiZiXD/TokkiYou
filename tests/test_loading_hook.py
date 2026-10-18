"""The loader's views are found by what they are, and hooked at the top of onDraw."""
import os, re, sys, tempfile, unittest
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
from tokkiyou import dexpatch  # noqa: E402

VIEW = """.class public final LX/0pWh;
.super Landroid/view/View;
.source "SourceFile"

.field public final LLJJIJIIJIL:Landroid/graphics/PorterDuffXfermode;

.method public final onDraw(Landroid/graphics/Canvas;)V
    .registers 20

    .prologue
    .line 16777216
    move-object/from16 v11, p1

    return-void
.end method

.method public final setProgressBarInfo(I)V
    .registers 5

    return-void
.end method
"""


class LoadingHookTest(unittest.TestCase):
    def rewrite(self, text):
        with tempfile.TemporaryDirectory() as root:
            path = os.path.join(root, "0pWh.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(text)
            counts = dexpatch.rewrite_loading(root)
            with open(path, encoding="utf-8") as handle:
                return handle.read(), counts

    def test_the_call_goes_before_anything_the_method_does(self):
        out, counts = self.rewrite(VIEW)
        self.assertEqual(counts, {"native loading indicator": 1})
        hook = out.index("Lcat/narezany/tokkiyou/Loading;->draw(Landroid/view/View;Landroid/graphics/Canvas;)Z")
        self.assertLess(out.index(".prologue"), hook)
        self.assertLess(hook, out.index("move-object/from16 v11, p1"))
        # two registers past fifteen cannot be named one by one
        self.assertIn("invoke-static/range {p0 .. p1}, Lcat/narezany/tokkiyou/Loading;", out)
        self.assertIn("    if-eqz v0, :tokkiyou_loading_native\n\n    return-void\n\n    :tokkiyou_loading_native\n", out)

    def test_a_second_pass_changes_nothing(self):
        once, _ = self.rewrite(VIEW)
        twice, counts = self.rewrite(once)
        self.assertEqual(once, twice)
        self.assertEqual(counts, {"native loading indicator": 1})

    def test_other_views_are_left_alone(self):
        for other in (VIEW.replace(".super Landroid/view/View;", ".super Landroid/widget/ImageView;"),
                      VIEW.replace("Landroid/graphics/PorterDuffXfermode;", "Landroid/graphics/Paint;"),
                      VIEW.replace(".method public final setProgressBarInfo(I)V", ".method public final setOther(I)V"),
                      # calling it is not having it
                      VIEW.replace(".method public final setProgressBarInfo(I)V", ".method public final other(I)V")
                          .replace("    return-void\n.end method\n\n.method public final other",
                                   "    invoke-virtual {p0, v0}, LX/0pWh;->setProgressBarInfo(I)V\n    return-void\n.end method\n\n.method public final other")):
            out, counts = self.rewrite(other)
            self.assertEqual((out, counts), (other, {}))

    def test_a_method_with_no_register_to_spare_is_left_alone(self):
        tight = VIEW.replace("    .registers 20\n\n    .prologue", "    .registers 2\n\n    .prologue")
        out, counts = self.rewrite(tight)
        self.assertEqual((out, counts), (tight, {}))

    def test_a_rule_is_only_run_where_its_text_is(self):
        for _label, pattern, _target in dexpatch.model_rules():
            needle = dexpatch._required(pattern)
            self.assertTrue(needle, pattern.pattern)
            # the needle is in the pattern as plain text, so it is in any match
            self.assertIn(needle, re.sub(r"\\(.)", r"\1", pattern.pattern))


CELL = """.class public Lcom/ss/android/ugc/aweme/base/widget/FixedRatioFrameLayout;
.super Landroid/widget/FrameLayout;

.method public constructor <init>(Landroid/content/Context;)V
    .registers 3

    invoke-direct {p0, p1}, Landroid/widget/FrameLayout;-><init>(Landroid/content/Context;)V

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Landroid/util/AttributeSet;)V
    .registers 6

    if-eqz p2, :cond_4

    return-void

    :cond_4
    return-void
.end method

.method public final onMeasure(II)V
    .registers 4

    return-void
.end method
"""


class CardCornersTest(unittest.TestCase):
    def test_every_constructor_ends_in_the_mod_and_nothing_else_does(self):
        with tempfile.TemporaryDirectory() as root:
            folder = os.path.join(root, "com/ss/android/ugc/aweme/base/widget")
            os.makedirs(folder)
            path = os.path.join(folder, "FixedRatioFrameLayout.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(CELL)
            self.assertEqual(dexpatch.rewrite_cards(root), {"native card corners": 2})
            with open(path, encoding="utf-8") as handle:
                out = handle.read()
            call = "invoke-static/range {p0 .. p0}, Lcat/narezany/tokkiyou/Corners;->cell(Landroid/view/View;)V"
            # one before each way out of each constructor, none in onMeasure
            self.assertEqual(out.count(call), 3)
            self.assertNotIn(call, out[out.index(".method public final onMeasure"):])
            self.assertEqual(dexpatch.rewrite_cards(root), {"native card corners": 2})
            with open(path, encoding="utf-8") as handle:
                self.assertEqual(handle.read(), out)
        self.assertIn("Lcat/narezany/tokkiyou/Corners;->cell(Landroid/view/View;)V", dexpatch.rewrite_targets())

    def test_a_dex_without_the_cell_is_left_alone(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertEqual(dexpatch.rewrite_cards(root), {})
        self.assertTrue(dexpatch.holds_a_card(b"..Lcom/ss/android/ugc/aweme/base/widget/FixedRatioFrameLayout;.."))
        self.assertFalse(dexpatch.holds_a_card(b"nothing"))


if __name__ == "__main__":
    unittest.main()
