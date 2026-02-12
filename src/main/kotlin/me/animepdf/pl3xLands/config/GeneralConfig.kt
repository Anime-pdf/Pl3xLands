package me.animepdf.pl3xLands.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
data class GeneralConfig(
    @Comment("Settings for connecting to external APIs or webhooks.")
    val api: ApiConfig = ApiConfig(),

    @Comment("Controls how land data is saved to the disk.")
    val storage: StorageConfig = StorageConfig(),

    @Comment("Settings for the embedded web server/editor interface.")
    val editor: EditorConfig = EditorConfig(),

    @Comment("Constraints and limits for regions/claims.")
    val validation: ValidationConfig = ValidationConfig(),

    @Comment("Visual settings for how regions appear on the map.")
    val rendering: RenderingConfig = RenderingConfig()
)

// ==================== AUTH ====================

@ConfigSerializable
data class AuthBearerConfig(
    @Comment("Token for BEARER auth.")
    val token: String = "thisisverysecrettoken"
)

@ConfigSerializable
data class AuthBasicConfig(
    @Comment("Username for BASIC auth.")
    val username: String = "admin",
    @Comment("Password for BASIC auth.")
    val password: String = "supersecretpassword"
)

@ConfigSerializable
data class AuthKeyConfig(
    @Comment("Header name for API Key auth.")
    val header: String = "X-API-Key",
    @Comment("Value for API Key auth.")
    val value: String = "api-key"
)

// ==================== API ====================

enum class ApiAuthType {
    BEARER, BASIC, KEY
}

@ConfigSerializable
data class ApiAuthConfig(
    @Comment("Enable authentication for API requests.")
    val enable: Boolean = false,
    @Comment("Authentication method. Valid values: BEARER, BASIC, KEY")
    val type: ApiAuthType = ApiAuthType.BEARER,
    val bearer: AuthBearerConfig = AuthBearerConfig(),
    val basic: AuthBasicConfig = AuthBasicConfig(),
    val key: AuthKeyConfig = AuthKeyConfig()
)

@ConfigSerializable
data class ApiConfig(
    @Comment("Enable or disable the API module completely.")
    val enable: Boolean = false,
    @Comment("Authentication settings for the API.")
    val auth: ApiAuthConfig = ApiAuthConfig(),
    @Comment("The endpoint URL for the API.")
    val url: String = "http://localhost:8000",
    @Comment("How often (in seconds) to refresh data from the API.")
    val refreshInterval: Long = 60,
    @Comment("Connection timeout in seconds.")
    val timeoutInterval: Long = 30,
    @Comment("How many times to retry a failed request.")
    val retryAttempts: Int = 3,
    @Comment("Delay (in seconds) between retry attempts.")
    val retryDelay: Long = 5
)

// ==================== STORAGE ====================

enum class StorageType {
    JSON, BINARY
}

@ConfigSerializable
data class StorageConfig(
    @Comment("Data storage format. Valid values: JSON, BINARY (Faster, smaller).")
    val type: StorageType = StorageType.BINARY,
    @Comment("The name of the database file.")
    val filename: String = "lands.bin",
    @Comment("Automatically save data at a set interval.")
    val autoSave: Boolean = true,
    @Comment("Interval in seconds between auto-saves.")
    val autoSaveInterval: Long = 300
)

// ==================== EDITOR ====================

@ConfigSerializable
data class CorsConfig(
    @Comment("Enable Cross-Origin Resource Sharing (CORS).")
    val enabled: Boolean = true,
    @Comment("List of allowed origins for CORS.")
    val allowedOrigins: List<String> = listOf("http://localhost:8181"),
    @Comment("Allow credentials (cookies, auth headers) in CORS requests.")
    val allowCredentials: Boolean = true
)

enum class EditorAuthType {
    BEARER, BASIC
}

@ConfigSerializable
data class EditorAuthConfig(
    @Comment("Enable authentication for the editor.")
    val enable: Boolean = true,
    @Comment("Authentication method. Valid values: BEARER, BASIC")
    val type: EditorAuthType = EditorAuthType.BASIC,
    val bearer: AuthBearerConfig = AuthBearerConfig(),
    val basic: AuthBasicConfig = AuthBasicConfig(),
    @Comment("Session timeout in seconds (Default: 3600 = 1 hour).")
    val sessionTimeout: Int = 3600
)

@ConfigSerializable
data class EditorConfig(
    @Comment("Enable the web editor.")
    val enable: Boolean = true,
    @Comment("Authentication settings for the web editor.")
    val auth: EditorAuthConfig = EditorAuthConfig(),
    @Comment("CORS settings for the web editor.")
    val cors: CorsConfig = CorsConfig(),
    @Comment("The port the web editor server will listen on.")
    val port: Int = 8181,
    @Comment("The interface IP to bind to (0.0.0.0 binds to all).")
    val bindAddress: String = "0.0.0.0",
    @Comment("The public URL where the map is hosted.")
    val mapUrl: String = "http://localhost:8080",
    @Comment("Request timeout in seconds.")
    val timeoutInterval: Long = 30
)

// ==================== VALIDATION ====================

@ConfigSerializable
data class ValidationConfig(
    @Comment("Enable validation logic.")
    val enabled: Boolean = true,
    @Comment("Maximum allowed characters for region names.")
    val maxRegionNameLength: Int = 64,
    @Comment("Maximum allowed characters for descriptions.")
    val maxDescriptionLength: Int = 512,
    @Comment("Maximum allowed characters for contact info.")
    val maxContactLength: Int = 128,
    @Comment("Maximum allowed characters for owner names.")
    val maxOwnerLength: Int = 64,
    @Comment("Maximum amount of chunks a single region can contain.")
    val maxChunksPerRegion: Int = 10000,
    @Comment("Minimum amount of chunks required to create a region.")
    val minChunksPerRegion: Int = 1,
    @Comment("List of worlds where claims are allowed. Empty = all.")
    val allowedWorlds: List<String> = emptyList(),
)

// ==================== RENDERING ====================

enum class ColorScheme {
    PLAYER_HASH,
    FIXED_COLOR
}

@ConfigSerializable
data class RenderingConfig(
    @Comment("How to color regions. Valid: PLAYER_HASH, FIXED_COLOR")
    val colorScheme: ColorScheme = ColorScheme.PLAYER_HASH,
    @Comment("Hex color code if colorScheme is FIXED_COLOR.")
    val fixedColor: String = "#3388ff",
    @Comment("Width of the region border line (pixels).")
    val strokeWidth: Int = 3,
    @Comment("Opacity of the border line (0-255).")
    val strokeOpacity: Int = 255,
    @Comment("Opacity of the region fill color (0-255).")
    val fillOpacity: Int = 100,
    @Comment("Show tooltips when hovering over a region.")
    val enableTooltips: Boolean = true,
    @Comment("Show popups when clicking a region.")
    val enablePopups: Boolean = true,
    @Comment("If true, the tooltip follows the mouse.")
    val tooltipSticky: Boolean = true,
    @Comment("The display name of the layer in the map menu.")
    val layerName: String = "Lands",
    @Comment("How often (in seconds) to redraw the layer.")
    val layerUpdateInterval: Int = 30,
    @Comment("Order in the layer list (Lower is higher).")
    val layerPriority: Int = 10,
    @Comment("Z-Index stacking order (Higher overlaps lower).")
    val layerZIndex: Int = 10,
    @Comment("If true, the layer is hidden by default.")
    val layerDefaultHidden: Boolean = false,
    @Comment("Show the layer in the map controls menu.")
    val layerShowControls: Boolean = true
)