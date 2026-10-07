package com.vamshi.field.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * One-shot spring when a freshly saved result lands in a cell: the value pops from 85% to full
 * size on [FieldMotion.fastSpatial].
 *
 * Only a *change* of [key] plays it. The first composition never does, which matters in the
 * testing grid: it renders athletes × tests, and scrolling a row back into view recomposes
 * its cells from scratch. Apply it to the cell's content box, which exists with or without a
 * score, so the first save animates; it scales the content only, so layout never moves.
 */
fun Modifier.resultEntrance(key: Any?): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val spec = FieldMotion.fastSpatial<Float>()
    val previous = remember { arrayOf(key) }
    LaunchedEffect(key) {
        if (key != null && key != previous[0]) {
            scale.snapTo(0.85f)
            scale.animateTo(1f, spec)
        }
        previous[0] = key
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
