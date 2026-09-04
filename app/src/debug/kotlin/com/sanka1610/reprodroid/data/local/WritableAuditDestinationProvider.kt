package com.sanka1610.reprodroid.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class WritableAuditDestinationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.authority != AUTHORITY || mode != "rwt") throw FileNotFoundException("Unsupported test destination")
        if (uri.lastPathSegment == "reject") throw FileNotFoundException("Injected destination failure")
        if (uri.lastPathSegment != "export") throw FileNotFoundException("Unknown test destination")
        return ParcelFileDescriptor.open(
            destinationFile(requireNotNull(context)),
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_READ_WRITE,
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.sanka1610.reprodroid.debug.audit-destination"
        val URI: Uri = Uri.parse("content://$AUTHORITY/export")
        val REJECT_URI: Uri = Uri.parse("content://$AUTHORITY/reject")

        fun destinationFile(context: android.content.Context): File = context.cacheDir.resolve("audit-destination.json")
    }
}
