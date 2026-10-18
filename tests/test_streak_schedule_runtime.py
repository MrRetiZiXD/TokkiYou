import pathlib,unittest
import test_settings_safety_runtime as safety
method=safety.method
class ScheduleTest(unittest.TestCase):
 run_java=safety.SafetyRuntimeTest.run_java
 def test_calendar_schedule(self):
  code=method('StreakSchedule.java',' static boolean due(')+method('StreakSchedule.java',' static boolean sameDay(')+method('StreakSchedule.java',' static int parse(')
  self.run_java({'Test.java':'''import java.util.*;public class Test{'''+code+'''static void check(boolean b){if(!b)throw new AssertionError();}static long at(int d,int h,int m){Calendar c=Calendar.getInstance();c.clear();c.set(2026,9,d,h,m,0);return c.getTimeInMillis();}public static void main(String[] a){TimeZone.setDefault(TimeZone.getTimeZone("Asia/Qyzylorda"));check(parse("00:00")==0&&parse("23:59")==1439&&parse("9:05")==545);for(String s:new String[]{"24:00","12:60","1:3","-1:00","abc"})check(parse(s)==-2);check(!due(at(5,19,59),1200)&&due(at(5,20,0),1200)&&due(at(5,23,59),1200));check(!due(at(6,0,0),1200));check(due(at(5,0,0),-1));check(sameDay(at(5,0,0),at(5,23,59))&&!sameDay(at(5,0,1),at(4,23,59))&&!sameDay(at(5,0,1),0));}}'''})
 def test_next_alarm(self):
  code=method('StreakSchedule.java',' static long next(')
  self.run_java({'Test.java':'''import java.util.*;public class Test{'''+code+'''static void check(boolean b){if(!b)throw new AssertionError();}static long at(int d,int h,int m){Calendar c=Calendar.getInstance();c.clear();c.set(2026,9,d,h,m,0);return c.getTimeInMillis();}public static void main(String[] a){TimeZone.setDefault(TimeZone.getTimeZone("Asia/Qyzylorda"));long M=60000L;check(next(at(5,12,0),1200,false,15*M)==at(5,20,0));check(next(at(5,20,0),1200,false,15*M)==at(6,20,0));check(next(at(5,20,3),1200,true,15*M)==at(5,20,3)+5*M);check(next(at(5,23,30),1200,true,15*M)==at(6,20,0));check(next(at(5,21,0),-1,false,15*M)==at(5,21,0)+15*M);}}'''})
