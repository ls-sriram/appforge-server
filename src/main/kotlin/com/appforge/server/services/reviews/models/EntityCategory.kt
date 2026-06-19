package com.appforge.server.services.reviews.models

/**
 * Generic entity type discriminator.
 * Frontend applications define their own entity types (e.g. "document", "image", "record").
 * No domain-specific values are enforced — any non-empty string is valid.
 */
data class EntityCategory(val value: String) {
    companion object {
        fun fromWire(wire: String): EntityCategory =
            EntityCategory(wire.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("EntityCategory must not be empty"))
    }
}
