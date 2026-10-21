package com.nutomic.syncthingandroid.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app self-update. Talks to the release site (version.json + APK hosted on
 * the same WorkBuddy site), compares versions and installs the downloaded APK.
 */
object AppUpdater {

    private const val TAG = "AppUpdater"

    /** Release site serving version.json and the APK (see release-site folder). */
    const val UPDATE_MANIFEST_URL = "https://syncthing.app.workbuddy.host/version.json"

    /**
     * Append a one-shot query parameter so CDNs / middle boxes can never serve
     * a cached manifest or APK: a stale version.json made the app compare
     * against an old release and download an outdated build.
     */
    private fun cacheBust(url: String): String =
        url + (if (url.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis()

    private val mainHandler = Handler(Looper.getMainLooper())

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Long,
        val apkUrl: String,
        val sha256: String?,
        val changelog: String?
    )

    /** Java-friendly callback: receives the fetched info, or null on failure. */
    fun interface FetchCallback { fun onResult(info: UpdateInfo?) }

    /** Java-friendly callback: download progress 0..100. */
    fun interface ProgressCallback { fun onProgress(percent: Int) }

    /** Java-friendly callback: downloaded file, or null on failure. */
    fun interface DoneCallback { fun onDone(file: File?) }

    @JvmStatic
    fun onMainThread(runnable: Runnable) {
        mainHandler.post(runnable)
    }

    /** Currently installed version code. */
    @JvmStatic
    fun installedVersionCode(context: Context): Long {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    /** Currently installed version name. */
    @JvmStatic
    fun installedVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Fetch the remote update manifest on a background thread.
     * onResult receives null on any failure (network, parse, ...).
     */
    @JvmStatic
    fun fetchUpdateInfo(onResult: FetchCallback) {
        Thread {
            var result: UpdateInfo? = null
            try {
                val conn = URL(cacheBust(UPDATE_MANIFEST_URL)).openConnection() as HttpURLConnection
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.instanceFollowRedirects = true
                try {
                    val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    val json = JSONObject(body)
                    val info = UpdateInfo(
                        versionName = json.optString("versionName", ""),
                        versionCode = json.optLong("versionCode", 0L),
                        apkUrl = json.optString("apkUrl", ""),
                        sha256 = if (json.has("sha256")) json.getString("sha256") else null,
                        changelog = if (json.has("changelog")) json.getString("changelog") else null
                    )
                    if (info.versionName.isNotEmpty() && info.versionCode > 0 && info.apkUrl.isNotEmpty()) {
                        result = info
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                result = null
            }
            onMainThread { onResult.onResult(result) }
        }.start()
    }

    /** True when the remote info is newer than the installed build. */
    @JvmStatic
    fun isNewer(info: UpdateInfo, context: Context): Boolean =
        info.versionCode > installedVersionCode(context)

    /**
     * Download the APK to the app cache. onProgress gets 0..100 on the main
     * thread; onDone receives the downloaded file or null on failure.
     */
    @JvmStatic
    fun downloadApk(context: Context, info: UpdateInfo, onProgress: ProgressCallback, onDone: DoneCallback) {
        Thread {
            var target: File? = null
            try {
                val conn = URL(cacheBust(info.apkUrl)).openConnection() as HttpURLConnection
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                val total = conn.contentLengthLong
                val tmp = File.createTempFile("syncthing_update_", ".apk.tmp")
                conn.inputStream.use { input ->
                    var written = 0L
                    java.io.FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            if (total > 0) {
                                val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                                onMainThread { onProgress.onProgress(pct) }
                            }
                        }
                    }
                }
                conn.disconnect()

                if (info.sha256 != null && info.sha256.isNotBlank()) {
                    val actual = sha256Hex(tmp)
                    if (!actual.equals(info.sha256, ignoreCase = true)) {
                        tmp.delete()
                        onMainThread { onDone.onDone(null) }
                        return@Thread
                    }
                }

                // Belt-and-braces: verify the downloaded archive really is the
                // advertised build before it can ever reach the installer.
                val pm = context.packageManager
                val archiveInfo = pm.getPackageArchiveInfo(tmp.absolutePath, 0)
                if (archiveInfo == null || archiveInfo.longVersionCode != info.versionCode) {
                    tmp.delete()
                    onMainThread { onDone.onDone(null) }
                    return@Thread
                }

                // Version-stamped name: a leftover download of an older build
                // can never be picked up or confused with this one.
                val finalFile = File(tmp.parentFile, "syncthing_update_" + info.versionName + ".apk")
                if (finalFile.exists()) finalFile.delete()
                if (!tmp.renameTo(finalFile)) {
                    tmp.copyTo(finalFile, overwrite = true)
                    tmp.delete()
                }
                target = finalFile
            } catch (e: Exception) {
                target = null
            }
            val result = target
            onMainThread { onDone.onDone(result) }
        }.start()
    }

    /** Whether this app may install packages (Android 8+ "unknown apps" gate). */
    @JvmStatic
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Open the system settings page that grants the install permission. */
    @JvmStatic
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // fall through to app details page
            }
        }
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Trigger the system package installer for the downloaded APK. */
    @JvmStatic
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
