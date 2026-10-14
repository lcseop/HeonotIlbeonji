package com.heonotilbeonji.admin;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowSettings;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},qualifiers="w360dp-h800dp-mdpi")
public class PickupEdgeTest {
    private void prepare() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("admin",0).edit().putString("session","edge-test").putBoolean("edge_enabled",true).commit();
        ShadowSettings.setCanDrawOverlays(true);
        PickupWidgetData.store(context,new JSONArray().put(new JSONObject().put("id","edge-one").put("name","김민수")
            .put("date","2026-10-15").put("status","new").put("pickupMethod","비대면 수거")
            .put("address","경기도 김포시 고촌읍 전호로56번길 30, 1층 (우측)")));
    }
    @Test public void enablingExplainsAndRequestsOverlayPermission() throws Exception {
        ShadowSettings.setCanDrawOverlays(false);
        try(var controller=Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity=controller.get();
            var settings=MainActivity.class.getDeclaredMethod("showEdgeSettings");settings.setAccessible(true);settings.invoke(activity);
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(android.os.Looper.getMainLooper()).idle();
            Intent permission=shadowOf(activity).getNextStartedActivity();
            assertNotNull(permission);assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,permission.getAction());
            assertFalse(PickupEdgeService.available(activity));
        }
    }
    @Test public void sidePanelWorksWithoutHomeWidgetAndStopsItsScheduleWhenDisabled() throws Exception {
        prepare();Context context=RuntimeEnvironment.getApplication();
        assertEquals(0,PickupWidgetProvider.widgetIds(context).length);assertTrue(PickupWidgetProvider.hasConsumers(context));
        PickupWidgetProvider.requestRefresh(context);
        assertNotNull(context.getSystemService(android.app.job.JobScheduler.class).getPendingJob(PickupWidgetProvider.JOB_ID));
        PickupEdgeService.stop(context,true);assertFalse(PickupWidgetProvider.hasConsumers(context));
        assertNull(context.getSystemService(android.app.job.JobScheduler.class).getPendingJob(PickupWidgetProvider.JOB_ID));
    }
    @Test public void handleAndPanelOpenRequestsAndDismissWithoutChangingWidgetRows() throws Exception {
        prepare();var controller=Robolectric.buildService(PickupEdgeService.class).create();
        try {
            PickupEdgeService service=controller.get();set(service,"loading",true);service.onStartCommand(new Intent(),0,1);
            View handle=(View)get(service,"handle");assertNotNull(handle);
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,((WindowManager.LayoutParams)handle.getLayoutParams()).type);
            handle.performClick();View drawer=(View)get(service,"drawer");assertNotNull(drawer);
            assertNotNull(PlannerInteractionTest.text(drawer,"김민수"));
            assertNotNull(PlannerInteractionTest.text(drawer,"경기도 김포시 고촌읍 전호로56번길 30, 1층 (우측)"));
            assertNotNull(PlannerInteractionTest.description(drawer,"비대면 수거"));
            var open=PickupEdgeService.class.getDeclaredMethod("openApp",String.class);open.setAccessible(true);open.invoke(service,"edge-one");
            Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertEquals("edge-one",intent.getStringExtra(PickupWidgetProvider.REQUEST_ID));assertNull(get(service,"drawer"));
            assertEquals(1,PickupWidgetData.read(service).length());
        } finally { controller.destroy(); }
    }
    @Test public void lockedPhoneCannotOpenTheRequestPanel() throws Exception {
        prepare();var controller=Robolectric.buildService(PickupEdgeService.class).create();
        try {
            PickupEdgeService service=controller.get();set(service,"loading",true);service.onStartCommand(new Intent(),0,1);
            shadowOf(service.getSystemService(KeyguardManager.class)).setKeyguardLocked(true);
            call(service,"updateLockVisibility");call(service,"openDrawer");
            assertNull(get(service,"drawer"));assertEquals(View.GONE,((View)get(service,"handle")).getVisibility());
        } finally { controller.destroy(); }
    }
    @Test public void handleCanMoveVerticallyAndOpenByDraggingLeft() throws Exception {
        prepare();var controller=Robolectric.buildService(PickupEdgeService.class).create();
        try {
            PickupEdgeService service=controller.get();set(service,"loading",true);service.onStartCommand(new Intent(),0,1);
            View handle=(View)get(service,"handle");
            touch(handle,MotionEvent.ACTION_DOWN,20,40);touch(handle,MotionEvent.ACTION_MOVE,20,100);touch(handle,MotionEvent.ACTION_UP,20,100);
            assertNull(get(service,"drawer"));assertTrue(service.getSharedPreferences("admin",0).getFloat("edge_y",0)>.35f);
            touch(handle,MotionEvent.ACTION_DOWN,20,40);touch(handle,MotionEvent.ACTION_MOVE,-20,40);
            assertNotNull(get(service,"drawer"));
            ((View)get(service,"drawer")).performClick();assertNull(get(service,"drawer"));
        } finally {controller.destroy();}
    }
    @Test public void missingLoginOrPermissionPreventsAnOverlay() throws Exception {
        prepare();ShadowSettings.setCanDrawOverlays(false);var controller=Robolectric.buildService(PickupEdgeService.class).create();
        try { assertEquals(Service.START_NOT_STICKY,controller.get().onStartCommand(new Intent(),0,1));assertNull(get(controller.get(),"handle")); }
        finally { controller.destroy(); }
    }
    private void call(Object object,String name)throws Exception {var method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
    private Object get(Object object,String name)throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private void set(Object object,String name,Object value)throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private void touch(View view,int action,float x,float y) {MotionEvent event=MotionEvent.obtain(0,10,action,x,y,0);view.dispatchTouchEvent(event);event.recycle();}
}
