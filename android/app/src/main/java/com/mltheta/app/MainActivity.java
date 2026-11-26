package com.mltheta.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private BroadcastReceiver downloadReceiver;
    private static long lastApkDownloadId = -1;

    public static class AndroidDownloaderInterface {
        private final Context context;

        public AndroidDownloaderInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void downloadFile(String url, String filename, String mimeType) {
            try {
                if (url == null || url.isEmpty()) return;

                Uri uri = Uri.parse(url);
                DownloadManager.Request request = new DownloadManager.Request(uri);

                if (mimeType != null && !mimeType.isEmpty()) {
                    request.setMimeType(mimeType);
                }

                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    request.addRequestHeader("cookie", cookies);
                }

                String resolvedName = (filename != null && !filename.isEmpty()) 
                    ? filename 
                    : URLUtil.guessFileName(url, null, mimeType);

                request.setTitle(resolvedName);
                request.setDescription("Downloading " + resolvedName);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, resolvedName);

                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    long id = dm.enqueue(request);
                    if (resolvedName.toLowerCase().endsWith(".apk")) {
                        lastApkDownloadId = id;
                    }
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).runOnUiThread(() ->
                            Toast.makeText(context, "Downloading update: " + resolvedName + "...", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        AndroidDownloaderInterface downloader = new AndroidDownloaderInterface(this);

        // Expose AndroidDownloader to JavaScript window
        webView.addJavascriptInterface(downloader, "AndroidDownloader");

        // Intercept native webview download events
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimetype);
            downloader.downloadFile(url, filename, mimetype);
        });

        // Register receiver to notify when APK update downloads finish
        registerDownloadReceiver();
    }

    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    return;
                }

                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == -1) return;

                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm == null) return;

                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = null;

                try {
                    cursor = dm.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (statusIdx != -1 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                            int localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            int titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);

                            String localUriString = localUriIdx != -1 ? cursor.getString(localUriIdx) : null;
                            String title = titleIdx != -1 ? cursor.getString(titleIdx) : "";

                            boolean isApk = (lastApkDownloadId == downloadId)
                                    || (title != null && title.toLowerCase().endsWith(".apk"))
                                    || (localUriString != null && localUriString.toLowerCase().endsWith(".apk"));

                            if (isApk) {
                                runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, "Download complete: " + (title.isEmpty() ? "Update APK" : title) + ". Check notification or Downloads to install.", Toast.LENGTH_LONG).show()
                                );
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            try {
                unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {}
        }
    }
}
