package com.radio.player.ui

import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.util.UpdateChecker
import com.radio.player.util.UpdateInstaller

object UpdateDialog {

    fun show(context: Context, release: UpdateChecker.Release, currentVersion: String) {
        val sizeMb = if (release.apkSize > 0) " (${release.apkSize / 1_000_000} MB)" else ""
        val msg = buildString {
            append("Current: v$currentVersion\n")
            append("Latest: v${release.versionName}$sizeMb\n\n")
            if (release.notes.isNotBlank()) {
                append("What's new:\n")
                append(release.notes.take(800))
                if (release.notes.length > 800) append("\n…")
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Update available")
            .setMessage(msg)
            .setPositiveButton("Update") { _, _ ->
                if (!UpdateInstaller.canInstallUnknownApps(context)) {
                    Toast.makeText(context, "Allow installs from this app, then try again", Toast.LENGTH_LONG).show()
                    UpdateInstaller.openInstallSettings(context)
                    return@setPositiveButton
                }
                Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
                UpdateInstaller.download(context, release)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    fun showUpToDate(context: Context, currentVersion: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Up to date")
            .setMessage("You're on the latest version (v$currentVersion).")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
