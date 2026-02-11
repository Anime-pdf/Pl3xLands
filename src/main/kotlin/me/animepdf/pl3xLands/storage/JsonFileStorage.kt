package me.animepdf.pl3xLands.storage

import com.google.gson.Gson
import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.dto.RegionManifest
import me.animepdf.pl3xLands.validation.RegionValidator
import me.animepdf.pl3xLands.validation.ValidationResult
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.logging.Logger

class JsonFileStorage(
    private val file: File,
    private val gson: Gson,
    private val logger: Logger,
    private val enableValidation: Boolean = true
) : RegionStorage {

    var manifest: RegionManifest? = null

    override fun save(manifest: RegionManifest?) {
        if (manifest != null) this.manifest = manifest
        if (this.manifest == null) {
            logger.warning("Attempted to save null manifest")
            return
        }

        try {
            file.parentFile?.mkdirs()

            FileWriter(file).use { writer ->
                gson.toJson(this.manifest, writer)
            }
            logger.info("Saved ${this.manifest?.regions?.size ?: 0} regions to ${file.name}")
        } catch (e: Exception) {
            logger.severe("Failed to save regions to ${file.name}: ${e.message}")
            throw e
        }
    }

    override fun load(fresh: Boolean): RegionManifest? {
        if (!file.exists()) {
            logger.info("No existing data file found at ${file.name}")
            createEmpty()
            return null
        }

        if (!fresh && manifest != null) return manifest

        try {
            return FileReader(file).use { reader ->
                val loaded = gson.fromJson(reader, RegionManifest::class.java)
                logger.info("Loaded ${loaded.regions.size} regions from ${file.name}")

                if (enableValidation) {
                    validateManifest(loaded)
                }

                manifest = loaded
                loaded
            }
        } catch (e: Exception) {
            logger.severe("Failed to load regions from ${file.name}: ${e.message}")
            throw e
        }
    }

    override fun addRegion(region: Region): StorageResult {
        if (manifest == null) load()

        if (manifest?.regions?.any { it.id == region.id } == true) {
            return StorageResult.Failure("Region with ID '${region.id}' already exists")
        }

        if (enableValidation) {
            when (val result = RegionValidator.validate(region)) {
                is ValidationResult.Success -> {}
                is ValidationResult.Failure -> {
                    return StorageResult.Failure(
                        "Validation failed: ${result.errors.joinToString("; ")}"
                    )
                }
            }
        }

        manifest?.regions?.add(region)
        logger.info("Added region '${region.id}' (${region.chunks.size} chunks)")
        return StorageResult.Success
    }

    override fun updateRegion(region: Region): StorageResult {
        if (manifest == null) load()

        val existingIndex = manifest?.regions?.indexOfFirst { it.id == region.id }
        if (existingIndex == null || existingIndex == -1) {
            return StorageResult.Failure("Region with ID '${region.id}' not found")
        }

        if (enableValidation) {
            when (val result = RegionValidator.validate(region)) {
                is ValidationResult.Success -> {}
                is ValidationResult.Failure -> {
                    return StorageResult.Failure(
                        "Validation failed: ${result.errors.joinToString("; ")}"
                    )
                }
            }
        }

        manifest?.regions?.set(existingIndex, region)
        logger.info("Updated region '${region.id}'")
        return StorageResult.Success
    }

    override fun deleteRegion(regionId: String): StorageResult {
        if (manifest == null) load()

        val removed = manifest?.regions?.removeIf { it.id == regionId } ?: false

        return if (removed) {
            logger.info("Deleted region '$regionId'")
            StorageResult.Success
        } else {
            StorageResult.Failure("Region with ID '$regionId' not found")
        }
    }

    override fun getRegion(regionId: String): Region? {
        if (manifest == null) load()
        return manifest?.regions?.find { it.id == regionId }
    }

    override fun getAllRegions(): List<Region> {
        if (manifest == null) load()
        return manifest?.regions?.toList() ?: emptyList()
    }

    private fun createEmpty() {
        manifest = RegionManifest("invalid", -1, arrayListOf())
        save(manifest)
    }

    private fun validateManifest(manifest: RegionManifest) {
        var validCount = 0
        var invalidCount = 0

        manifest.regions.forEach { region ->
            when (val result = RegionValidator.validate(region)) {
                is ValidationResult.Success -> validCount++
                is ValidationResult.Failure -> {
                    logger.warning("Invalid region '${region.id}': ${result.errors.joinToString("; ")}")
                    invalidCount++
                }
            }
        }

        if (invalidCount > 0) {
            logger.warning("Found $invalidCount invalid regions out of ${manifest.regions.size} total")
        }
    }
}