package com.heonotilbeonji.admin;

import android.content.Intent;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35})
public class KakaoNaviTest {
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
