package com.nearer.app

internal enum class StudioPerformanceAction { IDLE, WALK, RUN, JUMP, FALL, DANCE, FIGHT, FLY, SWIM, CLIMB, WAVE, TALK }
internal enum class StudioPracticalEffect { FIRE, SMOKE, SPARKS, LIGHTNING, EXPLOSION, WATER_SPLASH, DUST, MAGIC_GLOW, MUZZLE_FLASH }

internal data class StudioPerformanceCue(
    val id: String, val actorId: String, val startMs: Long, val endMs: Long,
    val action: StudioPerformanceAction, val animationName: String,
    val blendInMs: Long = 0L, val blendOutMs: Long = 0L
)
internal data class StudioDialogueCue(
    val id: String, val actorId: String, val startMs: Long, val endMs: Long,
    val audioUri: String, val mouthAnimationName: String? = null
)
internal data class StudioPracticalEffectCue(
    val id: String, val effect: StudioPracticalEffect, val startMs: Long, val endMs: Long,
    val anchorActorId: String? = null, val intensity: Float = 1f, val seed: Long = 0L
)
internal data class StudioPerformanceTimeline(
    val durationMs: Long, val performances: List<StudioPerformanceCue>,
    val dialogue: List<StudioDialogueCue>, val effects: List<StudioPracticalEffectCue>
)
internal data class StudioPerformanceValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Validates prepared, local skeletal animation and practical VFX cues. No AI or network assets. */
internal object StudioPerformanceTimelineValidator {
    const val MAX_CUES = 2_000

    fun validate(timeline: StudioPerformanceTimeline, scene: Studio3DScene, assets: List<Studio3DAsset>): StudioPerformanceValidation {
        val errors = mutableListOf<String>()
        if (timeline.durationMs !in 1L..StudioRenderPlanBuilder.MAX_DURATION_MS) errors += "Performance timeline duration must be between 1 ms and 24 hours."
        if (timeline.performances.size + timeline.dialogue.size + timeline.effects.size > MAX_CUES) errors += "Performance timeline exceeds the 2,000-cue limit."
        val actors = scene.actors.associateBy { it.id }
        val assetById = assets.associateBy { it.id }
        val allIds = timeline.performances.map { it.id } + timeline.dialogue.map { it.id } + timeline.effects.map { it.id }
        if (allIds.any { it.isBlank() || it.length > 128 }) errors += "Cue IDs must contain 1–128 characters."
        if (allIds.toSet().size != allIds.size) errors += "Cue IDs must be unique across the timeline."
        fun validRange(id: String, start: Long, end: Long) {
            if (start < 0L || end <= start || end > timeline.durationMs) errors += id + ": cue time range must be inside the project duration."
        }
        timeline.performances.forEach { cue ->
            validRange(cue.id, cue.startMs, cue.endMs)
            val actor = actors[cue.actorId]
            if (actor == null) errors += cue.id + ": performance actor does not exist in the scene."
            else {
                val asset = assetById[actor.assetId]
                if (asset == null || cue.animationName !in asset.animationNames) errors += cue.id + ": action animation is not present in the actor's local GLB asset."
                if (cue.animationName.isBlank() || cue.animationName.length > 80) errors += cue.id + ": animation name must contain 1–80 characters."
            }
            if (cue.blendInMs < 0L || cue.blendOutMs < 0L || cue.blendInMs > cue.endMs - cue.startMs || cue.blendOutMs > cue.endMs - cue.startMs) errors += cue.id + ": blend durations must fit inside the action cue."
        }
        timeline.dialogue.forEach { cue ->
            validRange(cue.id, cue.startMs, cue.endMs)
            if (cue.actorId !in actors) errors += cue.id + ": dialogue actor does not exist in the scene."
            val scheme = runCatching { java.net.URI(cue.audioUri).scheme?.lowercase() }.getOrNull()
            if (scheme !in setOf("content", "file", "android.resource")) errors += cue.id + ": dialogue audio must be a local offline source."
            if (cue.mouthAnimationName != null) {
                val asset = actors[cue.actorId]?.let { assetById[it.assetId] }
                if (asset == null || cue.mouthAnimationName !in asset.animationNames) errors += cue.id + ": mouth animation is missing from the actor's GLB asset."
            }
        }
        timeline.effects.forEach { cue ->
            validRange(cue.id, cue.startMs, cue.endMs)
            if (!cue.intensity.isFinite() || cue.intensity !in 0f..4f) errors += cue.id + ": effect intensity must be between 0 and 4."
            if (cue.anchorActorId != null && cue.anchorActorId !in actors) errors += cue.id + ": effect anchor actor does not exist in the scene."
        }
        return StudioPerformanceValidation(errors.distinct())
    }
}
