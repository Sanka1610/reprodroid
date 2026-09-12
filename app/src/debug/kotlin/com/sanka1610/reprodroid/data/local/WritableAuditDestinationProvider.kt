package com.sanka1610.reprodroid.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import com.sanka1610.reprodroid.BuildConfig
import java.io.File
import java.io.FileNotFoundException

class WritableAuditDestinationProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.authority != AUTHORITY || mode != "rwt") throw FileNotFoundException("Unsupported test destination")
        if (uri.lastPathSegment == "reject") throw FileNotFoundException("Injected destination failure")
        if (uri.lastPathSegment == "no-space") {
            return requireNotNull(context).getSystemService(StorageManager::class.java).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_WRITE_ONLY,
                object : ProxyFileDescriptorCallback() {
                    override fun onGetSize(): Long = 0

                    override fun onWrite(offset: Long, size: Int, data: ByteArray): Int =
                        throw ErrnoException("write", OsConstants.ENOSPC)

                    override fun onFsync() = Unit

                    override fun onRelease() = Unit
                },
                Handler(Looper.getMainLooper()),
            )
        }
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
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".phase48-audit-destination"
        val URI: Uri = Uri.parse("content://$AUTHORITY/export")
        val REJECT_URI: Uri = Uri.parse("content://$AUTHORITY/reject")
        val NO_SPACE_URI: Uri = Uri.parse("content://$AUTHORITY/no-space")

        fun destinationFile(context: android.content.Context): File = context.cacheDir.resolve("audit-destination.json")
    }
}
