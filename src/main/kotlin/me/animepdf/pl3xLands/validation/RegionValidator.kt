package me.animepdf.pl3xLands.validation

import me.animepdf.pl3xLands.dto.Region
import org.bukkit.Bukkit

object RegionValidator {

    private const val MAX_NAME_LENGTH = 64
    private const val MAX_DESCRIPTION_LENGTH = 512
    private const val MAX_CONTACT_LENGTH = 128
    private const val MAX_OWNER_LENGTH = 64
    private const val MAX_CHUNKS_PER_REGION = 10000
    private const val MIN_CHUNKS_PER_REGION = 1

    private val ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")

    fun validate(region: Region, allowedWorlds: Set<String> = emptySet()): ValidationResult {
        val errors = mutableListOf<String>()

        validateId(region.id, errors)
        validateName(region.name, errors)
        validateDescription(region.description, errors)
        validateOwner(region.owner, errors)
        validateContact(region.contact, errors)
        validateWorld(region.world, allowedWorlds, errors)
        validateChunks(region.chunks, errors)

        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(errors)
        }
    }

    fun isValidId(id: String): Boolean {
        return id.matches(ID_REGEX)
    }

    private fun validateId(id: String, errors: MutableList<String>) {
        when {
            id.isBlank() -> errors.add("Region ID cannot be blank")
            !id.matches(ID_REGEX) -> errors.add("Region ID must contain only letters, numbers, hyphens, and underscores (1-64 characters)")
        }
    }

    private fun validateName(name: String, errors: MutableList<String>) {
        when {
            name.isBlank() -> errors.add("Region name cannot be blank")
            name.length > MAX_NAME_LENGTH -> errors.add("Region name exceeds maximum length of $MAX_NAME_LENGTH characters")
            containsControlCharacters(name) -> errors.add("Region name contains invalid control characters")
        }
    }

    private fun validateDescription(description: String, errors: MutableList<String>) {
        when {
            description.length > MAX_DESCRIPTION_LENGTH -> errors.add("Description exceeds maximum length of $MAX_DESCRIPTION_LENGTH characters")
            containsControlCharacters(description) -> errors.add("Description contains invalid control characters")
        }
    }

    private fun validateOwner(owner: String, errors: MutableList<String>) {
        when {
            owner.isBlank() -> errors.add("Owner cannot be blank")
            owner.length > MAX_OWNER_LENGTH -> errors.add("Owner name exceeds maximum length of $MAX_OWNER_LENGTH characters")
            containsControlCharacters(owner) -> errors.add("Owner name contains invalid control characters")
        }
    }

    private fun validateContact(contact: String, errors: MutableList<String>) {
        when {
            contact.length > MAX_CONTACT_LENGTH -> errors.add("Contact exceeds maximum length of $MAX_CONTACT_LENGTH characters")
            containsControlCharacters(contact) -> errors.add("Contact contains invalid control characters")
        }
    }

    private fun validateWorld(world: String, allowedWorlds: Set<String>, errors: MutableList<String>) {
        if (world.isBlank()) {
            errors.add("World name cannot be blank")
            return
        }

        if (allowedWorlds.isNotEmpty() && world !in allowedWorlds) {
            errors.add("World '$world' is not in the allowed worlds list: ${allowedWorlds.joinToString()}")
            return
        }

        val validWorlds = Bukkit.getWorlds().map { it.name }.toSet()
        if (world !in validWorlds) {
            errors.add("World '$world' does not exist on this server. Available worlds: ${validWorlds.joinToString()}")
        }
    }

    private fun validateChunks(chunks: List<Long>, errors: MutableList<String>) {
        when {
            chunks.isEmpty() -> errors.add("Region must have at least $MIN_CHUNKS_PER_REGION chunk")
            chunks.size > MAX_CHUNKS_PER_REGION -> errors.add("Region exceeds maximum chunk count of $MAX_CHUNKS_PER_REGION (has ${chunks.size})")
            chunks.size != chunks.distinct().size -> errors.add("Region contains duplicate chunks")
        }
    }

    private fun containsControlCharacters(text: String): Boolean {
        return text.any { char ->
            char.isISOControl() && char != '\n' && char != '\r' && char != '\t'
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val errors: List<String>) : ValidationResult()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getErrorsOrEmpty(): List<String> {
        return when (this) {
            is Success -> emptyList()
            is Failure -> errors
        }
    }
}