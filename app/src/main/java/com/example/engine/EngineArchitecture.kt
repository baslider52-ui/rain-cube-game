package com.example.engine

/**
 * İleride geliştirilecek gerçek zamanlı 2D Rain Cube oyun motoru için
 * temel teknik sözleşme ve modül sınırları.
 * Bu aşamada hiçbir oyun mekaniği, küp hareketi, XP veya rakip mantığı içermez.
 */
enum class GameLoopPhase {
    UNINITIALIZED,
    INFRASTRUCTURE_READY
}

data class FrameBudgetSpec(
    val targetFps: Int = 60,
    val fixedStepSeconds: Float = 1f / 60f,
    val maxFrameDeltaSeconds: Float = 0.05f
)

data class ViewportSpec(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val density: Float = 1f
) {
    val isPortrait: Boolean
        get() = heightPx >= widthPx
}

interface RenderSurfaceContract {
    val frameBudget: FrameBudgetSpec
    fun onViewportChanged(spec: ViewportSpec)
    fun onStep(deltaSeconds: Float)
}

object EngineArchitectureInfo {
    const val RENDER_BACKEND = "Jetpack Compose Canvas + Coroutine Frame Clock"
    const val ORIENTATION_LOCK = "Portrait (Dikey)"
    val defaultFrameBudget = FrameBudgetSpec()
    val currentPhase = GameLoopPhase.INFRASTRUCTURE_READY
}
