package com.example.diggacounter.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Opens Android's package installer for the APK the update check downloaded. */
object ApkInstaller {
    fun installDownloaded(context: Context) {
        val file = File(context.getExternalFilesDir(null), "digga-counter-update.apk")
        if (!file.exists()) return

        val apkUri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
