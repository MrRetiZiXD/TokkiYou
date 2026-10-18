import os, tempfile, unittest

from tokkiyou import dexpatch

ROWS = (".class public final LX/0MUp;\n.super Ljava/lang/Object;\n\n"
        ".method public static final LIZ(LX/0MUl;ZZLX/0Lx7;I)V\n    .registers 40\n\n"
        "    invoke-virtual {v6}, LX/0MXQ;->LJJJJZI()LX/0MXC;\n\n    move-result-object v1\n\n"
        "    if-eqz v1, :cond_5a\n\n"
        "    .line 5\n    new-instance v0, Lkotlin/jvm/internal/AwS1S0121000_8;\n\n    const/16 v11, 0xe\n\n"
        "    .line 6\n    move-object v6, v0\n\n    move-object v7, v5\n\n    move v8, v4\n\n    move v9, v3\n\n"
        "    move v10, v2\n\n"
        "    invoke-direct/range {v6 .. v11}, Lkotlin/jvm/internal/AwS1S0121000_8;-><init>(LX/0MUl;ZZII)V\n\n"
        "    iput-object v0, v1, LX/0MXC;->LIZLLL:Lkotlin/jvm/functions/Function2;\n\n    :cond_5a\n    return-void\n\n"
        "    :cond_5b\n    const v0, 0x7f112aa9\n\n    .line 7\n    .line 8\n"
        "    invoke-static {v0, v6}, LX/0L52;->LIZIZ(ILX/0Lx7;)Ljava/lang/String;\n\n    move-result-object v11\n\n"
        "    sget-object v0, LX/0LMt;->n:LX/0LPz;\n\n    goto :cond_5a\n.end method\n\n"
        ".method public static final LIZIZ()V\n    .registers 1\n\n    return-void\n.end method\n")

GROUP = (".class public final LX/1Htf;\n.super Ljava/lang/Object;\n\n"
         ".method public static final LJIIIZ(LX/1Huk;ZZLX/0Lx7;I)V\n    .registers 20\n\n"
         "    move-object v12, v6\n\n" + dexpatch.SETTINGS_FIRST_ROW
         + "\n    invoke-virtual {v6, v3}, LX/0MXQ;->LJJJJL(Z)V\n\n    return-void\n.end method\n")


class SettingsRowHookTests(unittest.TestCase):
    def write(self, room, name, text):
        os.makedirs(os.path.join(room, "X"), exist_ok=True)
        path = os.path.join(room, "X", name + ".smali")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)
        return path

    def test_the_row_is_copied_and_the_first_group_draws_it(self):
        with tempfile.TemporaryDirectory() as room:
            rows = self.write(room, "0MUp", ROWS)
            group = self.write(room, "1Htf", GROUP)
            self.assertEqual(dexpatch.rewrite_settings_row(room), {dexpatch.SETTINGS_LABEL: 2})
            with open(rows, encoding="utf-8") as handle:
                patched = handle.read()
            # TikTok's own function is as it was, and the copy follows it
            self.assertTrue(patched.startswith(ROWS[:ROWS.index(".method public static final LIZIZ()V")].rstrip("\n")))
            copy = patched[patched.index("final tokkiyou(LX/0MUl;ZZLX/0Lx7;I)V"):]
            copy = copy[:copy.index(".end method")]
            self.assertIn("invoke-static {}, Lcat/narezany/tokkiyou/SettingsCell;->title()Ljava/lang/String;\n\n"
                          "    move-result-object v11", copy)
            self.assertIn("sget-object v0, LX/0LMt;->o:LX/0LPz;", copy)
            self.assertIn("invoke-static {v5, v4, v3, v2}, Lcat/narezany/tokkiyou/SettingsCell;->again"
                          "(Ljava/lang/Object;ZZI)Ljava/lang/Object;\n\n    move-result-object v0\n\n"
                          "    check-cast v0, Lkotlin/jvm/functions/Function2;\n\n"
                          "    iput-object v0, v1, LX/0MXC;->LIZLLL:Lkotlin/jvm/functions/Function2;", copy)
            self.assertNotIn("AwS1S0121000_8", copy)
            self.assertNotIn("0x7f112aa9", copy)
            self.assertIn(".method public static final LIZIZ()V", patched)
            with open(group, encoding="utf-8") as handle:
                patched_group = handle.read()
            # asked just before the first row, and what the row was told is put back after it
            self.assertIn("    move v1, v9\n\n    invoke-static {v9, v6}, Lcat/narezany/tokkiyou/SettingsCell;->draw"
                          "(ZLjava/lang/Object;)Z\n\n    move-result v9\n\n" + dexpatch.SETTINGS_FIRST_ROW
                          + "\n    move v9, v1\n", patched_group)
            # a second pass finds it done and says so, rather than doing it again
            self.assertEqual(dexpatch.rewrite_settings_row(room), {dexpatch.SETTINGS_LABEL: 2})
            with open(rows, encoding="utf-8") as handle:
                self.assertEqual(handle.read(), patched)
            with open(group, encoding="utf-8") as handle:
                self.assertEqual(handle.read(), patched_group)
            for target in ("title()Ljava/lang/String;", "again(Ljava/lang/Object;ZZI)Ljava/lang/Object;",
                           "draw(ZLjava/lang/Object;)Z"):
                self.assertIn(dexpatch.SETTINGS_CELL + "->" + target, dexpatch.rewrite_targets())

    def test_case_collisions_use_the_baksmali_suffixed_classes(self):
        with tempfile.TemporaryDirectory() as room:
            self.write(room, "0MUP", ".class public final LX/0MUP;\n")
            rows = self.write(room, "0MUp.1", ROWS)
            self.write(room, "1HTF", ".class public final LX/1HTF;\n")
            group = self.write(room, "1Htf.3", GROUP)

            self.assertEqual(dexpatch.rewrite_settings_row(room), {dexpatch.SETTINGS_LABEL: 2})
            with open(rows, encoding="utf-8") as handle:
                self.assertIn("final tokkiyou(LX/0MUl;ZZLX/0Lx7;I)V", handle.read())
            with open(group, encoding="utf-8") as handle:
                self.assertIn(dexpatch.SETTINGS_CELL + "->draw(ZLjava/lang/Object;)Z", handle.read())

    def test_a_row_that_has_changed_shape_is_left_alone(self):
        with tempfile.TemporaryDirectory() as room:
            rows = self.write(room, "0MUp", ROWS.replace("LX/0LMt;->n:", "LX/0LMt;->q:"))
            self.assertEqual(dexpatch.rewrite_settings_row(room), {})
            with open(rows, encoding="utf-8") as handle:
                self.assertNotIn("tokkiyou", handle.read())
            # and a dex that only names the classes is not searched for them
            self.write(room, "1Htf", GROUP)
            self.assertEqual(dexpatch.rewrite_settings_row(room, b"LX/0MUp; LX/1Htf; but not here"), {})


if __name__ == "__main__":
    unittest.main()
