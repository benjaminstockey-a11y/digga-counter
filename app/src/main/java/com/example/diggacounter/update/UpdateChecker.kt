package com.example.diggacounter.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.example.diggacounter.BuildConfig
import com.example.diggacounter.Config
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(val versionName: String, val apkUrl: String, val notes: String)

/**
 * Checks GitHub Releases for a newer build than the one currently installed and, if the
 * user agrees, downloads the APK via the system DownloadManager and opens it so Android's
 * package installer takes over (the user still has to tap "Install" - this app can't
 * silently replace itself).
 *
 * Requires a public GitHub repo whose releases each attach one .apk file, built
 * automatically by .github/workflows/release-apk.yml on every push to main.
 */
object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Returns update info if the latest GitHub release is newer than this build, else null. */
    fun checkForUpdate(): UpdateInfo? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${Config.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val latestVersion = json.optString("tag_name").removePrefix("v")
            if (latestVersion.isBlank() || latestVersion == BuildConfig.VERSION_NAME) return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            apkUrl ?: return null

            return UpdateInfo(
                versionName = latestVersion,
                apkUrl = apkUrl,
                notes = json.optString("body")
            )
        }
    }

    /** Queues the APK download; Android shows a notification, tapping it opens the installer. */
    fun downloadAndInstall(context: Context, update: UpdateInfo) {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Digga Counter Update ${update.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "digga-counter-update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }
}
