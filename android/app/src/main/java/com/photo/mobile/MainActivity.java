package com.photo.mobile;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.http.SslError;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.BridgeActivity;

/** Capacitor host with an isolated remote WebView: website scripts have only an origin-restricted refresh message channel. */
public class MainActivity extends BridgeActivity {
    private static final String HOME = "https://antiquewhite-hornet-229089.hostingersite.com/";
    private WebView site;
    private ProgressBar progress;
    private LinearLayout error;
    private SwipeRefreshLayout refresh;
    private ValueCallback<Uri[]> upload;
    private GeolocationPermissions.Callback locationCallback;
    private String locationOrigin;
    private boolean locationPermissionInFlight;
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void finishLocationRequest(boolean allow) {
        GeolocationPermissions.Callback callback = locationCallback;
        String origin = locationOrigin;
        locationCallback = null;
        locationOrigin = null;
        if (callback != null) callback.invoke(origin, allow, false);
    }
    private void allowLocationIfReady() {
        if (locationCallback == null) return;
        if (site.getUrl() == null || !trusted(Uri.parse(site.getUrl())) || !trusted(Uri.parse(locationOrigin)) || !hasLocationPermission()) {
            finishLocationRequest(false); return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            finishLocationRequest(false);
            new AlertDialog.Builder(this).setTitle("Turn on location")
                .setMessage("Enable your phone's Location setting, then return and tap Use current GPS location again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Location settings", (d,w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).show();
            return;
        }
        finishLocationRequest(true);
    }
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            locationPermissionInFlight = false;
            if (locationCallback == null) return;
            if (hasLocationPermission()) allowLocationIfReady();
            else {
                finishLocationRequest(false);
                new AlertDialog.Builder(this).setTitle("Location permission needed")
                    .setMessage("To fill GPS coordinates, allow Location for Photo in App settings, then tap Use current GPS location again. You can also enter coordinates manually.")
                    .setNegativeButton("Not now", null)
                    .setPositiveButton("App settings", (d,w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))).show();
            }
        });
    private boolean failed;
    private boolean pageLoading;
    private boolean refreshBridgeAvailable;
    private String pullRefreshScript = "";
    private String mobileLayoutScript = "";
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTimeout = () -> {
        site.stopLoading();
        pageLoading = false;
        progress.setVisibility(View.GONE);
        finishRefresh();
        installPullRefresh();
        Toast.makeText(this, "Page is taking longer to load. Please check your connection.", Toast.LENGTH_LONG).show();
    };
    private void finishRefresh() {
        refreshHandler.removeCallbacks(refreshTimeout);
        if (refresh != null) refresh.setRefreshing(false);
        if (site != null) site.evaluateJavascript("window.__photoPullRefresh?.reset()", null);
    }
    private void requestRefresh() {
        if (pageLoading || refresh.isRefreshing()) { site.evaluateJavascript("window.__photoPullRefresh?.reset()", null); return; }
        String current = site.getUrl();
        if (current == null || !trusted(Uri.parse(current))) return;
        refresh.setRefreshing(true);
        refreshHandler.removeCallbacks(refreshTimeout);
        refreshHandler.postDelayed(refreshTimeout, 20000);
        site.reload();
    }
    private void installPullRefresh() {
        if (!failed) site.evaluateJavascript(mobileLayoutScript, null);
        if (refreshBridgeAvailable && !failed) site.evaluateJavascript(pullRefreshScript, null);
    }
    private String retryUrl = HOME;
    private boolean uploadAllowsMultiple;
    private void finishUpload(Uri[] files) {
        ValueCallback<Uri[]> callback = upload;
        upload = null;
        if (callback != null) callback.onReceiveValue(files);
    }
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (upload == null) return;
            Intent data = result.getData();
            if (result.getResultCode() != RESULT_OK || data == null) { finishUpload(null); return; }
            java.util.LinkedHashSet<Uri> selected = new java.util.LinkedHashSet<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null && "content".equals(uri.getScheme())) selected.add(uri);
                }
            }
            if (data.getData() != null && "content".equals(data.getData().getScheme())) selected.add(data.getData());
            if (!uploadAllowsMultiple && selected.size() > 1) {
                Uri first = selected.iterator().next(); selected.clear(); selected.add(first);
            }
            for (Uri uri : selected) {
                if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (SecurityException ignored) { /* Temporary picker grant still permits upload. */ }
                }
            }
            if (selected.isEmpty()) {
                finishUpload(null);
                Toast.makeText(this, "No readable photo was returned. Please choose it from Files or Photos again.", Toast.LENGTH_LONG).show();
            } else finishUpload(selected.toArray(new Uri[0]));
        });

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Deliver our AndroidX pickers before Capacitor/Cordova plugin routing.
        if (getActivityResultRegistry().dispatchResult(requestCode, resultCode, data)) return;
        super.onActivityResult(requestCode, resultCode, data);
    }
    private Intent photoPickerIntent(WebChromeClient.FileChooserParams params) {
        java.util.LinkedHashSet<String> types = new java.util.LinkedHashSet<>();
        for (String accept : params.getAcceptTypes()) {
            if (accept == null) continue;
            for (String item : accept.split(",")) {
                String type = item.trim().toLowerCase(java.util.Locale.ROOT);
                if (type.startsWith(".")) type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1));
                if (type != null && type.contains("/")) types.add(type);
            }
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setType(types.size() == 1 ? types.iterator().next() : "*/*");
        if (types.size() > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, uploadAllowsMultiple);
        return intent;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private boolean trusted(Uri u) {
        return "https".equals(u.getScheme()) && Uri.parse(HOME).getHost().equals(u.getHost())
            && (u.getPort() == -1 || u.getPort() == 443);
    }
    private void external(Uri uri) {
        String s = uri.getScheme();
        if (!("https".equals(s) || "http".equals(s) || "mailto".equals(s) || "tel".equals(s))) {
            Toast.makeText(this, "This link type is not supported", Toast.LENGTH_SHORT).show(); return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (android.content.ActivityNotFoundException e) { Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show(); }
    }
    private void showError() {
        failed = true; pageLoading = false; finishRefresh(); progress.setVisibility(View.GONE); site.setVisibility(View.GONE);
        error.setVisibility(View.VISIBLE); refresh.setRefreshing(false); refresh.setEnabled(false);
    }
    private void load(String url) {
        error.setVisibility(View.GONE); site.setVisibility(View.VISIBLE); refresh.setEnabled(true); site.loadUrl(url);
    }
    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248,249,252));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v,insets) -> {
            androidx.core.graphics.Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom); return insets;
        });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        FrameLayout content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1,0,1));
        refresh = new SwipeRefreshLayout(this);
        refresh.setColorSchemeColors(Color.rgb(25,177,179));
        refresh.setProgressBackgroundColorSchemeColor(Color.WHITE);
        site = new WebView(this);
        refresh.addView(site, new SwipeRefreshLayout.LayoutParams(-1,-1));
        // Never let native SwipeRefreshLayout steal WebView scrolling. The page's
        // DOM knows whether a nested scroll container is actually at its top.
        refresh.setOnChildScrollUpCallback((parent,child) -> true);
        try (java.io.InputStream script = getAssets().open("pull-refresh.js")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = script.read(buffer)) != -1) bytes.write(buffer, 0, count);
            pullRefreshScript = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException e) { throw new IllegalStateException("Missing pull-refresh asset", e); }
        try (java.io.InputStream script = getAssets().open("mobile-layout.js")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = script.read(buffer)) != -1) bytes.write(buffer, 0, count);
            mobileLayoutScript = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException e) { throw new IllegalStateException("Missing mobile layout asset", e); }
        refreshBridgeAvailable = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
        if (refreshBridgeAvailable) {
            // Expose only a reload request, only to this origin and its main frame.
            WebViewCompat.addWebMessageListener(site, "PhotoRefresh", Collections.singleton(HOME.substring(0,HOME.length()-1)),
                (view,message,origin,isMainFrame,reply) -> {
                    if (isMainFrame && trusted(origin) && "refresh".equals(message.getData())) requestRefresh();
                });
        }
        content.addView(refresh, new FrameLayout.LayoutParams(-1,-1));
        error = new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(17); error.setPadding(dp(28),dp(28),dp(28),dp(28));
        TextView message = new TextView(this); message.setText("Unable to load Photo\n\nCheck your connection and try again. Your account data stays on the website.");
        message.setTextSize(18); message.setGravity(17); error.addView(message);
        error.addView(button("Try again", () -> load(retryUrl))); error.setVisibility(View.GONE);
        content.addView(error, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        WindowInsetsControllerCompat systemBars = new WindowInsetsControllerCompat(getWindow(),root);
        systemBars.setAppearanceLightStatusBars(true);
        systemBars.setAppearanceLightNavigationBars(true);
        WebSettings settings=site.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(true); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(site,false);
        site.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (trusted(request.getUrl())) return false;
                if (request.isForMainFrame()) external(request.getUrl()); return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
                finishLocationRequest(false);
                pageLoading=true; failed=false; if(trusted(Uri.parse(url))) retryUrl=url;
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v,String url) {
                pageLoading=false; progress.setVisibility(View.GONE); finishRefresh(); installPullRefresh(); CookieManager.getInstance().flush();
            }
            @Override public void onPageCommitVisible(WebView v,String url) { finishRefresh(); installPullRefresh(); }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError e) { handler.cancel(); showError(); }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e) { if(r.isForMainFrame()) showError(); }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e) { if(r.isForMainFrame() && e.getStatusCode()>=400) showError(); }
        });
        site.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                // Only the configured HTTPS site can ask for foreground location.
                if (origin == null || !trusted(Uri.parse(origin)) || site.getUrl() == null || !trusted(Uri.parse(site.getUrl()))) {
                    callback.invoke(origin, false, false); return;
                }
                finishLocationRequest(false);
                locationOrigin = origin;
                locationCallback = callback;
                if (hasLocationPermission()) allowLocationIfReady();
                else if (!locationPermissionInFlight) {
                    locationPermissionInFlight = true;
                    locationPermission.launch(new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION });
                }
            }
            @Override public void onGeolocationPermissionsHidePrompt() { finishLocationRequest(false); }

            @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show(); return true;
            }
            @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK",(d,w)->result.confirm()).setNegativeButton("Cancel",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show(); return true;
            }
            @Override public void onProgressChanged(WebView v,int value) { progress.setProgress(value); }
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params) {
                finishUpload(null);
                upload = callback;
                uploadAllowsMultiple = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                Intent intent = photoPickerIntent(params);
                try { picker.launch(intent); }
                catch (android.content.ActivityNotFoundException e) {
                    intent.setAction(Intent.ACTION_GET_CONTENT);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { picker.launch(intent); }
                    catch (android.content.ActivityNotFoundException unavailable) {
                        finishUpload(null);
                        Toast.makeText(MainActivity.this, "No file picker available", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });
        site.setDownloadListener((url,agent,disposition,mime,length)->{
            if("https".equals(Uri.parse(url).getScheme())) new AlertDialog.Builder(this).setTitle("Open download in browser?")
                .setMessage("You may need to sign in again in your browser.").setNegativeButton("Cancel",null).setPositiveButton("Open",(d,w)->external(Uri.parse(url))).show();
            else Toast.makeText(this,"This download needs the website in your browser",Toast.LENGTH_LONG).show();
        });
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if(site.canGoBack()) { error.setVisibility(View.GONE);site.setVisibility(View.VISIBLE);refresh.setEnabled(true);site.goBack(); } else new AlertDialog.Builder(MainActivity.this).setTitle("Close Photo?").setNegativeButton("Stay",null).setPositiveButton("Close",(d,w)->finish()).show(); }
        });
        if(saved == null || site.restoreState(saved)==null) load(HOME); else installPullRefresh();
    }
    @Override public void onSaveInstanceState(Bundle state) { site.saveState(state); super.onSaveInstanceState(state); }
    @Override public void onPause() { if(site!=null) { site.onPause(); CookieManager.getInstance().flush(); } super.onPause(); }
    @Override public void onResume() { super.onResume(); if(site!=null) site.onResume(); }
    @Override public void onDestroy() { finishLocationRequest(false); refreshHandler.removeCallbacksAndMessages(null); finishUpload(null); if(site!=null) site.destroy(); super.onDestroy(); }
}
