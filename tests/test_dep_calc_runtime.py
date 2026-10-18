import pathlib, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.margyt;
public class DepHarness {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) {
        check(DepSums.apply(2, '+', 3) == 5 && DepSums.apply(2, '\u2212', 3) == -1 && DepSums.apply(2, '\u00d7', 3) == 6 && DepSums.apply(3, '\u00f7', 2) == 1.5, "the four sums");
        check(Double.isNaN(DepSums.apply(1, '\u00f7', 0)) && DepSums.shown(DepSums.apply(1, '\u00f7', 0)).equals("\u221e"), "nothing divides by nothing");
        check(DepSums.shown(5).equals("5") && DepSums.shown(1.5).equals("1.5") && DepSums.shown(-0.25).equals("-0.25"), "a whole number is written as one");
        check(DepSums.card(0).equals("A\\u2660") && DepSums.card(12 + 13).equals("K\\u2665") && DepSums.card(9).equals("10\\u2660"), "a card is its rank and its suit");
        int reds = 0; for (int n = 0; n <= 36; n++) if (DepSums.red(n)) reds++;
        check(reds == 18 && DepSums.red(1) && !DepSums.red(2) && !DepSums.red(0) && DepSums.red(36) && !DepSums.red(11) && DepSums.red(12), "eighteen numbers are red, and nought is not");
    }
}
"""


class DepCalcTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_the_sums_the_cards_and_the_colours_of_the_wheel(self):
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "DepHarness.java").write_text(HARNESS, encoding="utf-8")
            built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", folder,
                                    str(ROOT / "inject/java/cat/narezany/margyt/DepSums.java"),
                                    str(room / "DepHarness.java")], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(["java", "-cp", folder, "cat.narezany.margyt.DepHarness"],
                                 capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == "__main__":
    unittest.main()
