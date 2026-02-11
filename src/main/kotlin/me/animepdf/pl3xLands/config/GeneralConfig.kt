package me.animepdf.pl3xLands.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class GeneralConfig(
    val api: ApiConfig = ApiConfig(),
    val storage: StorageConfig = StorageConfig(),
    val editor: EditorConfig = EditorConfig(),
    val validation: ValidationConfig = ValidationConfig(),
    val rendering: RenderingConfig = RenderingConfig()
)

// ==================== AUTH ====================

@ConfigSerializable
data class AuthBearerConfig(
    val token: String = "api-token"
)

@ConfigSerializable
data class AuthBasicConfig(
    val username: String = "user",
    val password: String = "pass"
)

@ConfigSerializable
data class AuthKeyConfig(
    val header: String = "X-API-Key",
    val value: String = "api-key"
)

// ==================== API ====================

enum class ApiAuthType {
    BEARER, BASIC, KEY
}

@ConfigSerializable
data class ApiAuthConfig(
    val enable: Boolean = false,
    val type: ApiAuthType = ApiAuthType.BEARER,
    val bearer: AuthBearerConfig = AuthBearerConfig(),
    val basic: AuthBasicConfig = AuthBasicConfig(),
    val key: AuthKeyConfig = AuthKeyConfig()
)

@ConfigSerializable
data class ApiConfig(
    val enable: Boolean = true,
    val auth: ApiAuthConfig = ApiAuthConfig(),
    val url: String = "http://localhost:8000",
    val refreshInterval: Long = 60,
    val timeoutInterval: Long = 30,
    val retryAttempts: Int = 3,
    val retryDelay: Long = 5
)

// ==================== STORAGE ====================

enum class StorageType {
    JSON, BINARY
}

@ConfigSerializable
data class StorageConfig(
    val type: StorageType = StorageType.JSON,
    val filename: String = "lands.json",
    val autoSave: Boolean = true,
    val autoSaveInterval: Long = 300
)

// ==================== EDITOR ====================

@ConfigSerializable
data class CorsConfig(
    val enabled: Boolean = true,
    val allowedOrigins: List<String> = listOf("http://localhost:8181"),
    val allowCredentials: Boolean = true
)

enum class EditorAuthType {
    BEARER, BASIC
}

@ConfigSerializable
data class EditorAuthConfig(
    val enable: Boolean = false,
    val type: EditorAuthType = EditorAuthType.BASIC,
    val bearer: AuthBearerConfig = AuthBearerConfig(),
    val basic: AuthBasicConfig = AuthBasicConfig(),
    val sessionTimeout: Int = 3600
)

@ConfigSerializable
data class EditorConfig(
    val enable: Boolean = true,
    val auth: EditorAuthConfig = EditorAuthConfig(),
    val cors: CorsConfig = CorsConfig(),
    val port: Int = 8181,
    val bindAddress: String = "0.0.0.0",
    val mapUrl: String = "http://localhost:8080",
    val timeoutInterval: Long = 30
)

// ==================== VALIDATION ====================

@ConfigSerializable
data class ValidationConfig(
    val enabled: Boolean = true,
    val maxRegionNameLength: Int = 64,
    val maxDescriptionLength: Int = 512,
    val maxContactLength: Int = 128,
    val maxOwnerLength: Int = 64,
    val maxChunksPerRegion: Int = 10000,
    val minChunksPerRegion: Int = 1,
    val allowedWorlds: List<String> = emptyList(),
)

// ==================== RENDERING ====================

enum class ColorScheme {
    PLAYER_HASH,
    FIXED_COLOR
}

@ConfigSerializable
data class RenderingConfig(
    val colorScheme: ColorScheme = ColorScheme.PLAYER_HASH,
    val fixedColor: String = "#3388ff",
    val strokeWidth: Int = 3,
    val strokeOpacity: Int = 255,
    val fillOpacity: Int = 100,
    val enableTooltips: Boolean = true,
    val enablePopups: Boolean = true,
    val tooltipSticky: Boolean = true,
    val layerName: String = "Lands",
    val layerUpdateInterval: Int = 30,
    val layerPriority: Int = 10,
    val layerZIndex: Int = 10,
    val layerDefaultHidden: Boolean = false,
    val layerShowControls: Boolean = true
)
