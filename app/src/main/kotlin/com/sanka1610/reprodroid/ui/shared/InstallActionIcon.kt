package com.sanka1610.reprodroid.ui.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun InstallActionIcon(description: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val unit = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit),
            strokeWidth = 2 * unit, cap = StrokeCap.Round,
        )
        line(12f, 3f, 12f, 15f)
        line(8f, 11f, 12f, 15f)
        line(12f, 15f, 16f, 11f)
        line(4f, 16f, 4f, 21f)
        line(4f, 21f, 20f, 21f)
        line(20f, 21f, 20f, 16f)
    }
}
