import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from margyt.palette import map_colour
from test_secondary_accent_runtime import COLOR


ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
class PaletteParity(unittest.TestCase):
    def test_java_and_python_for_custom_colours(self):
        reference = 0xFFFE2C55
        sources = [0xFFFE2C55, 0x80F343C9, 0xFFFF365A, 0xFF808080]
        accents = [0xFF19B7D4, 0xFF7A4BC8, 0xFFFFA726, 0xFF14D081]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            files = {
                "android/graphics/Color.java": COLOR,
                "cat/narezany/margyt/Palette.java": (
                    ROOT / "inject/java/cat/narezany/margyt/Palette.java"
                ).read_text(encoding="utf-8"),
                "Test.java": """import cat.narezany.margyt.Palette;
public class Test {public static void main(String[] a){
int[] colours={0xFFFE2C55,0x80F343C9,0xFFFF365A,0xFF808080};
int[] accents={0xFF19B7D4,0xFF7A4BC8,0xFFFFA726,0xFF14D081};
for(int target:accents)for(int source:colours)
System.out.printf("%08X%n",Palette.map(source,0xFFFE2C55,target));}}
""",
            }
            for path, content in files.items():
                target = root / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content, encoding="utf-8")
            subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(root),
                            *[str(root / path) for path in files]], check=True)
            result = subprocess.run(["java", "-cp", str(root), "Test"],
                                    capture_output=True, text=True, check=True)
        actual = [int(line, 16) for line in result.stdout.splitlines()]
        expected = [map_colour(source, reference, target)
                    for target in accents for source in sources]
        for java, python in zip(actual, expected):
            self.assertEqual(java >> 24, python >> 24)
            for shift in (0, 8, 16):
                self.assertLessEqual(abs((java >> shift & 255) - (python >> shift & 255)), 1)
