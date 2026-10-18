import pathlib, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.margyt;
public class LagTallyHarness {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) {
        LagTally tally = new LagTally(new String[] {"waiting", "layout", "draw", "gpu"});
        check(tally.report().equals("no stalls\\n"), "nothing counted, nothing said: " + tally.report());

        tally.stall("Badge.decorate:40", "X.a.b < X.c.d << Badge.decorate:40", 120);
        tally.stall("Badge.decorate:40", "X.e.f < X.g.h << Badge.decorate:40", 310);
        tally.stall("X.q.r < X.s.t", "X.q.r < X.s.t", 900);
        String said = tally.report();
        String[] lines = said.split("\\n");
        check(lines.length == 2, said);
        check(lines[0].equals("900 ms in 1 stalls, longest 900: X.q.r < X.s.t"), "the costliest place is first: " + said);
        check(lines[1].equals("430 ms in 2 stalls, longest 310: Badge.decorate:40  [X.e.f < X.g.h << Badge.decorate:40]"),
                "a place is said with its longest stall: " + said);

        long[] none = new long[4];
        for (int i = 0; i < 97; i++) tally.frame("MainActivity", false, false, none);
        tally.frame("MainActivity", true, false, new long[] {10, 60, 25, 5});
        tally.frame("MainActivity", true, true, new long[] {10, 60, 25, 5});
        tally.frame("MainActivity", true, false, new long[] {0, 60, 26, 4});
        tally.frame("Other", false, false, none);
        said = tally.report();
        check(said.startsWith("MainActivity: 100 frames, 3 slow (3.0%), 1 stuck; slow frames went on layout 62%, draw 26%, waiting 7%, gpu 5%\\n"
                + "Other: 1 frames, 0 slow (0.0%), 0 stuck\\n"), "frames a screen, the busiest first: " + said);

        LagTally full = new LagTally(new String[0]);
        for (int i = 0; i < LagTally.PLACES + 5; i++) full.stall("place " + i, "place " + i, 100);
        full.stall("place 0", "place 0", 100);
        said = full.report();
        check(said.split("\\n").length == LagTally.PLACES + 1, "no more places than there is room for: " + said);
        check(said.contains("500 ms in 5 stalls, longest 100: " + LagTally.REST), "the rest are counted together: " + said);
        check(said.contains("200 ms in 2 stalls, longest 100: place 0"), "and a known place still counts as itself: " + said);
    }
}
"""


class LagTallyTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_stalls_and_frames_are_counted_by_where_they_were(self):
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "LagTallyHarness.java").write_text(HARNESS, encoding="utf-8")
            built = subprocess.run(
                ["javac", "-encoding", "UTF-8", "-d", folder,
                 str(ROOT / "inject/java/cat/narezany/margyt/LagTally.java"),
                 str(room / "LagTallyHarness.java")], capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(["java", "-cp", folder, "cat.narezany.margyt.LagTallyHarness"],
                                 capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == "__main__":
    unittest.main()
