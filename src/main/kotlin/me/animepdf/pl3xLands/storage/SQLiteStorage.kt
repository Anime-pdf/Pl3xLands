package me.animepdf.pl3xLands.storage

import com.google.gson.Gson
import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.dto.RegionManifest
import java.sql.DriverManager
import kotlin.collections.plusAssign

// TODO: make it normal
class SQLiteStorage(private val dbPath: String, private val gson: Gson) : RegionStorage {

    var manifest: RegionManifest? = null

    init {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS regions (
                    id TEXT PRIMARY KEY,
                    data TEXT NOT NULL,
                    hash TEXT NOT NULL
                )
            """.trimIndent()
            )
        }
    }

    override fun save(manifest: RegionManifest?) {
        if (manifest != null) this.manifest = manifest
        if(this.manifest == null) return

        val oldManifest = this.manifest

        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.autoCommit = false
            try {
                this.manifest = manifest

                val stmt =
                    connection.prepareStatement("INSERT OR REPLACE INTO regions (id, data, hash) VALUES (?, ?, ?)")

                stmt.setString(1, "GLOBAL_MANIFEST")
                stmt.setString(2, gson.toJson(this.manifest))
                stmt.setString(3, this.manifest?.hash)
                stmt.executeUpdate()
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                this.manifest = oldManifest
                throw e
            }
        }

    }

    override fun load(fresh: Boolean): RegionManifest? {
        if (!fresh && manifest != null) return manifest

        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            val stmt =
                connection.createStatement().executeQuery("SELECT data FROM regions WHERE id = 'GLOBAL_MANIFEST'")
            while (stmt.next()) {
                return gson.fromJson(stmt.getString("data"), RegionManifest::class.java)
            }
        }
        return null
    }

    override fun addRegions(regions: Array<Region>, save: Boolean) {
        if (manifest == null) load()
        manifest?.regions += regions
        if (save && manifest != null)
            save(manifest!!)
    }

    override fun addChunksToRegion(regionId: String, chunks: Array<Long>, save: Boolean) {
        if (manifest == null) load()
        manifest?.regions?.find { it.id == regionId }?.chunks += chunks
        if (save && manifest != null)
            save(manifest!!)
    }
}