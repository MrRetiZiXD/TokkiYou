import pathlib, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.tokkiyou;
public class NavPlanHarness {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static boolean near(float a, float b) { return Math.abs(a - b) < 0.01f; }
    public static void main(String[] args) {
        float[] shut = NavPlan.edges(500f, new float[] {0, 0, 0, 0, 0}, new float[] {60, 60, 0, 60, 60}, 40f);
        check(shut.length == 6 && near(shut[0], 0f) && near(shut[5], 500f), "the bar is filled end to end");
        for (int i = 0; i < 5; i++) check(near(shut[i + 1] - shut[i], 100f), "with none open the tabs are equal");

        float[] one = NavPlan.edges(500f, new float[] {1, 0, 0, 0, 0}, new float[] {60, 60, 0, 60, 60}, 40f);
        check(near(one[1] - one[0], 148f), "the open tab is wider by its name");
        for (int i = 1; i < 5; i++) check(near(one[i + 1] - one[i], 88f), "the rest share what is left");

        float[] half = NavPlan.edges(500f, new float[] {0.5f, 0.5f, 0, 0, 0}, new float[] {60, 100, 0, 60, 60}, 40f);
        check(near(half[1] - half[0], 84f + 30f) && near(half[2] - half[1], 84f + 50f), "half open is half the name");
        check(near(half[5], 500f), "and the bar is still filled");

        float[] tight = NavPlan.edges(250f, new float[] {1, 0, 0, 0, 0}, new float[] {200, 0, 0, 0, 0}, 40f);
        for (int i = 1; i < 5; i++) check(near(tight[i + 1] - tight[i], 40f), "no tab is squeezed past the least");
        check(near(tight[1] - tight[0], 90f), "it is the name that gives");

        check(NavPlan.edges(100f, new float[0], new float[0], 40f).length == 1, "no tabs, no edges but the first");

        final StringBuilder said = new StringBuilder();
        final java.util.ArrayList<Float> went = new java.util.ArrayList<Float>();
        NavPlan.Pen pen = new NavPlan.Pen() {
            void to(char what, float... where) { said.append(what); for (float one : where) went.add(one); }
            public void move(float x, float y) { to('M', x, y); }
            public void line(float x, float y) { to('L', x, y); }
            public void curve(float a, float b, float c, float d, float x, float y) { to('C', a, b, c, d, x, y); }
            public void close() { said.append('Z'); }
        };
        NavPlan.trace("M10 20v-6h4V3H1L2 4l1,1z", pen);
        check(said.toString().equals("MLLLLLLZ"), "lines: " + said);
        same(went, "lines", 10, 20, 10, 14, 14, 14, 14, 3, 1, 3, 2, 4, 3, 5);

        said.setLength(0);
        went.clear();
        NavPlan.trace("M4 4c-1.1 0-1.99.9-1.99 2s.9 2 2 2S8 6 9 9", pen);
        check(said.toString().equals("MCCC"), "curves: " + said);
        same(went, "numbers run together, and a smooth curve mirrors the bend before", 4, 4,
                2.9f, 4, 2.01f, 4.9f, 2.01f, 6,
                2.01f, 7.1f, 2.91f, 8, 4.01f, 8,
                5.11f, 8, 8, 6, 9, 9);

        said.setLength(0);
        went.clear();
        NavPlan.trace("M1 1h2zm1 1 2 2", pen);
        check(said.toString().equals("MLZML"), "a move after closing: " + said);
        same(went, "a move after closing starts from where the shape began", 1, 1, 3, 1, 2, 2, 4, 4);

        said.setLength(0);
        went.clear();
        NavPlan.trace("M0 0q30 0 30 30t30 30T90 30", pen);
        check(said.toString().equals("MCCC"), "the simpler curve is drawn as the other: " + said);
        same(went, "a bow is two thirds of the way to its bend, and the next mirrors it", 0, 0,
                20, 0, 30, 10, 30, 30,
                30, 50, 40, 60, 60, 60,
                80, 60, 90, 50, 90, 30);

        said.setLength(0);
        went.clear();
        NavPlan.trace("M1 1a5 5 0 0 1 2 2L9 9", pen);
        check(said.toString().equals("M"), "an unknown command stops the pen: " + said);
    }
    static void same(java.util.ArrayList<Float> went, String what, float... expected) {
        check(went.size() == expected.length, what + ": " + went);
        for (int i = 0; i < expected.length; i++) check(near(went.get(i), expected[i]), what + ": " + went);
    }
}
"""


class NavPlanTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_the_bar_is_shared_out_and_icons_are_traced(self):
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "NavPlanHarness.java").write_text(HARNESS, encoding="utf-8")
            built = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", folder,
                 str(ROOT / "inject/java/cat/narezany/tokkiyou/NavPlan.java"),
                 str(room / "NavPlanHarness.java")], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(["java", "-cp", folder, "cat.narezany.tokkiyou.NavPlanHarness"],
                                 capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == "__main__":
    unittest.main()
