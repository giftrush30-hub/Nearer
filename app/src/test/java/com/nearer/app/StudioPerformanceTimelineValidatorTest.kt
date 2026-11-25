package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioPerformanceTimelineValidatorTest {
    private val asset = Studio3DAsset("hero-model", "content://models/hero.glb", "a".repeat(64), 1024L,
        "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", listOf("Idle", "Run", "Jump", "Talk"), "humanoid-v1")
    private val scene = Studio3DScene("scene", "Action scene", listOf(Studio3DActor("hero", "hero-model")))

    @Test fun acceptsLocalPreparedActionsDialogueAndPracticalEffects() {
        val timeline = StudioPerformanceTimeline(10_000L,
            listOf(StudioPerformanceCue("run", "hero", 0L, 4_000L, StudioPerformanceAction.RUN, "Run", 250L, 300L)),
            listOf(StudioDialogueCue("line", "hero", 1_000L, 3_000L, "content://audio/dialogue", "Talk")),
            listOf(StudioPracticalEffectCue("fire", StudioPracticalEffect.FIRE, 2_000L, 5_000L, "hero", 1.5f, 42L)))
        assertTrue(StudioPerformanceTimelineValidator.validate(timeline, scene, listOf(asset)).isValid)
    }

    @Test fun rejectsRemoteDialogueAndMissingPreparedAnimation() {
        val timeline = StudioPerformanceTimeline(5_000L,
            listOf(StudioPerformanceCue("fly", "hero", 0L, 2_000L, StudioPerformanceAction.FLY, "Fly")),
            listOf(StudioDialogueCue("line", "hero", 0L, 1_000L, "https://example.invalid/voice.wav")), emptyList())
        val result = StudioPerformanceTimelineValidator.validate(timeline, scene, listOf(asset))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("not present") })
        assertTrue(result.errors.any { it.contains("local offline") })
    }

    @Test fun rejectsMissingActorOutOfRangeCueAndInvalidIntensity() {
        val timeline = StudioPerformanceTimeline(1_000L,
            listOf(StudioPerformanceCue("run", "missing", 0L, 2_000L, StudioPerformanceAction.RUN, "Run")),
            emptyList(), listOf(StudioPracticalEffectCue("blast", StudioPracticalEffect.EXPLOSION, 0L, 500L, intensity = 9f)))
        val result = StudioPerformanceTimelineValidator.validate(timeline, scene, listOf(asset))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("time range") })
        assertTrue(result.errors.any { it.contains("actor does not exist") })
        assertTrue(result.errors.any { it.contains("intensity") })
    }

    @Test fun rejectsDuplicateCueIdsAcrossCueTypes() {
        val timeline = StudioPerformanceTimeline(1_000L,
            listOf(StudioPerformanceCue("same", "hero", 0L, 500L, StudioPerformanceAction.IDLE, "Idle")),
            listOf(StudioDialogueCue("same", "hero", 0L, 500L, "content://audio/voice")), emptyList())
        val result = StudioPerformanceTimelineValidator.validate(timeline, scene, listOf(asset))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("unique") })
    }
}
