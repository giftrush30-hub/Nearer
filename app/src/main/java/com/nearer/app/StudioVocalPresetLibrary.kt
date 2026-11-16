package com.nearer.app

/**
 * Starter settings for NeaRer's offline Vocal Pro treatments.
 * These are practical processing starting points, not microphone emulations.
 */
internal object StudioVocalPresetLibrary {
    fun controlsFor(profile: StudioVocalProfile): StudioVocalControls = when (profile) {
        StudioVocalProfile.CLEAN -> StudioVocalControls(
            warmth = 0.05f, presence = 0.02f, deEss = 0.08f,
            compression = 0.25f, ambience = 0.005f, outputGain = 1.0f,
            air = 0.04f, stereoWidth = 1.0f, limiterCeiling = 0.94f, noiseReduction = 0.12f
        )
        StudioVocalProfile.WARM_CONDENSER -> StudioVocalControls(
            warmth = 0.42f, presence = 0.05f, deEss = 0.12f,
            compression = 0.55f, ambience = 0.035f, outputGain = 1.04f,
            air = 0.10f, stereoWidth = 1.04f, limiterCeiling = 0.92f, noiseReduction = 0.08f
        )
        StudioVocalProfile.BRIGHT_POP -> StudioVocalControls(
            warmth = -0.08f, presence = 0.12f, deEss = 0.20f,
            compression = 0.85f, ambience = 0.045f, outputGain = 1.02f,
            air = 0.22f, stereoWidth = 1.12f, limiterCeiling = 0.90f, noiseReduction = 0.10f
        )
        StudioVocalProfile.PODCAST -> StudioVocalControls(
            warmth = 0.12f, presence = 0.08f, deEss = 0.16f,
            compression = 1.0f, ambience = 0f, outputGain = 1.06f,
            air = 0.06f, stereoWidth = 1.0f, limiterCeiling = 0.90f, noiseReduction = 0.22f
        )
        StudioVocalProfile.CLEAN_NARRATION -> StudioVocalControls(
            warmth = 0.08f, presence = 0.04f, deEss = 0.10f,
            compression = 0.35f, ambience = 0f, outputGain = 1.02f,
            air = 0.05f, stereoWidth = 1.0f, limiterCeiling = 0.94f, noiseReduction = 0.16f
        )
        StudioVocalProfile.LEAD_VOCAL -> StudioVocalControls(
            warmth = 0.18f, presence = 0.09f, deEss = 0.18f,
            compression = 0.75f, ambience = 0.055f, outputGain = 1.03f,
            air = 0.14f, stereoWidth = 1.08f, limiterCeiling = 0.91f, noiseReduction = 0.08f
        )
    }.normalized()
}
