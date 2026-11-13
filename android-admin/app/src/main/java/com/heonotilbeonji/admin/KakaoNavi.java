package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

final class KakaoNavi {
    static final String PACKAGE = "com.locnall.KimGiSa";
    static final String PAGE = "https://1beonji.com/html/admin-navi.html";
    private static final String STORE = "https://play.google.com/store/apps/details?id=" + PACKAGE;

    static Uri lookupPage(String address) {
        // The fragment is processed on the device and is not sent to our web server.
        return Uri.parse(PAGE).buildUpon().fragment(address).build();
    }

    static Intent destinationIntent(Uri uri) {
        if ("intent".equals(uri.getScheme())) {
            try { uri = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getData(); }
            catch (java.net.URISyntaxException error) { return null; }
        }
        if (uri == null || !"kakaonavi-sdk".equals(uri.getScheme()) || !"navigate".equals(uri.getAuthority())
                || !"".equals(uri.getPath()) || uri.getQueryParameter("param") == null) return null;
        return new Intent(Intent.ACTION_VIEW, uri).setPackage(PACKAGE);
    }

    static void open(Activity activity, String address) {
        if (address == null || address.trim().isEmpty()) return;
        WebView web = new WebView(activity);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        // AlertDialog's wrap-content panel can measure an unloaded WebView at zero height.
        FrameLayout panel = new FrameLayout(activity);
        int height = Math.min(AdminPlanner.dp(activity, 320),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * .58f));
        panel.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        TextView loading = new TextView(activity);
        loading.setText("주소 확인 화면을 불러오는 중…");
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(AdminPlanner.dp(activity, 16), 0, AdminPlanner.dp(activity, 16), 0);
        loading.setBackgroundColor(android.graphics.Color.WHITE);
        panel.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("카카오내비")
                .setView(panel).setNegativeButton("닫기", null).create();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (!dialog.isShowing()) return;
            web.stopLoading();
            web.setVisibility(View.INVISIBLE);
            loading.setText("주소 확인 화면을 불러오지 못했습니다. 닫고 다시 열어 주세요.");
        };
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (!dialog.isShowing() || view.getVisibility() != View.VISIBLE
                        || !PAGE.equals(Uri.parse(url).buildUpon().fragment(null).build().toString())) return;
                handler.removeCallbacks(timeout);
                loading.setVisibility(View.GONE);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handle(request.getUrl(), request.isForMainFrame());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handle(Uri.parse(url), true);
            }
            private boolean handle(Uri uri, boolean mainFrame) {
                Intent launch = destinationIntent(uri);
                if (launch != null) {
                    try { activity.startActivity(launch); dialog.dismiss(); }
                    catch (ActivityNotFoundException | SecurityException error) { offerInstall(activity); }
                    return true;
                }
                if ("https".equals(uri.getScheme()) && "kakaonavi.kakao.com".equals(uri.getHost())
                        && "/launch/index.do".equals(uri.getPath())) { offerInstall(activity); return true; }
                return mainFrame || !"https".equals(uri.getScheme());
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fail();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse error) {
                if (request.isForMainFrame()) fail();
            }
            private void fail() {
                if (!dialog.isShowing()) return;
                handler.removeCallbacks(timeout);
                timeout.run();
            }
        });
        dialog.setOnDismissListener(d -> {
            handler.removeCallbacks(timeout);
            web.stopLoading();
            panel.removeView(web);
            web.destroy();
        });
        dialog.show();
        handler.postDelayed(timeout, 15000);
        web.loadUrl(lookupPage(address).toString());
    }

    private static void offerInstall(Activity activity) {
        new AlertDialog.Builder(activity).setTitle("카카오내비를 열 수 없습니다")
                .setMessage("카카오내비가 설치되어 있는지 확인해 주세요.")
                .setNegativeButton("닫기", null).setPositiveButton("Play 스토어", (d, which) -> {
                    try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(STORE))); }
                    catch (ActivityNotFoundException | SecurityException error) {
                        Toast.makeText(activity, "Play 스토어에서 카카오내비를 검색해 주세요.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }
}
