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

/** How the calendar should arrange itself for the current device posture. */
enum class Posture {
    /** No fold (or non-separating): a single pane, or an even split if wide. */
    FLAT,

    /** Vertical fold: two facing pages side by side (a book). */
    BOOK,

    /** Horizontal fold: two stacked pages, screen above the hinge and below it. */
    TABLETOP,
}

/**
 * The current foldable posture plus the hinge geometry, expressed along the
 * split axis (X for [Posture.BOOK], Y for [Posture.TABLETOP]).
 *
 * @property hingePositionPx start of the hinge along the split axis, in window
 *   pixels, or `null` when there is no separating hinge.
 * @property hingeThicknessPx size of the hinge gutter along the split axis.
 */
data class FoldState(
    val posture: Posture,
    val hingePositionPx: Int?,
    val hingeThicknessPx: Int,
) {
    val isBook: Boolean get() = posture == Posture.BOOK
    val isTabletop: Boolean get() = posture == Posture.TABLETOP

    companion object {
        val Flat = FoldState(Posture.FLAT, hingePositionPx = null, hingeThicknessPx = 0)
    }
}

/**
 * Observes [WindowInfoTracker] and maps the current [FoldingFeature] to a
 * [FoldState]: a vertical fold becomes a [Posture.BOOK] spread and a horizontal
 * fold a [Posture.TABLETOP] spread, each with the gutter aligned to the hinge.
 */
@Composable
fun rememberFoldState(): FoldState {
    val activity = currentActivity() ?: return FoldState.Flat
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { layoutInfo ->
                val fold = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                    ?: return@map FoldState.Flat

                when (fold.orientation) {
                    FoldingFeature.Orientation.VERTICAL -> FoldState(
                        posture = Posture.BOOK,
                        hingePositionPx = fold.bounds.left,
                        hingeThicknessPx = fold.bounds.width(),
                    )

                    FoldingFeature.Orientation.HORIZONTAL -> FoldState(
                        posture = Posture.TABLETOP,
                        hingePositionPx = fold.bounds.top,
                        hingeThicknessPx = fold.bounds.height(),
                    )

                    else -> FoldState.Flat
                }
            }
    }
    return flow.collectAsStateWithLifecycle(initialValue = FoldState.Flat).value
}

/** Resolves the [Activity] backing the current composition, if any. */
@Composable
private fun currentActivity(): Activity? {
    var ctx: Context? = androidx.compose.ui.platform.LocalContext.current
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
