package com.heonotilbeonji.admin;
import android.app.Activity;
import android.view.View;
import android.widget.ProgressBar;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class SaveSpeedTest {
    @Test public void savedEditsKeepStatusAndCustomerFieldsAndDoNotDuplicate() throws Exception {
        JSONArray rows = new JSONArray().put(new JSONObject().put("id","a").put("name","고객")
            .put("status","contacted").put("message","기존 내용").put("created_at",123));
        JSONObject change = new JSONObject().put("id","a").put("action","adminDetails")
            .put("reservedTime","09:30").put("adminNote","추가 내용");
        JSONArray saved = RequestCache.apply(RequestCache.apply(rows,change),change);
        assertEquals(1,saved.length()); assertEquals("contacted",saved.getJSONObject(0).getString("status"));
        assertEquals("기존 내용",saved.getJSONObject(0).getString("message"));
        assertEquals("09:30",saved.getJSONObject(0).getString("reservedTime"));
        assertFalse(rows.getJSONObject(0).has("adminNote"));
        assertEquals(0,RequestCache.remove(saved,"a").length());
        assertEquals(1,RequestCache.remove(saved,"other").length());
    }
    @Test public void creationIsImmediateAndKeepsOneRowOnRetry() throws Exception {
        JSONObject data = new JSONObject().put("requestId","new-id").put("action","create").put("name","고객");
        JSONArray first = RequestCache.apply(new JSONArray(),data);
        assertEquals(1,RequestCache.apply(first,data).length());
        assertEquals("new",first.getJSONObject(0).getString("status"));
    }
    @Test public void writesDoNotWaitForBlockedListDownloads() throws Exception {
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        LoadingTasks reads=new LoadingTasks(a,n -> {}), writes=new LoadingTasks(a,n -> {});
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1), saved=new CountDownLatch(1);
        try {
            reads.execute(() -> { started.countDown(); try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException ignored) {} });
            assertTrue(started.await(2,TimeUnit.SECONDS)); writes.execute(saved::countDown);
            assertTrue(saved.await(2,TimeUnit.SECONDS));
        } finally { release.countDown(); reads.shutdownNow(); writes.shutdownNow(); }
    }
    @Test public void savingShowsSpinnerAndFailureRetainsDraft() {
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>> callback = new java.util.concurrent.atomic.AtomicReference<>();
        AdminPlanner.FormEditor editor=new AdminPlanner.FormEditor(a,"",null,form -> {},phone -> null,
            (data,result) -> callback.set(result));
        editor.name.setText("고객"); editor.phone.setText("01012345678"); editor.save.performClick();
        assertNotNull(callback.get()); assertTrue(findVisibleSpinner(editor.dialog.getWindow().getDecorView()));
        assertFalse(editor.save.isEnabled()); callback.get().accept("연결 실패");
        assertTrue(editor.dialog.isShowing()); assertTrue(editor.save.isEnabled());
        assertEquals("고객",editor.name.getText().toString());
        assertFalse(findVisibleSpinner(editor.dialog.getWindow().getDecorView())); editor.dialog.dismiss();
    }
    private static boolean findVisibleSpinner(View view) {
        if(view instanceof ProgressBar && view.isShown()) return true;
        if(view instanceof android.view.ViewGroup group) for(int i=0;i<group.getChildCount();i++) if(findVisibleSpinner(group.getChildAt(i))) return true;
        return false;
    }
}
