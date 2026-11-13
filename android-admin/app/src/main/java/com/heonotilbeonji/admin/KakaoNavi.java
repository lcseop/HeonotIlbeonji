package com.heonotilbeonji.admin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("카카오내비")
                .setView(web).setNegativeButton("닫기", null).create();
        web.setWebViewClient(new WebViewClient() {
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
                dialog.dismiss();
                Toast.makeText(activity, "주소 검색을 열지 못했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_LONG).show();
            }
        });
        dialog.setOnDismissListener(d -> {
            web.stopLoading();
            if (web.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) web.getParent()).removeView(web);
            web.destroy();
        });
        dialog.show();
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
