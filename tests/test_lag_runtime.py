import pathlib, re, shutil, subprocess, tempfile, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

HARNESS = """package cat.narezany.margyt;
public class LagHarness {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static StackTraceElement at(String type, String method, int line) { return new StackTraceElement(type, method, "SourceFile", line); }
    public static void main(String[] args) {
        check(LagWhere.where(new StackTraceElement[]{at("android.os.MessageQueue", "nativePollOnce", -2), at("android.os.MessageQueue", "next", 346)}).equals(""), "waiting for a message is not a stall");
        check(LagWhere.where(new StackTraceElement[0]).equals("") && LagWhere.where(null).equals(""), "nothing is nothing");
        String said = LagWhere.where(new StackTraceElement[]{at("java.io.FileOutputStream", "write", 12), at("X.0abc", "LIZ", 50331648), at("a.b.C", "d", 7), at("a.b.E", "f", 8), at("a.b.G", "h", 9), at("a.b.I", "j", 10), at("cat.narezany.margyt.Themes", "walk", 733)});
        check(said.startsWith("FileOutputStream.write:12 < X.0abc.LIZ < C.d:7"), said);
        check(said.endsWith(" << Themes.walk:733"), "the mod's own frame is said even when it is far down: " + said);
        String near = LagWhere.where(new StackTraceElement[]{at("cat.narezany.margyt.Dim", "sync", 5), at("android.view.View", "draw", 1)});
        check(near.equals("Dim.sync:5 < View.draw:1"), "and once when it is near the top: " + near);
    }
}
"""


class LagTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "JDK required")
    def test_a_stall_is_said_with_where_it_was(self):
        source = (ROOT / "inject/java/cat/narezany/margyt/Lag.java").read_text(encoding="utf-8")
        pieces = [re.search(r"    static String where\(.*?\n    }\n", source, re.S).group(0),
                  re.search(r"    private static String name\(.*?\n    }\n", source, re.S).group(0)]
        with tempfile.TemporaryDirectory() as folder:
            room = pathlib.Path(folder)
            (room / "LagWhere.java").write_text(
                "package cat.narezany.margyt;\nfinal class LagWhere {\n" + "".join(pieces) + "}\n", encoding="utf-8")
            (room / "LagHarness.java").write_text(HARNESS, encoding="utf-8")
            built = subprocess.run(["javac", "-encoding", "UTF-8", "-d", folder,
                                    str(room / "LagWhere.java"), str(room / "LagHarness.java")],
                                   capture_output=True, text=True)
            self.assertEqual(0, built.returncode, built.stderr)
            ran = subprocess.run(["java", "-cp", folder, "cat.narezany.margyt.LagHarness"],
                                 capture_output=True, text=True)
            self.assertEqual(0, ran.returncode, ran.stderr)


if __name__ == "__main__":
    unittest.main()
