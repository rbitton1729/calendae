package com.rbitton.calendae.fold

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.map

/**
 * Describes how the calendar should arrange itself for the current device posture.
 *
 * @property isBookSpread true when the window should be split into two facing
 *   pages (a vertical fold, or simply a wide window).
 * @property hingeStartPx left edge of the physical hinge, in window pixels, or
 *   `null` when there is no separating hinge (split the window evenly instead).
 * @property hingeWidthPx width of the hinge gutter, in window pixels.
 */
data class FoldState(
    val isBookSpread: Boolean,
    val hingeStartPx: Int?,
    val hingeWidthPx: Int,
) {
    companion object {
        val Flat = FoldState(isBookSpread = false, hingeStartPx = null, hingeWidthPx = 0)
    }
}

/**
 * Observes [WindowInfoTracker] and maps the current [FoldingFeature] to a
 * [FoldState]. A vertical fold yields a book spread with the gutter aligned to
 * the hinge; absent a fold we stay flat (the caller may still split a wide
 * window evenly).
 */
@Composable
fun rememberFoldState(): FoldState {
    val activity = LocalActivity() ?: return FoldState.Flat
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                val fold = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
                    ?: return@map FoldState.Flat

                FoldState(
                    isBookSpread = true,
                    hingeStartPx = fold.bounds.left,
                    hingeWidthPx = fold.bounds.width(),
                )
            }
    }
    return flow.collectAsStateWithLifecycle(initialValue = FoldState.Flat).value
}

/** Resolves the [Activity] backing the current composition, if any. */
@Composable
private fun LocalActivity(): Activity? {
    var ctx: Context? = androidx.compose.ui.platform.LocalContext.current
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
