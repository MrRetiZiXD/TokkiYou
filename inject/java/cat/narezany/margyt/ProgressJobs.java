package cat.narezany.margyt;
/** UI-thread state, independent of activity/window lifetime. */
final class ProgressJobs {
    private static final class Job {final String label;final int percent;Job(String s,int n){label=s;percent=n<0?-1:Math.max(0,Math.min(100,n));}}
    private final java.util.LinkedHashMap<String,Job> jobs=new java.util.LinkedHashMap<String,Job>();
    void update(String key,String label,int percent) {
        jobs.remove(key);
        jobs.put(key,new Job(label,percent));
    }
    void remove(String key){jobs.remove(key);}
    String line(){String result=null;for(Job job:jobs.values())result=job.label;
        return result==null?null:result+(jobs.size()>1?"  · "+jobs.size():"");}
    int percent(){int result=-1;for(Job job:jobs.values())result=job.percent;return result;}
}
