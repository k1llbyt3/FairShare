package com.fairshare.android.core.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Shape system for FairShare.
 * Avoids giant pills everywhere.
 * As defined in specs/Design.md Section 11.
 */
@Immutable
data class FSShapes(
    val card: CornerBasedShape = RoundedCornerShape(14.dp),
    val button: CornerBasedShape = RoundedCornerShape(12.dp),
    val input: CornerBasedShape = RoundedCornerShape(12.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val dialog: CornerBasedShape = RoundedCornerShape(20.dp),
    val avatar: CornerBasedShape = CircleShape,
    val badge: CornerBasedShape = RoundedCornerShape(6.dp)
)

val LocalFSShapes = staticCompositionLocalOf { FSShapes() }
