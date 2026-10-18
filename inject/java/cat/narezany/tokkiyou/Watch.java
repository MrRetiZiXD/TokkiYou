package cat.narezany.tokkiyou;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * One callback for everything that needs to know a screen came up: the frame
 * rate, the HDR headroom, the test mark. Three registrations would be three
 * times the work on every screen change, and this app changes screens often.
 */
public final class Watch implements Application.ActivityLifecycleCallbacks {

    private Watch() {}

    /** The screen on top, for anything that has to ask about TikTok's own. */
    private static volatile java.lang.ref.WeakReference<Activity> here =
            new java.lang.ref.WeakReference<Activity>(null);

    public static Activity here() {
        return here.get();
    }

    public static void start(Application application) {
        try {
            application.registerActivityLifecycleCallbacks(new Watch());
        } catch (Throwable error) {
            Diary.note("watch: " + error);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        here = new java.lang.ref.WeakReference<Activity>(activity);
        try {
            Rate.apply(activity);
        } catch (Throwable ignored) {
        }
        try {
            Brightness.apply(activity);
        } catch (Throwable ignored) {
        }
        try {
            Tester.mark(activity);
        } catch (Throwable ignored) {
        }
        Lag.frames(activity);
        // what follows dresses TikTok's own screens, and Lite's are not those
        if (!Version.LITE) {
            TabBadges.attach(activity);
            NavBar.attach(activity);
            Md3.attach(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (here.get() == activity) {
            here = new java.lang.ref.WeakReference<Activity>(null);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { LiteLogin.created(activity); }
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle out) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
