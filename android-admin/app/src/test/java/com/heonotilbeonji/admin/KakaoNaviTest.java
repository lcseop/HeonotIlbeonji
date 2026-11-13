package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.time.Duration;
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
public class KakaoNaviTest {
    @Test public void loadingDialogHasVisibleBodyBeforePageLoadsAndReportsTimeout() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            KakaoNavi.open(controller.get(), "서울 예시로 1");
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            WebView web = findWebView(dialog.getWindow().getDecorView());
            assertNotNull(web);
            FrameLayout panel = (FrameLayout) web.getParent();
            panel.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
            assertTrue(web.getMeasuredHeight() > 0);
            TextView status = (TextView) panel.getChildAt(1);
            assertEquals(View.VISIBLE, status.getVisibility());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
            assertTrue(status.getText().toString().contains("다시 열어"));
            assertEquals(View.INVISIBLE, web.getVisibility());
            dialog.dismiss();
        }
    }

    private static WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView web = findWebView(group.getChildAt(i));
                if (web != null) return web;
            }
        }
        return null;
    }
    @Test public void originalAddressLivesOnlyInFragment() {
        String address = "서울 예시로 1 #3 & 101동";
        Uri page = KakaoNavi.lookupPage(address);
        assertEquals(KakaoNavi.PAGE, page.buildUpon().fragment(null).build().toString());
        assertEquals(address, page.getFragment());
        assertNull(page.getQuery());
    }

    @Test public void onlyKakaoDestinationCanLaunchAndIntentExtrasAreIgnored() {
        String url = "kakaonavi-sdk://navigate?param=%7B%7D";
        for (String raw : new String[]{url, "intent:" + url + "#Intent;package=example.bad;S.injected=test;end;"}) {
            Intent intent = KakaoNavi.destinationIntent(Uri.parse(raw));
            assertNotNull(intent);
            assertEquals(KakaoNavi.PACKAGE, intent.getPackage());
            assertEquals(url, intent.getDataString());
            assertNull(intent.getExtras());
        }
        assertNull(KakaoNavi.destinationIntent(Uri.parse("intent://other#Intent;scheme=malicious;end;")));
        assertNull(KakaoNavi.destinationIntent(Uri.parse("https://1beonji.com")));
    }
}
