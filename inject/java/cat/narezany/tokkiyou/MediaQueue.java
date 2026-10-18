package cat.narezany.tokkiyou;
/** Bound simultaneous transfers and queued work instead of spawning a thread per tap. */
final class MediaQueue {
 private static final java.util.concurrent.ThreadPoolExecutor workers=new java.util.concurrent.ThreadPoolExecutor(
  2,2,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(32),r->{
   Thread thread=new Thread(r,"tokkiyou-media");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;
  });
 static boolean add(Runnable work){try{workers.execute(work);return true;}catch(java.util.concurrent.RejectedExecutionException e){return false;}}
}
