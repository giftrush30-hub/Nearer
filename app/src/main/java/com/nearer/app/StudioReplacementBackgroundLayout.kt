package com.nearer.app

internal enum class StudioReplacementBackgroundLayout {
    FULL_FRAME,
    PICTURE_IN_PICTURE
}

internal object StudioReplacementBackgroundLayoutPlanner {
    fun layouts(layout: StudioReplacementBackgroundLayout): List<StudioCompositorLayerLayout> {
        val background = when (layout) {
            StudioReplacementBackgroundLayout.FULL_FRAME ->
                StudioCompositorLayerLayout(1, -1f, -1f, 1f, 1f, zIndex = 0)
            StudioReplacementBackgroundLayout.PICTURE_IN_PICTURE ->
                StudioCompositorLayerLayout(1, 0.24f, -0.88f, 0.94f, -0.18f, zIndex = 0)
        }
        return listOf(
            // Foreground remains the top layer; transparent keyed pixels reveal the background.
            StudioCompositorLayerLayout(0, -1f, -1f, 1f, 1f, zIndex = 1),
            background
        )
    }
}
