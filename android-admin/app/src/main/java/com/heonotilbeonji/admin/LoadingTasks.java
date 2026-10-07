package com.heonotilbeonji.admin;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/** Each lane stays ordered; writes do not wait behind list downloads. */
final class LoadingTasks implements Executor {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final IntConsumer busy;
    LoadingTasks(Activity activity, IntConsumer busy) { this.activity = activity; this.busy = busy; }
    public void execute(Runnable task) {
        activity.runOnUiThread(() -> busy.accept(1));
        worker.execute(() -> { try { task.run(); } finally { activity.runOnUiThread(() -> busy.accept(-1)); } });
    }
    void shutdownNow() { worker.shutdownNow(); }
    static LinearLayout indicator(Activity a, String title) {
        LinearLayout row = new LinearLayout(a); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ProgressBar spinner = new ProgressBar(a); spinner.setContentDescription(title);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(52, 111, 174)));
        row.addView(spinner, new LinearLayout.LayoutParams(AdminPlanner.dp(a, 24), AdminPlanner.dp(a, 24)));
        TextView label = AdminPlanner.text(a, title, 13, android.graphics.Color.rgb(52, 111, 174), false);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2); gap.leftMargin = AdminPlanner.dp(a, 8);
        row.addView(label, gap); row.setVisibility(View.GONE); return row;
    }
}
