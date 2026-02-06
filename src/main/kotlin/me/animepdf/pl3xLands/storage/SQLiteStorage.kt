package me.animepdf.pl3xLands.storage

import com.google.gson.Gson
import me.animepdf.pl3xLands.dto.RegionManifest
import java.sql.DriverManager

// TODO: make it normal
class SQLiteStorage(private val dbPath: String, private val gson: Gson) : RegionStorage {

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

    override fun save(manifest: RegionManifest) {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            connection.autoCommit = false
            try {
                val stmt = connection.prepareStatement("INSERT OR REPLACE INTO regions (id, data, hash) VALUES (?, ?, ?)")

                stmt.setString(1, "GLOBAL_MANIFEST")
                stmt.setString(2, gson.toJson(manifest))
                stmt.setString(3, manifest.hash)
                stmt.executeUpdate()
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }

    }

    override fun load(): RegionManifest? {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { connection ->
            val stmt = connection.createStatement().executeQuery("SELECT data FROM regions WHERE id = 'GLOBAL_MANIFEST'")
            while (stmt.next()) {
                return gson.fromJson(stmt.getString("data"), RegionManifest::class.java)
            }
        }
        return null
    }
}