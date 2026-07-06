package com.yourhour.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#212121"));
        getWindow().setNavigationBarColor(Color.BLACK);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.setBackgroundColor(Color.parseColor("#121212"));
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new UsageStatsBridge(this), "YourHourNative");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Let the web layer refresh with live data (e.g. after the user
        // grants usage access and returns from Settings).
        if (webView != null) {
            webView.evaluateJavascript(
                "window.onNativeResume && window.onNativeResume();", null);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript(
                "window.onNativeBack ? window.onNativeBack() : false;",
                handled -> {
                    if (!"true".equals(handled)) {
                        runOnUiThread(MainActivity.super::onBackPressed);
                    }
                });
        } else {
            super.onBackPressed();
        }
    }
}
