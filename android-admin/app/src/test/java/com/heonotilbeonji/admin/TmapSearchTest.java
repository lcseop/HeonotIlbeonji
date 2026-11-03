package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class TmapSearchTest {
    public static class LaunchActivity extends Activity {
        final ArrayList<Intent> launches = new ArrayList<>();
        boolean missingTmap, missingStore, missingBrowser;
        @Override public void startActivity(Intent intent) {
            launches.add(intent);
            String scheme = intent.getData().getScheme();
            if ((missingTmap && scheme.equals("tmap")) || (missingStore && scheme.equals("market"))
                    || (missingBrowser && scheme.equals("https"))) throw new ActivityNotFoundException();
        }
    }

    @Test public void fullAddressIncludingKoreanAndSpecialCharactersSurvivesUriEncoding() {
        String address = "경기도 김포시 고촌읍 전호로56번길 30, 1층 (우측) A&B #101 + 출입구";
        Intent intent = TmapSearch.searchIntent(address);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(TmapSearch.PACKAGE, intent.getPackage());
        assertEquals("tmap", intent.getData().getScheme());
        assertEquals("search", intent.getData().getHost());
        assertEquals(address, intent.getData().getQueryParameter("name"));
        assertEquals(1, intent.getData().getQueryParameterNames().size());
    }

    @Test public void missingTmapRequiresConfirmationBeforeStoreAndCanFallBackToBrowser() {
        try (var controller = Robolectric.buildActivity(LaunchActivity.class).setup()) {
            LaunchActivity activity = controller.get(); activity.missingTmap = true;
            TmapSearch.open(activity, "김포시 예시로 1");
            assertEquals(1, activity.launches.size());
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, activity.launches.size());
            TmapSearch.open(activity, "김포시 예시로 1"); activity.missingStore = true;
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals("market", activity.launches.get(2).getData().getScheme());
            assertEquals("https", activity.launches.get(3).getData().getScheme());
            assertEquals(TmapSearch.PACKAGE, activity.launches.get(3).getData().getQueryParameter("id"));
            activity.missingBrowser = true;
            TmapSearch.open(activity, "김포시 예시로 1");
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    @Test public void blankAddressDoesNotLaunchAnApp() {
        try (var controller = Robolectric.buildActivity(LaunchActivity.class).setup()) {
            TmapSearch.open(controller.get(), "   "); TmapSearch.open(controller.get(), null);
            assertTrue(controller.get().launches.isEmpty());
        }
    }

    @Test public void detailAddressIsClickableAndUsesOriginalRatherThanSummary() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            String full = "경기도 김포시 고촌읍 전호로56번길 30, 1층 (우측)";
            JSONObject item = new JSONObject().put("id", "test").put("name", "예시")
                    .put("phone", "01000000000").put("address", full);
            var detail = MainActivity.class.getDeclaredMethod("showDetail", JSONObject.class);
            detail.setAccessible(true); detail.invoke(activity, item);
            View root = ShadowDialog.getLatestDialog().getWindow().getDecorView();
            TextView address = (TextView) PlannerInteractionTest.description(root, "티맵에서 주소 검색: " + full);
            assertNotNull(address); assertEquals(full, address.getText().toString());
            assertTrue(address.isClickable()); assertFalse(address.isTextSelectable());
            address.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            Intent launched = shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched); assertEquals(full, launched.getData().getQueryParameter("name"));
        }
    }
}
