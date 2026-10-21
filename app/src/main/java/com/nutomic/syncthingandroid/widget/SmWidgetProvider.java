package com.nutomic.syncthingandroid.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;

/**
 * Smartisan home screen widgets (2x2 and 4x2).
 *
 * Data sources:
 *  - While the app runs, MainActivity pushes live state via {@link #pushUpdate}.
 *  - Otherwise the periodic system update (30 min) probes the local Syncthing
 *    REST endpoint; folder/device counts always come from the on-disk config.
 */
public class SmWidgetProvider extends AppWidgetProvider {

    private static final int COLOR_RUNNING = Color.parseColor("#6EBF3B");
    private static final int COLOR_STARTING = Color.parseColor("#E5C31E");
    private static final int COLOR_STOPPED = Color.parseColor("#D97A1C");

    private static SSLContext sTrustAllContext;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateWidget(context, manager, id);
        }
        refreshAsync(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                          int appWidgetId, android.os.Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
        refreshAsync(context);
    }

    /** Re-renders one widget, choosing the layout from the current span. */
    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        android.os.Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        boolean isWide = minWidthDp >= 250;
        RemoteViews views = new RemoteViews(context.getPackageName(),
                isWide ? R.layout.widget_4x2 : R.layout.widget_2x2);
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context));
        manager.updateAppWidget(appWidgetId, views);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Live push from the running app. percent &lt; 0 means "not available yet".
     */
    public static void pushUpdate(Context context, boolean running, boolean starting,
                                  int folders, int devices, int percent) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName[] providers = {
                new ComponentName(context, SmWidgetProvider.class),
                new ComponentName(context, SmWidgetProvider4x2.class),
        };
        for (ComponentName provider : providers) {
            for (int id : manager.getAppWidgetIds(provider)) {
                RemoteViews views = viewsFor(context, manager, id, running, starting, folders, devices, percent);
                manager.updateAppWidget(id, views);
            }
        }
    }

    private static RemoteViews viewsFor(Context context, AppWidgetManager manager, int appWidgetId,
                                        boolean running, boolean starting,
                                        int folders, int devices, int percent) {
        android.os.Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110);
        boolean isWide = minWidthDp >= 250;
        RemoteViews views = new RemoteViews(context.getPackageName(),
                isWide ? R.layout.widget_4x2 : R.layout.widget_2x2);
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context));

        int dotColor = running ? COLOR_RUNNING : (starting ? COLOR_STARTING : COLOR_STOPPED);
        String statusText;
        if (running) {
            statusText = context.getString(R.string.widget_status_running);
        } else if (starting) {
            statusText = context.getString(R.string.widget_status_starting);
        } else {
            statusText = context.getString(R.string.widget_status_stopped);
        }
        String counts = context.getString(R.string.widget_count_format, folders, devices);

        views.setInt(R.id.widget_status_dot, "setColorFilter", dotColor);
        views.setTextViewText(R.id.widget_status_text, statusText);
        views.setTextViewText(R.id.widget_counts, counts);

        if (isWide) {
            String syncLine;
            if (running && percent >= 0) {
                syncLine = context.getString(R.string.widget_synced_format, percent);
            } else {
                syncLine = statusText;
            }
            views.setTextViewText(R.id.widget_sync_line, syncLine);
            // The big line carries the state while stopped; keep the pill quiet then.
            views.setTextViewText(R.id.widget_status_text, running ? statusText : "");
        }
        return views;
    }

    /**
     * Periodic refresh while the app UI is closed: probe the local REST endpoint
     * and read counts from the on-disk config (same process, so the file is ours).
     */
    private static void refreshAsync(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            boolean running = false;
            try {
                String apiKey = readConfigValue(app, "apikey");
                if (!apiKey.isEmpty() && probeRest(apiKey)) {
                    running = true;
                }
            } catch (Exception ignored) {
            }
            int folders = countConfigElements(app, "<folder ");
            int devices = countConfigElements(app, "<device ");
            final boolean isRunning = running;
            final int f = folders, d = devices;
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> pushUpdate(app, isRunning, false, f, d, -1));
        }, "sm-widget-refresh").start();
    }

    /** Lightweight REST probe; any valid response means syncthing is serving. */
    private static boolean probeRest(String apiKey) throws Exception {
        synchronized (SmWidgetProvider.class) {
            if (sTrustAllContext == null) {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }}, null);
                sTrustAllContext = sc;
            }
        }
        HttpsURLConnection conn = (HttpsURLConnection)
                new URL("https://127.0.0.1:8384/rest/system/status").openConnection();
        conn.setSSLSocketFactory(sTrustAllContext.getSocketFactory());
        conn.setHostnameVerifier((hostname, session) -> true);
        conn.setRequestProperty("X-API-Key", apiKey);
        conn.setConnectTimeout(800);
        conn.setReadTimeout(1500);
        try {
            int code = conn.getResponseCode();
            return code >= 200 && code < 500; // 403 would still mean "serving"
        } finally {
            conn.disconnect();
        }
    }

    private static String readConfigValue(Context context, String tag) {
        String xml = readConfigXml(context);
        if (xml.isEmpty()) return "";
        Matcher m = Pattern.compile("<" + tag + ">([^<]+)</" + tag + ">").matcher(xml);
        return m.find() ? m.group(1) : "";
    }

    private static int countConfigElements(Context context, String marker) {
        return countOccurrences(readConfigXml(context), marker);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String readConfigXml(Context context) {
        try {
            File config = new File(context.getFilesDir(), "config.xml");
            if (!config.exists()) return "";
            FileInputStream fis = new FileInputStream(config);
            byte[] buf = new byte[(int) config.length()];
            int read = fis.read(buf);
            fis.close();
            return new String(buf, 0, Math.max(read, 0), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
