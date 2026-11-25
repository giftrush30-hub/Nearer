package com.nearer.app

/** Licence-tracked local GLB asset entry used by the offline 3D scene workspace. */
internal data class Studio3DAsset(
    val id: String,
    val localUri: String,
    val sha256: String,
    val fileSizeBytes: Long,
    val licenceName: String,
    val licenceUrl: String,
    val animationNames: List<String> = emptyList(),
    val skeletonId: String? = null
)

internal data class Studio3DVector3(val x: Float, val y: Float, val z: Float)
internal data class Studio3DTransform(
    val position: Studio3DVector3 = Studio3DVector3(0f, 0f, 0f),
    val rotationDegrees: Studio3DVector3 = Studio3DVector3(0f, 0f, 0f),
    val scale: Studio3DVector3 = Studio3DVector3(1f, 1f, 1f)
)

internal data class Studio3DActor(
    val id: String,
    val assetId: String,
    val transform: Studio3DTransform = Studio3DTransform(),
    val activeAnimation: String? = null
)

internal data class Studio3DScene(
    val id: String,
    val name: String,
    val actors: List<Studio3DActor>,
    val cameraPosition: Studio3DVector3 = Studio3DVector3(0f, 1.5f, 5f),
    val cameraTarget: Studio3DVector3 = Studio3DVector3(0f, 1f, 0f),
    val lightIntensity: Float = 100_000f,
    val outputWidth: Int = 1280,
    val outputHeight: Int = 720
)

internal data class Studio3DValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Safety, licence, reference and compatibility checks before a GLB is handed to a renderer. */
internal object Studio3DSceneValidator {
    const val MAX_ACTORS = 100
    const val MAX_ASSET_BYTES = 512L * 1024L * 1024L

    fun validate(scene: Studio3DScene, assets: List<Studio3DAsset>): Studio3DValidation {
        val errors = mutableListOf<String>()
        if (scene.id.isBlank() || scene.id.length > 128) errors += "Scene ID must contain 1–128 characters."
        if (scene.name.isBlank() || scene.name.length > 80) errors += "Scene name must contain 1–80 characters."
        if (scene.actors.size > MAX_ACTORS) errors += "Scene exceeds the 100-actor safety limit."
        if (scene.outputWidth !in 64..3840 || scene.outputHeight !in 64..2160) errors += "Scene output dimensions are outside supported bounds."
        if (!scene.lightIntensity.isFinite() || scene.lightIntensity !in 0f..200_000f) errors += "Scene light intensity is invalid."
        if (!isFinite(scene.cameraPosition) || !isFinite(scene.cameraTarget)) errors += "Camera coordinates must be finite."

        val assetById = assets.associateBy { it.id }
        if (assetById.size != assets.size) errors += "Asset IDs must be unique."
        assets.forEach { asset ->
            if (asset.id.isBlank() || asset.id.length > 128) errors += "Asset ID must contain 1–128 characters."
            val uriScheme = runCatching { java.net.URI(asset.localUri).scheme?.lowercase() }.getOrNull()
            if (uriScheme !in setOf("content", "file", "android.resource")) errors += "${asset.id}: GLB source must be local."
            if (!asset.localUri.endsWith(".glb", ignoreCase = true)) errors += "${asset.id}: only GLB assets are accepted by this manifest."
            if (!asset.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) errors += "${asset.id}: SHA-256 must contain 64 hexadecimal characters."
            if (asset.fileSizeBytes !in 1L..MAX_ASSET_BYTES) errors += "${asset.id}: asset size exceeds the 512 MB safety limit."
            if (asset.licenceName.isBlank() || asset.licenceUrl.isBlank()) errors += "${asset.id}: licence name and provenance URL are required."
            if (asset.animationNames.size > 256 || asset.animationNames.any { it.isBlank() || it.length > 80 }) {
                errors += "${asset.id}: animation names must be valid and limited to 256 entries."
            }
        }
        scene.actors.forEach { actor ->
            val asset = assetById[actor.assetId]
            if (actor.id.isBlank() || actor.id.length > 128) errors += "Actor ID must contain 1–128 characters."
            if (asset == null) {
                errors += "${actor.id}: referenced GLB asset is missing."
            } else if (actor.activeAnimation != null && actor.activeAnimation !in asset.animationNames) {
                errors += "${actor.id}: animation is not included in the referenced GLB asset."
            }
            if (!isFinite(actor.transform.position) || !isFinite(actor.transform.rotationDegrees) ||
                !isFinite(actor.transform.scale) || actor.transform.scale.x <= 0f ||
                actor.transform.scale.y <= 0f || actor.transform.scale.z <= 0f) {
                errors += "${actor.id}: transform values must be finite and scale must be positive."
            }
        }
        if (scene.actors.map { it.id }.toSet().size != scene.actors.size) errors += "Actor IDs must be unique."
        return Studio3DValidation(errors.distinct())
    }

    private fun isFinite(value: Studio3DVector3) =
        value.x.isFinite() && value.y.isFinite() && value.z.isFinite()
}
