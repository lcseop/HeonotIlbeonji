package com.heonotilbeonji.admin;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class LogoutConfirmationTest {
    @Test public void logoutRequiresConfirmationAndCancelKeepsLogin() throws Exception {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            var prefs = activity.getSharedPreferences("admin", Context.MODE_PRIVATE);
            prefs.edit().putString("session", "test-session").commit();
            var settings = MainActivity.class.getDeclaredMethod("showSettings");
            settings.setAccessible(true);
            settings.invoke(activity);
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 3, 3);
            AlertDialog confirmation = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("test-session", prefs.getString("session", ""));
            confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals("test-session", prefs.getString("session", ""));
            settings.invoke(activity);
            ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 3, 3);
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals("", prefs.getString("session", ""));
        }
    }
}
