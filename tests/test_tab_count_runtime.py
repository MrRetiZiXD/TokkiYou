import pathlib, shutil, subprocess, sys, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.margyt;
public class TabCountHarness {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) {
        check(TabCount.of("13") == 13 && TabCount.of(" 1 ") == 1, "a number is itself");
        check(TabCount.of("99+") == TabCount.PLENTY && TabCount.of("999+") == TabCount.PLENTY, "past what is written out is plenty");
        check(TabCount.of("") == 0 && TabCount.of(null) == 0 && TabCount.of("+") == 0, "nothing is nothing");
        check(TabCount.of("Inbox") == 0 && TabCount.of("1k") == 0 && TabCount.of("0") == 0, "a word is not a number, and none is none");
        check(TabCount.remembered(13, 13) == 13, "the number dismissed stays out of sight");
        check(TabCount.remembered(13, 14) == 0, "one more brings it back");
        check(TabCount.remembered(13, 5) == 5 && TabCount.remembered(5, 6) == 0, "reading some lowers what is remembered");
        check(TabCount.remembered(0, 7) == 0 && TabCount.remembered(13, 0) == 0, "nothing dismissed, nothing hidden");
        check(TabCount.remembered(TabCount.PLENTY, TabCount.PLENTY) == TabCount.PLENTY, "plenty stays plenty");
    }
}
"""


class TabCountTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_a_dismissed_number_comes_back_only_for_something_new(self):
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "TabCountHarness.java").write_text(HARNESS, encoding="utf-8")
            built = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", folder,
                 str(ROOT / "inject/java/cat/narezany/margyt/TabCount.java"),
                 str(room / "TabCountHarness.java")], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(["java", "-cp", folder, "cat.narezany.margyt.TabCountHarness"],
                                 capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == "__main__":
    unittest.main()
