package org.muilab.notigpt.ui.component.drawer.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun findClosestHit(bounds: Map<String, Rect>, pointer: Offset, exclude: String?, valid: Set<String>): String? {
    val candidates = bounds.entries.filter { it.key in valid && it.key != exclude && pointer.x in it.value.left..it.value.right }
    val exact = candidates.firstOrNull { it.value.contains(pointer) }?.key
    if (exact != null) return exact
    return candidates.minByOrNull { abs(it.value.center.y - pointer.y) }?.key
}

fun unionRect(a: Rect, b: Rect): Rect = Rect(
    min(a.left, b.left),
    min(a.top, b.top),
    max(a.right, b.right),
    max(a.bottom, b.bottom)
)

fun Rect.inflate(p: Float): Rect = Rect(left - p, top - p, right + p, bottom + p)

