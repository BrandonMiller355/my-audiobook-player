package com.brandonmiller.audiobookplayer.ui.library

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * `ACTION_OPEN_DOCUMENT` with the flag that makes the grant outlive the process.
 *
 * [ActivityResultContracts.OpenDocumentTree] asks for `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`,
 * which is why folder books survive a reboot. [ActivityResultContracts.OpenDocument] does not — and
 * without it `takePersistableUriPermission` fails, so a single-file book would break on the next
 * launch, silently and long after the fact (design D3).
 *
 * The MIME filter is deliberately loose: a `.m4b` is reported as `audio/mp4`, `audio/x-m4b`,
 * `audio/m4b`, or `application/octet-stream` depending on the provider, so nothing is hidden here
 * and the extension decides afterwards (design D4).
 */
class OpenPersistableDocument : ActivityResultContracts.OpenDocument() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }

    companion object {
        val AUDIO_MIME_TYPES = arrayOf("audio/*", "application/octet-stream")

        /**
         * Loose for the same reason [AUDIO_MIME_TYPES] is: an `.epub` is reported as
         * `application/epub+zip`, `application/zip`, or `application/octet-stream` depending on the
         * provider, so nothing is hidden here and the file's own contents decide afterwards
         * (`add-ebook-companion` design D13).
         */
        val EBOOK_MIME_TYPES = arrayOf(
            "application/epub+zip",
            "application/zip",
            "application/octet-stream",
        )
    }
}
