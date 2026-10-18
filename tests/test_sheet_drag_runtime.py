"""Execute actual gesture thresholds independently of Android window stubs."""
import pathlib,shutil,subprocess,tempfile,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
HARNESS='''package cat.narezany.margyt;public class DragHarness {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){SheetDrag drag=new SheetDrag();
  drag.start(100,0);check(drag.move(80,30)==0,"upward pull cannot move sheet up");check(!drag.release(400,1),"upward pull cannot close");
  drag.start(100,0);drag.move(130,200);check(!drag.release(400,1),"short slow pull settles");
  drag.start(100,0);drag.move(230,500);check(drag.release(400,1),"long slow pull closes");
  drag.start(100,0);drag.move(140,20);check(drag.release(400,1),"deliberate downward fling closes");
  check(!drag.release(400,1,500),"holding after a short fling clears stale velocity");
  drag.start(100,0);drag.move(110,5);check(!drag.release(400,1),"tiny fast movement does not close");
  drag.start(100,0);drag.move(155,200);check(!drag.release(100,1),"small panels retain minimum drag distance");
  drag.start(100,0);drag.move(170,20);check(drag.release(800,2),"fling threshold scales with display density");
 }
}'''
class SheetDragRuntimeTest(unittest.TestCase):
 @unittest.skipUnless(shutil.which('javac') and shutil.which('java'),'JDK required')
 def test_drag_thresholds(self):
  with tempfile.TemporaryDirectory() as directory:
   p=pathlib.Path(directory);source=p/'SheetDrag.java';source.write_text((ROOT/'inject/java/cat/narezany/margyt/SheetDrag.java').read_text(),encoding='utf-8');h=p/'DragHarness.java';h.write_text(HARNESS,encoding='utf-8')
   run=subprocess.run(['javac','-source','8','-target','8','-d',str(p),str(source),str(h)],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
   run=subprocess.run(['java','-cp',str(p),'cat.narezany.margyt.DragHarness'],capture_output=True,text=True);self.assertEqual(0,run.returncode,run.stderr)
