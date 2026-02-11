package me.animepdf.pl3xLands.storage

import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.dto.RegionManifest

interface RegionStorage {
    /**
     * Save the manifest to storage.
     * If manifest is null, saves the current in-memory manifest.
     */
    fun save(manifest: RegionManifest? = null)

    /**
     * Load the manifest from storage.
     * @param fresh If true, ignore cache and reload from disk/database
     */
    fun load(fresh: Boolean = false): RegionManifest?

    /**
     * Add a new region.
     * @return Success or Failure with error message
     */
    fun addRegion(region: Region): StorageResult

    /**
     * Update an existing region.
     * @return Success or Failure with error message
     */
    fun updateRegion(region: Region): StorageResult

    /**
     * Delete a region by ID.
     * @return Success or Failure with error message
     */
    fun deleteRegion(regionId: String): StorageResult

    /**
     * Get a single region by ID.
     */
    fun getRegion(regionId: String): Region?

    /**
     * Get all regions.
     */
    fun getAllRegions(): List<Region>
}

/**
 * Result of a storage operation.
 */
sealed class StorageResult {
    object Success : StorageResult()
    data class Failure(val error: String) : StorageResult()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
}