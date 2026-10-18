package cat.narezany.tokkiyou;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** The crash-report question first, then five short steps: once on a foreground home screen, always in settings. */
final class Tutorial {
    private static final String DONE = "tutorial_done_v1";
    private static WeakReference<Dialog> open = new WeakReference<Dialog>(null);
    private static WeakReference<Activity> owner = new WeakReference<Activity>(null);
    private static final String[] TITLES = Text.TUTORIAL_TITLES;
    private static final String[] WORDS = Text.TUTORIAL_WORDS;
    private Tutorial() {}

    static void maybeShow(Activity activity) {
        String name = activity.getClass().getName();
        if (!"com.ss.android.ugc.aweme.main.MainActivity".equals(name)
                && !SettingsRow.SETTINGS_ACTIVITY.equals(name)
                && !(activity instanceof SettingsActivity)) return;
        if (finished(activity) && CrashConsent.asked(activity)) return;
        WeakReference<Activity> target = new WeakReference<Activity>(activity);
        activity.getWindow().getDecorView().postDelayed(() -> {
            Activity current=target.get();
            if (current == null || current != Screen.now() || current.isFinishing() || current.isDestroyed()
                    || !current.getWindow().getDecorView().hasWindowFocus()) return;
            if (!CrashConsent.asked(current)) { CrashConsent.show(current,() -> afterConsent(current)); return; }
            if (!finished(current)) show(current);
        },900L);
    }

    private static boolean finished(Activity activity) {
        return activity.getSharedPreferences(TokkiYou.PREFS,Context.MODE_PRIVATE).getBoolean(DONE,false);
    }

    /** The question is answered; the walkthrough follows unless it was seen before. */
    private static void afterConsent(Activity activity) {
        if (finished(activity) || activity.isFinishing() || activity.isDestroyed()) return;
        show(activity);
    }

    static void show(Activity activity) {
        if(!(activity instanceof SettingsActivity)) {
            activity.startActivity(new android.content.Intent(activity,SettingsActivity.class).putExtra("tokkiyou_tutorial",true));
            return;
        }
        Dialog previous=open.get();
        if (previous != null && previous.isShowing()) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Skin skin=Skin.remembered(activity);
        Panel panel=Panel.with(activity,skin,null);
        LinearLayout page=new LinearLayout(activity); page.setOrientation(LinearLayout.VERTICAL);
        int space=Math.round(12*activity.getResources().getDisplayMetrics().density);
        TextView counter=new TextView(activity); counter.setTextColor(Accent.colour()); counter.setTextSize(12);
        TextView title=new TextView(activity); title.setTextColor(skin.text); title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        title.setPadding(0,space,0,space);
        TextView body=new TextView(activity); body.setTextColor(skin.text); body.setTextSize(16);
        body.setLineSpacing(0,1.15f);
        page.addView(counter); page.addView(title); page.addView(body); panel.view(page);
        final int[] step={0};
        TextView next=panel.action(Text.TUTORIAL_NEXT,true);
        TextView back=panel.action(Text.TUTORIAL_BACK,false);
        Runnable render=() -> {
            ((SettingsActivity)activity).tutorialStep(step[0]);
            counter.setText(dots(step[0]));
            title.setText(TITLES[step[0]]); body.setText(WORDS[step[0]]);
            next.setText(step[0]==TITLES.length-1 ? Text.TUTORIAL_DONE : Text.TUTORIAL_NEXT);
            back.setVisibility(step[0]==0 ? View.GONE : View.VISIBLE);
            title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        };
        render.run();
        next.setOnClickListener(v -> {
            if (step[0]==TITLES.length-1) { finish(activity); panel.close(); }
            else { step[0]++; render.run(); Motion.enter(page,1f); }
        });
        back.setOnClickListener(v -> {
            if (step[0]>0) { step[0]--; render.run(); Motion.enter(page,-1f); }
        });
        panel.quiet(Text.TUTORIAL_SKIP,() -> finish(activity));
        Dialog dialog=panel.dialog();
        dialog.setOnCancelListener(d -> finish(activity));
        dialog.setOnDismissListener(d -> {
            if (open.get()==dialog) {open.clear();owner.clear();}
        });
        open=new WeakReference<Dialog>(dialog); owner=new WeakReference<Activity>(activity);
        panel.show();
    }

    /** One mark per step, the current one filled. */
    private static String dots(int step) {
        StringBuilder marks=new StringBuilder();
        for (int i=0;i<TITLES.length;i++) marks.append(i==step ? "●" : "○").append(i==TITLES.length-1 ? "" : "  ");
        return marks.toString();
    }

    private static void finish(Activity activity) {
        activity.getSharedPreferences(TokkiYou.PREFS,Context.MODE_PRIVATE).edit().putBoolean(DONE,true).apply();
    }

    static void paused(Activity activity) {
        CrashConsent.paused(activity);
        if (owner.get()!=activity) return;
        Dialog dialog=open.get(); open.clear(); owner.clear();
        if (dialog!=null) dialog.dismiss();
    }
}
