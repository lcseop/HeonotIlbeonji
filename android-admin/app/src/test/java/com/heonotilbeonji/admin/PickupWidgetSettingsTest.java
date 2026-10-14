package com.heonotilbeonji.admin;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class PickupWidgetSettingsTest {
    @Test @Config(sdk=35,qualifiers="mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void previewExpandsForNarrowScreensAndLargeFonts() {
        Context context=RuntimeEnvironment.getApplication();
        for(float scale:new float[]{1f,1.8f}) for(int width:new int[]{240,320}) {
            Intent intent=new Intent(context,PickupWidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,12);
            try(var controller=Robolectric.buildActivity(PickupWidgetConfigActivity.class,intent)) {
                var activity=controller.get();
                var config=new android.content.res.Configuration(activity.getResources().getConfiguration()); config.fontScale=scale;
                activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
                controller.setup();
                ViewGroup content=activity.findViewById(android.R.id.content); View root=content.getChildAt(0);
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
                root.layout(0,0,width,800);
                TextView sample=(TextView)PlannerInteractionTest.text(root,"수거 신청 미리보기\n26.10.15 (목)   ·   새 신청");
                assertNotNull(sample);
                View preview=(View)sample.getParent();
                assertTrue("Preview clips text at width "+width+", font scale "+scale,sample.getBottom() <= preview.getHeight()-preview.getPaddingBottom());
                assertEquals(sample.getText().length(),sample.getLayout().getLineEnd(sample.getLineCount()-1));
            }
        }
    }
    @Test public void transparencyIsIndependentAndChangesOnlyTheBackground() {
        Context context=RuntimeEnvironment.getApplication();
        PickupWidgetSettings.save(context,10,75); PickupWidgetSettings.save(context,11,0);
        assertEquals(75,PickupWidgetSettings.transparency(context,10));
        assertEquals(0,PickupWidgetSettings.transparency(context,11));
        View root=PickupWidgetProvider.views(context,10).apply(context,new FrameLayout(context));
        assertEquals(64,((ImageView)root.findViewById(R.id.widget_background_image)).getImageAlpha());
        assertEquals(1f,root.findViewById(R.id.widget_header).getAlpha(),0f);
        PickupWidgetSettings.save(context,10,100);
        assertEquals(0,PickupWidgetSettings.alpha(context,10));
        PickupWidgetSettings.remove(context,10); assertEquals(0,PickupWidgetSettings.transparency(context,10));
    }
    @Test public void configureSavesOnlyAfterPressingSave() {
        Context context=RuntimeEnvironment.getApplication();
        PickupWidgetSettings.save(context,12,25);
        Intent intent=new Intent(context,PickupWidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,12);
        try(var controller=Robolectric.buildActivity(PickupWidgetConfigActivity.class,intent).setup()) {
            Activity activity=controller.get(); View root=activity.getWindow().getDecorView();
            slider(root).setProgress(80);
            PlannerInteractionTest.text(root,"취소").performClick();
            assertEquals(25,PickupWidgetSettings.transparency(context,12));
            assertEquals(Activity.RESULT_CANCELED,shadowOf(activity).getResultCode());
        }
        try(var controller=Robolectric.buildActivity(PickupWidgetConfigActivity.class,intent).setup()) {
            Activity activity=controller.get(); View root=activity.getWindow().getDecorView();
            slider(root).setProgress(60);
            PlannerInteractionTest.text(root,"저장").performClick();
            assertEquals(60,PickupWidgetSettings.transparency(context,12));
            assertEquals(Activity.RESULT_OK,shadowOf(activity).getResultCode());
            assertEquals(12,shadowOf(activity).getResultIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1));
        }
    }
    @Test public void automaticRefreshSchedulesFiveMinuteChecksAndStopsOnLogout() {
        Context context=RuntimeEnvironment.getApplication();
        shadowOf(AppWidgetManager.getInstance(context)).createWidget(PickupWidgetProvider.class,R.layout.pickup_widget);
        context.getSharedPreferences("admin",0).edit().putString("session","widget-test").commit();
        PickupWidgetProvider.requestRefresh(context);
        var alarms=shadowOf(context.getSystemService(AlarmManager.class));
        assertEquals(1,alarms.getScheduledAlarms().size());
        assertEquals(300000L,alarms.peekNextScheduledAlarm().getIntervalMs());
        assertEquals(AlarmManager.ELAPSED_REALTIME,alarms.peekNextScheduledAlarm().getType());
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        assertNotNull(jobs.getPendingJob(PickupWidgetProvider.PERIODIC_JOB_ID));
        assertEquals(900000L,jobs.getPendingJob(PickupWidgetProvider.PERIODIC_JOB_ID).getIntervalMillis());
        new PickupWidgetProvider().onReceive(context,new Intent(context,PickupWidgetProvider.class).setAction(PickupWidgetProvider.AUTO_REFRESH));
        assertNotNull(jobs.getPendingJob(PickupWidgetProvider.JOB_ID));
        assertEquals(1,alarms.getScheduledAlarms().size());
        context.getSharedPreferences("admin",0).edit().remove("session").commit(); PickupWidgetData.clear(context);
        assertEquals(0,alarms.getScheduledAlarms().size());
        assertNull(jobs.getPendingJob(PickupWidgetProvider.PERIODIC_JOB_ID)); assertNull(jobs.getPendingJob(PickupWidgetProvider.JOB_ID));
    }
    private SeekBar slider(View view) {
        if(view instanceof SeekBar) return (SeekBar)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            SeekBar result=slider(((ViewGroup)view).getChildAt(i)); if(result!=null) return result;
        }
        return null;
    }
}
