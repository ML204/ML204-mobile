package com.ml204.app;

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
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.getcapacitor.BridgeActivity;
import java.io.File;

public class MainActivity extends BridgeActivity {

    private BroadcastReceiver downloadReceiver;
    private Uri pendingInstallUri = null;
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

        // Register receiver for automatic APK update installation
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
                                Uri contentUri = resolveApkContentUri(dm, downloadId, localUriString, title);
                                if (contentUri != null) {
                                    promptApkInstall(contentUri);
                                }
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

    private Uri resolveApkContentUri(DownloadManager dm, long downloadId, String localUriString, String title) {
        try {
            File apkFile = null;
            if (localUriString != null && localUriString.startsWith("file://")) {
                apkFile = new File(Uri.parse(localUriString).getPath());
            }

            if (apkFile == null || !apkFile.exists()) {
                if (title != null && !title.isEmpty()) {
                    File candidate = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), title);
                    if (candidate.exists()) {
                        apkFile = candidate;
                    }
                }
            }

            if (apkFile != null && apkFile.exists()) {
                return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            }
        } catch (Exception ignored) {}

        try {
            return dm.getUriForDownloadedFile(downloadId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void promptApkInstall(Uri contentUri) {
        runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
                    pendingInstallUri = contentUri;
                    Toast.makeText(this, "Please allow ML204 to install app updates", Toast.LENGTH_LONG).show();
                    Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settingsIntent.setData(Uri.parse("package:" + getPackageName()));
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(settingsIntent);
                    return;
                }

                Intent installIntent = new Intent(Intent.ACTION_VIEW);
                installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                installIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(installIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pendingInstallUri != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls()) {
                Uri toInstall = pendingInstallUri;
                pendingInstallUri = null;
                promptApkInstall(toInstall);
            }
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
