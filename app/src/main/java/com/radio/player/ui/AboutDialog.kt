package com.radio.player.ui

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.radio.player.R

class AboutDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_about, null)

        val versionName = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        view.findViewById<TextView>(R.id.aboutVersion).text = "Radio Gaga v$versionName"

        val owner = getString(R.string.update_github_owner)
        val repo = getString(R.string.update_github_repo)
        val urlLabel = view.findViewById<TextView>(R.id.githubUrl)
        val row = view.findViewById<android.view.View>(R.id.githubRow)

        if (owner.isNotBlank() && repo.isNotBlank()) {
            val url = "https://github.com/$owner/$repo"
            urlLabel.text = "github.com/$owner/$repo"
            row.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Cannot open URL", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            row.visibility = android.view.View.GONE
        }

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.about)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }
}
