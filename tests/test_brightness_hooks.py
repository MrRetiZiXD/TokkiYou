import os, tempfile, unittest

from margyt import dexpatch

CONFIG = (".class public Lcom/ss/android/ugc/aweme/video/simplayer/PlayerConfigImpl;\n"
          ".super Ljava/lang/Object;\n\n"
          ".method public isForceHdrOff()Z\n    .registers 4\n\n    .prologue\n    .line 1\n"
          "    invoke-static {}, LX/0qIc;->LJJJLIIL()Z\n\n    move-result v0\n\n    const/4 v2, 0x0\n\n    return v2\n"
          ".end method\n\n"
          ".method public isOther()Z\n    .registers 2\n\n    const/4 v0, 0x0\n\n    return v0\n.end method\n")


class BrightnessHookTests(unittest.TestCase):
    def test_the_player_asks_the_mod_whether_hdr_is_off(self):
        with tempfile.TemporaryDirectory() as room:
            folder = os.path.join(room, "com/ss/android/ugc/aweme/video/simplayer")
            os.makedirs(folder)
            path = os.path.join(folder, "PlayerConfigImpl.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(CONFIG)
            self.assertEqual(dexpatch.rewrite_hdr_switch(room), {dexpatch.HDR_LABEL: 1})
            with open(path, encoding="utf-8") as handle:
                patched = handle.read()
            # asked first, and TikTok's own answer is still there for when the mod says no
            self.assertIn("    .prologue\n    invoke-static {}, Lcat/narezany/margyt/Brightness;->flat()Z\n\n"
                          "    move-result v0\n\n    if-eqz v0, :margyt_hdr\n\n    const/4 v0, 0x1\n\n"
                          "    return v0\n\n    :margyt_hdr\n\n    .line 1\n"
                          "    invoke-static {}, LX/0qIc;->LJJJLIIL()Z", patched)
            self.assertEqual(patched.count("margyt_hdr"), 2)
            self.assertIn(".method public isOther()Z\n    .registers 2\n\n    const/4 v0, 0x0", patched)
            # a second pass finds it done and says so, rather than doing it again
            self.assertEqual(dexpatch.rewrite_hdr_switch(room), {dexpatch.HDR_LABEL: 1})
            with open(path, encoding="utf-8") as handle:
                self.assertEqual(handle.read(), patched)
            # a dex that only names the class is not searched for it
            self.assertEqual(dexpatch.rewrite_hdr_switch(room, b"PlayerConfigImpl; but not here"), {})
            self.assertIn(dexpatch.HDR_QUESTION, dexpatch.rewrite_targets())

    def test_a_window_is_changed_through_the_mod(self):
        with tempfile.TemporaryDirectory() as room:
            path = os.path.join(room, "Camera.smali")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(".class public LX/Camera;\n.super Ljava/lang/Object;\n\n.method public a()V\n"
                             "    .registers 3\n\n"
                             "    invoke-virtual {v0, v1}, Landroid/view/Window;->setAttributes"
                             "(Landroid/view/WindowManager$LayoutParams;)V\n\n"
                             "    invoke-virtual {v0}, Landroid/view/Window;->getAttributes()"
                             "Landroid/view/WindowManager$LayoutParams;\n\n    return-void\n.end method\n")
            counts = dexpatch.rewrite_models(room)
            self.assertEqual(counts.get(dexpatch.WINDOW_LABEL), 1)
            with open(path, encoding="utf-8") as handle:
                patched = handle.read()
            self.assertIn("invoke-static {v0, v1}, Lcat/narezany/margyt/Brightness;->setAttributes"
                          "(Landroid/view/Window;Landroid/view/WindowManager$LayoutParams;)V", patched)
            # reading a window's attributes is nobody's business but TikTok's
            self.assertIn("invoke-virtual {v0}, Landroid/view/Window;->getAttributes()", patched)
            self.assertIn("Lcat/narezany/margyt/Brightness;->setAttributes"
                          "(Landroid/view/Window;Landroid/view/WindowManager$LayoutParams;)V",
                          dexpatch.rewrite_targets())


if __name__ == "__main__":
    unittest.main()
