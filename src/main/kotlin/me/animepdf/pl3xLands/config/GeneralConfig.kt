package me.animepdf.pl3xLands.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class GeneralConfig(
    val api: ApiConfig = ApiConfig(),
    val storage: StorageConfig = StorageConfig()
)

/// API
enum class ApiAuthType {
    BEARER, BASIC, KEY
}
@ConfigSerializable
data class ApiAuthBearerConfig(
    val token: String = "api-token"
)
@ConfigSerializable
data class ApiAuthBasicConfig(
    val username: String = "user",
    val password: String = "pass"
)
@ConfigSerializable
data class ApiAuthKeyConfig(
    val header: String = "X-API-Key",
    val value: String = "api-key"
)
@ConfigSerializable
data class ApiAuthConfig(
    val enable: Boolean = false,
    val type: ApiAuthType = ApiAuthType.BEARER,
    val bearer: ApiAuthBearerConfig = ApiAuthBearerConfig(),
    val basic: ApiAuthBasicConfig = ApiAuthBasicConfig(),
    val key: ApiAuthKeyConfig = ApiAuthKeyConfig()
)
@ConfigSerializable
data class ApiConfig(
    val enable: Boolean = true,
    val url: String = "https://localhost:8000",
    val auth: ApiAuthConfig = ApiAuthConfig(),
    val refreshInterval: Long = 60,
    val timeoutInterval: Long = 30
)

/// STORAGE
enum class StorageType {
    SQLITE, JSON
}
@ConfigSerializable
data class StorageConfig(
    val type: StorageType = StorageType.SQLITE,
    val filename: String = "lands.db"
)
