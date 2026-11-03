package dev.fourthbreakfast.planner.updates

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Installs a newer release on the phone: the apk is fetched into the app's cache and handed to Android's own
 * installer, which asks the person to allow it. The update keeps everything, as the apk is signed with the same key.
 */
suspend fun installOnPhone(context: Context, updates: Updates, release: Release) {
    val url = release.apk ?: return
    val file = File(context.cacheDir, "updates/planner-${release.version}.apk")
    if (!updates.download(url, file)) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
