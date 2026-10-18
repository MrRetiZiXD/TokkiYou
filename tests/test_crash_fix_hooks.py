import os, tempfile, unittest

from tokkiyou import dexpatch

STARTER = (".class public Landroidx/activity/ComponentActivity;\n.super LX/1APY;\n\n"
           ".method public startActivityForResult(Landroid/content/Intent;I)V\n    .registers 3\n"
           "    .annotation runtime LX/0nVe;\n    .end annotation\n\n    .prologue\n"
           "    invoke-static {p0, p1, p2}, Landroidx/activity/ComponentActivity;->a(LX/1APY;Landroid/content/Intent;I)V\n\n"
           "    return-void\n.end method\n\n"
           ".method public startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V\n    .registers 4\n\n"
           "    .prologue\n    return-void\n.end method\n\n"
           ".method public other(Landroid/content/Intent;I)V\n    .registers 3\n\n    return-void\n.end method\n")


class CrashFixHookTests(unittest.TestCase):
    def test_every_start_is_shown_to_the_mod(self):
        with tempfile.TemporaryDirectory() as room:
            folder = os.path.join(room, "androidx", "activity")
            os.makedirs(folder)
            path = os.path.join(folder, "ComponentActivity.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(STARTER)
            self.assertEqual(dexpatch.rewrite_starts(room), {dexpatch.START_LABEL: 2})
            with open(path, encoding="utf-8") as handle:
                patched = handle.read()
            # first thing in both, after what describes the method
            self.assertIn("    .prologue\n    invoke-static {p1}, " + dexpatch.START_ROUTE + "\n\n"
                          "    invoke-static {p0, p1, p2}, Landroidx/activity/ComponentActivity;->a", patched)
            self.assertIn("    .prologue\n    invoke-static {p1}, " + dexpatch.START_ROUTE + "\n\n    return-void", patched)
            self.assertEqual(patched.count(dexpatch.START_ROUTE), 2)
            self.assertIn(".method public other(Landroid/content/Intent;I)V\n    .registers 3\n\n    return-void", patched)
            # a second pass finds it done and says so, rather than doing it again
            self.assertEqual(dexpatch.rewrite_starts(room), {dexpatch.START_LABEL: 2})
            with open(path, encoding="utf-8") as handle:
                self.assertEqual(handle.read(), patched)
            self.assertEqual(dexpatch.rewrite_starts(room, b"ComponentActivity; but not here"), {})
            self.assertIn(dexpatch.START_ROUTE, dexpatch.rewrite_targets())

    def test_the_missing_string_is_asked_for_through_the_mod(self):
        with tempfile.TemporaryDirectory() as room:
            os.makedirs(os.path.join(room, "X"))
            banner = os.path.join(room, "X", "0TFu.smali")
            other = os.path.join(room, "X", "Other.smali")
            call = "    invoke-virtual {v3, v2}, Landroid/content/Context;->getString(I)Ljava/lang/String;\n"
            for path, name in ((banner, "LX/0TFu;"), (other, "LX/Other;")):
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(".class public final %s\n.super Ljava/lang/Object;\n\n.method public a()V\n"
                                 "    .registers 5\n\n%s\n    return-void\n.end method\n" % (name, call))
            dexpatch.rewrite_anchored(room)
            with open(banner, encoding="utf-8") as handle:
                self.assertIn("invoke-static {v3, v2}, Lcat/narezany/tokkiyou/Safely;->getString"
                              "(Landroid/content/Context;I)Ljava/lang/String;", handle.read())
            # only where it crashes: everywhere else a string that is missing should be heard about
            with open(other, encoding="utf-8") as handle:
                self.assertIn(call, handle.read())


if __name__ == "__main__":
    unittest.main()
