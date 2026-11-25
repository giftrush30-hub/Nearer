package com.nearer.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Studio3DSceneModelTest {
    private val asset = Studio3DAsset(
        id = "hero",
        localUri = "content://assets/hero.glb",
        sha256 = "a".repeat(64),
        fileSizeBytes = 12_000_000L,
        licenceName = "CC BY 4.0",
        licenceUrl = "https://creativecommons.org/licenses/by/4.0/",
        animationNames = listOf("Idle", "Run", "Jump"),
        skeletonId = "humanoid-v1"
    )

    @Test fun acceptsLicensedLocalGlbAndKnownAnimation() {
        val scene = Studio3DScene("scene-1", "Forest scene", listOf(
            Studio3DActor("hero-1", "hero", activeAnimation = "Run")
        ))
        assertTrue(Studio3DSceneValidator.validate(scene, listOf(asset)).isValid)
    }

    @Test fun rejectsNetworkAssetsMissingLicenceAndInvalidHash() {
        val invalid = asset.copy(
            localUri = "https://example.invalid/hero.glb",
            sha256 = "bad",
            licenceName = "",
            licenceUrl = ""
        )
        val result = Studio3DSceneValidator.validate(
            Studio3DScene("scene", "Scene", listOf(Studio3DActor("actor", "hero"))),
            listOf(invalid)
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("local") })
        assertTrue(result.errors.any { it.contains("SHA-256") })
        assertTrue(result.errors.any { it.contains("licence") })
    }

    @Test fun rejectsMissingAssetUnknownAnimationAndInvalidTransform() {
        val scene = Studio3DScene("scene", "Scene", listOf(
            Studio3DActor("a", "missing"),
            Studio3DActor("b", "hero",
                transform = Studio3DTransform(scale = Studio3DVector3(1f, 0f, 1f)),
                activeAnimation = "Fly")
        ))
        val result = Studio3DSceneValidator.validate(scene, listOf(asset))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("asset is missing") })
        assertTrue(result.errors.any { it.contains("animation is not included") })
        assertTrue(result.errors.any { it.contains("scale must be positive") })
    }

    @Test fun enforcesSceneAndActorLimitsAndUniqueIds() {
        val duplicate = Studio3DScene("scene", "Scene", listOf(
            Studio3DActor("same", "hero"), Studio3DActor("same", "hero")
        ))
        assertFalse(Studio3DSceneValidator.validate(duplicate, listOf(asset)).isValid)
        val tooMany = duplicate.copy(actors = (0..Studio3DSceneValidator.MAX_ACTORS).map {
            Studio3DActor("actor-$it", "hero")
        })
        assertFalse(Studio3DSceneValidator.validate(tooMany, listOf(asset)).isValid)
    }
}
