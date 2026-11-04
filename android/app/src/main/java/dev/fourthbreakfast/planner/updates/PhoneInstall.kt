package dev.fourthbreakfast.planner.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

/**
 * Installs a downloaded and checked update: it is handed to Android's own installer, which asks the person to confirm,
 * as it always does for an app that does not come from a store. Everything is kept, as the apk is signed with the same
 * key.
 */
fun installOnPhone(context: Context, ready: UpdateState.Ready) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", ready.file)
    val install = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(install)
    } catch (_: ActivityNotFoundException) {
        // a phone with its package installer taken away: the update stays offered, nothing else happens
    }
}
