package com.sanka1610.reprodroid.ui.appdetail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.sanka1610.reprodroid.data.local.ReferenceDownloadStatus
import com.sanka1610.reprodroid.data.local.RegisteredAppRecord
import com.sanka1610.reprodroid.ui.*
import com.sanka1610.reprodroid.ui.shared.*
import java.io.File
import java.util.UUID

@Composable
internal fun ManagedAppIcon(record: RegisteredAppRecord, size: Dp = 52.dp) {
    val context = LocalContext.current
    val assetId = record.latestRelease?.selectedAsset
        ?.takeIf { it.downloadStatus == ReferenceDownloadStatus.VERIFIED.name }
        ?.releaseAssetId
    val iconFile = remember(context.filesDir, assetId) {
        val safeId = assetId?.let { runCatching { UUID.fromString(it).toString() }.getOrNull() }
        safeId?.let { File(context.filesDir, "reference-icons/$it.png") }
    }
    val lastModified = iconFile?.takeIf(File::isFile)?.lastModified() ?: 0L
    val storedImage = remember(iconFile?.absolutePath, lastModified) {
        iconFile?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
    }
    val selfImage = remember(context.packageName, record.app.registeredAppId) {
        if (isSelfRegistration(record)) {
            context.packageManager.getApplicationIcon(context.applicationInfo)
                .toBitmap(width = 192, height = 192)
                .asImageBitmap()
        } else {
            null
        }
    }
    val image = storedImage ?: selfImage
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(record.app.resolvedDisplayName.take(2).uppercase(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
