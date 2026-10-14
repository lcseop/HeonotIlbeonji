package com.heonotilbeonji.admin;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ListView;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class PickupWidgetTest {
    private JSONObject request(String id, String date, String status) throws Exception {
        return new JSONObject().put("id", id).put("name", "고객 " + id).put("date", date).put("status", status)
            .put("phone", "01012345678").put("address", "주소는 위젯에 저장하지 않음");
    }
    @Test public void widgetIncludesBothStatesAndSortsByFullPreferredDate() throws Exception {
        JSONArray input = new JSONArray().put(request("later", "2027-01-01", "new"))
            .put(request("done", "2026-10-01", "done"))
            .put(request("contact", "2026-10-15", "contacted"))
            .put(request("early", "2026-10-14", "new"))
            .put(request("new", "2026-10-15", "new"))
            .put(request("unknown", "", "new"));
        JSONArray rows = PickupWidgetData.sorted(input);
        assertEquals(5, rows.length());
        String[] expected = {"early", "new", "contact", "later", "unknown"};
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], rows.getJSONObject(i).getString("id"));
        assertEquals("26.10.14 (수)", PickupWidgetData.dateLabel("2026-10-14"));
        assertEquals("날짜 미정", PickupWidgetData.dateLabel(""));
    }
    @Test public void cacheIsMinimalAndLoggedOutWidgetHasNoNames() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("session", "widget-test").commit();
        PickupWidgetData.store(context, new JSONArray().put(request("one", "2026-10-14", "new")));
        JSONObject cached = PickupWidgetData.read(context).getJSONObject(0);
        assertEquals("one", cached.getString("id"));
        assertFalse(cached.has("phone")); assertFalse(cached.has("address"));
        context.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().remove("session").commit();
        assertEquals(0, PickupWidgetData.read(context).length());
        PickupWidgetData.clear(context);
        assertEquals("[]", PickupWidgetData.prefs(context).getString("rows", ""));
    }
    @Test public void rowsRenderNamesDatesAndBothStatusLabels() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (String status : new String[]{"new", "contacted"}) {
            View row = PickupWidgetProvider.row(context, request("one", "2026-10-14", status)).apply(context, new FrameLayout(context));
            assertEquals("고객 one", ((TextView) row.findViewById(R.id.widget_name)).getText().toString());
            assertEquals("26.10.14 (수)", ((TextView) row.findViewById(R.id.widget_date)).getText().toString());
            assertEquals(status.equals("new") ? "새 신청" : "연락 완료", ((TextView) row.findViewById(R.id.widget_status)).getText().toString());
        }
        assertNotNull(PickupWidgetProvider.views(context, 1));
    }
    @Test public void methodIconsPersistAndDistinguishBothMethodsWithoutGuessingMissingValues() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("session", "widget-test").commit();
        JSONArray input = new JSONArray();
        for (String method : new String[]{"대면 수거", "비대면 수거", ""}) input.put(request(method, "2026-10-14", "new").put("pickupMethod", method));
        PickupWidgetData.store(context, input);
        JSONArray cached = PickupWidgetData.read(context);
        for (int i = 0; i < cached.length(); i++) {
            JSONObject item = cached.getJSONObject(i);
            String method = item.getString("pickupMethod");
            View row = PickupWidgetProvider.row(context, item).apply(context, new FrameLayout(context));
            View icon = row.findViewById(R.id.widget_method_icon);
            assertEquals(method.isEmpty() ? View.GONE : View.VISIBLE, icon.getVisibility());
            assertEquals(method, icon.getContentDescription().toString());
            if (!method.isEmpty()) assertTrue(row.getContentDescription().toString().contains(method));
        }
    }
    @Test public void hostedWidgetRendersCacheAndClearsAfterLogout() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        var manager = shadowOf(AppWidgetManager.getInstance(context));
        int id = manager.createWidget(PickupWidgetProvider.class, R.layout.pickup_widget);
        context.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("session", "widget-test").commit();
        PickupWidgetData.store(context, new JSONArray().put(request("one", "2026-10-14", "new"))
            .put(request("two", "2026-10-15", "contacted")));
        View widget = manager.getViewFor(id);
        assertEquals("2건", ((TextView) widget.findViewById(R.id.widget_count)).getText().toString());
        if (Build.VERSION.SDK_INT >= 31) {
            AppWidgetHostView host = new AppWidgetHostView(context);
            View hosted = PickupWidgetProvider.views(context, id).apply(context, host);
            host.addView(hosted);
            assertEquals(2, ((ListView) hosted.findViewById(R.id.widget_list)).getAdapter().getCount());
        }
        else {
            var service = Robolectric.buildService(PickupWidgetListService.class).create();
            try {
                var factory = service.get().onGetViewFactory(new Intent()); factory.onCreate();
                assertEquals(2, factory.getCount()); assertNotNull(factory.getViewAt(1)); factory.onDestroy();
            } finally { service.destroy(); }
        }
        context.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().remove("session").commit();
        PickupWidgetData.clear(context);
        View cleared = manager.getViewFor(id);
        assertEquals("0건", ((TextView) cleared.findViewById(R.id.widget_count)).getText().toString());
        assertEquals("앱에서 로그인해 주세요", ((TextView) cleared.findViewById(R.id.widget_empty)).getText().toString());
    }
    @Test public void widgetRequestOpensDetailsEvenWithAnUnrelatedAppFilter() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.getSharedPreferences("admin", Context.MODE_PRIVATE).edit().putString("session", "widget-test").commit();
            var requests = MainActivity.class.getDeclaredField("requests"); requests.setAccessible(true);
            requests.set(activity, new JSONArray().put(request("one", "2026-10-14", "contacted")));
            var filter = MainActivity.class.getDeclaredField("requestFilter"); filter.setAccessible(true); filter.set(activity, RequestListControls.DONE);
            var receive = MainActivity.class.getDeclaredMethod("receiveWidgetIntent", Intent.class); receive.setAccessible(true);
            Intent intent = new Intent().putExtra(PickupWidgetProvider.REQUEST_ID, "one");
            assertEquals(true, receive.invoke(activity, intent));
            assertFalse(intent.hasExtra(PickupWidgetProvider.REQUEST_ID));
            var open = MainActivity.class.getDeclaredMethod("openWidgetRequest"); open.setAccessible(true); open.invoke(activity);
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            assertNotNull(PlannerInteractionTest.text(dialog.getWindow().getDecorView(), "고객 one 님의 신청"));
            var pending = MainActivity.class.getDeclaredField("pendingWidgetRequestId"); pending.setAccessible(true);
            assertEquals("", pending.get(activity));
        }
    }
}
