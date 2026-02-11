package me.animepdf.pl3xLands.storage

import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.dto.RegionManifest
import me.animepdf.pl3xLands.validation.RegionValidator
import me.animepdf.pl3xLands.validation.ValidationResult
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.logging.Logger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Format:
 * - Header: Magic number (4 bytes) + Version (2 bytes) + Timestamp (8 bytes) + Hash (32 bytes)
 * - Regions: Count (4 bytes) + Region entries
 * - Each Region: ID length (2 bytes) + ID (UTF-8) + Fields + Chunk count (4 bytes) + Chunks (8 bytes each)
 *
 * All data is GZIP compressed.
 */
class BinaryFileStorage(
    private val file: File,
    private val logger: Logger,
    private val enableValidation: Boolean = true
) : RegionStorage {

    private var manifest: RegionManifest? = null

    companion object {
        private const val MAGIC_NUMBER = 0x504C334C // "PL3L"
        private const val FORMAT_VERSION: Short = 1
    }

    override fun save(manifest: RegionManifest?) {
        if (manifest != null) this.manifest = manifest
        if (this.manifest == null) {
            logger.warning("Attempted to save null manifest")
            return
        }

        try {
            file.parentFile?.mkdirs()

            val tempFile = File(file.absolutePath + ".tmp")

            FileOutputStream(tempFile).use { fos ->
                GZIPOutputStream(fos).use { gzip ->
                    DataOutputStream(BufferedOutputStream(gzip)).use { out ->
                        writeBinary(out, this.manifest!!)
                    }
                }
            }

            if (file.exists()) {
                file.delete()
            }
            tempFile.renameTo(file)

            logger.info("Saved ${this.manifest?.regions?.size ?: 0} regions to binary file (${file.length()} bytes)")
        } catch (e: Exception) {
            logger.severe("Failed to save binary file: ${e.message}")
            throw e
        }
    }

    override fun load(fresh: Boolean): RegionManifest? {
        if (!file.exists()) {
            logger.info("No existing binary file found at ${file.name}")
            return null
        }

        if (!fresh && manifest != null) return manifest

        try {
            return FileInputStream(file).use { fis ->
                GZIPInputStream(fis).use { gzip ->
                    DataInputStream(BufferedInputStream(gzip)).use { input ->
                        val loaded = readBinary(input)
                        logger.info("Loaded ${loaded.regions.size} regions from binary file (${file.length()} bytes)")

                        // Validate regions if enabled
                        if (enableValidation) {
                            validateManifest(loaded)
                        }

                        manifest = loaded
                        loaded
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("Failed to load binary file: ${e.message}")
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

    private fun writeBinary(out: DataOutputStream, manifest: RegionManifest) {
        out.writeInt(MAGIC_NUMBER)
        out.writeShort(FORMAT_VERSION.toInt())
        out.writeLong(manifest.timestamp)

        val hashBytes = manifest.hash.toByteArray(StandardCharsets.UTF_8)
        if (hashBytes.size != 64) {
            out.writeInt(hashBytes.size)
            out.write(hashBytes)
        } else {
            out.writeInt(64)
            out.write(hashBytes)
        }

        out.writeInt(manifest.regions.size)

        for (region in manifest.regions) {
            writeRegion(out, region)
        }
    }

    private fun writeRegion(out: DataOutputStream, region: Region) {
        writeString(out, region.id)
        writeString(out, region.name)
        writeString(out, region.description)
        writeString(out, region.owner)
        writeString(out, region.contact)
        writeString(out, region.world)

        out.writeInt(region.chunks.size)
        for (chunk in region.chunks) {
            out.writeLong(chunk)
        }
    }

    private fun writeString(out: DataOutputStream, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        out.writeShort(bytes.size)
        out.write(bytes)
    }

    private fun readBinary(input: DataInputStream): RegionManifest {
        val magic = input.readInt()
        if (magic != MAGIC_NUMBER) {
            throw IOException("Invalid file format: magic number mismatch")
        }

        val version = input.readShort()
        if (version > FORMAT_VERSION) {
            throw IOException("Unsupported format version: $version (max supported: $FORMAT_VERSION)")
        }

        val timestamp = input.readLong()

        val hashLength = input.readInt()
        val hashBytes = ByteArray(hashLength)
        input.readFully(hashBytes)
        val hash = String(hashBytes, StandardCharsets.UTF_8)

        val regionCount = input.readInt()
        val regions = mutableListOf<Region>()

        for (i in 0 until regionCount) {
            regions.add(readRegion(input))
        }

        return RegionManifest(hash, timestamp, regions)
    }

    private fun readRegion(input: DataInputStream): Region {
        val id = readString(input)
        val name = readString(input)
        val description = readString(input)
        val owner = readString(input)
        val contact = readString(input)
        val world = readString(input)

        val chunkCount = input.readInt()
        val chunks = mutableListOf<Long>()
        for (i in 0 until chunkCount) {
            chunks.add(input.readLong())
        }

        return Region(id, name, description, owner, contact, world, chunks)
    }

    private fun readString(input: DataInputStream): String {
        val length = input.readUnsignedShort()
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
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