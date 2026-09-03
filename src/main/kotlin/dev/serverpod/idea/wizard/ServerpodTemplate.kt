package dev.serverpod.idea.wizard

import dev.serverpod.idea.cli.ServerpodFeature
import dev.serverpod.idea.cli.ServerpodVersion

/**
 * A `serverpod create --template` value.
 *
 * Serverpod 4 reshaped this list: `mini` is gone, `fullstack` is the new default,
 * and `server` narrowed from "everything" to "no Flutter app". The offered
 * templates therefore depend on the CLI that will run, not on this plugin.
 */
enum class ServerpodTemplate(
    val cliValue: String,
    val displayName: String,
) {

    FULLSTACK("fullstack", "Full stack"),
    SERVER("server", "Server"),
    MINI("mini", "Mini"),
    MODULE("module", "Module"),
    ;

    /** A module is a library for other projects, so it never gets a database. */
    val supportsDatabase: Boolean get() = this != MODULE

    override fun toString(): String = displayName

    /** Whether [version] of the CLI still accepts this template. */
    fun isAvailableOn(version: ServerpodVersion?): Boolean = when (this) {
        FULLSTACK -> ServerpodFeature.FULLSTACK_TEMPLATE.isSupportedBy(version)
        MINI -> ServerpodFeature.MINI_TEMPLATE.isSupportedBy(version)
        SERVER, MODULE -> true
    }

    /**
     * What this template produces on [version]. `server` needs two descriptions
     * because Serverpod 4 kept the name and changed the meaning.
     */
    fun descriptionFor(version: ServerpodVersion?): String = when (this) {
        FULLSTACK -> "Server and a companion Flutter app."
        SERVER ->
            if (ServerpodFeature.FULLSTACK_TEMPLATE.isSupportedBy(version)) {
                "Server on its own, with no Flutter app."
            } else {
                "Server, client, and Flutter app with PostgreSQL and Redis."
            }

        MINI -> "Server, client, and Flutter app with no database."
        MODULE -> "A reusable module for other Serverpod projects."
    }

    companion object {

        fun availableFor(version: ServerpodVersion?): List<ServerpodTemplate> =
            entries.filter { it.isAvailableOn(version) }

        /** Matches the CLI's own default, which moved to `fullstack` in Serverpod 4. */
        fun defaultFor(version: ServerpodVersion?): ServerpodTemplate =
            if (ServerpodFeature.FULLSTACK_TEMPLATE.isSupportedBy(version)) FULLSTACK else SERVER

        /**
         * Resolves a remembered choice, falling back to the default when the CLI
         * has since dropped that template.
         */
        fun restore(cliValue: String?, version: ServerpodVersion?): ServerpodTemplate =
            entries.firstOrNull { it.cliValue == cliValue && it.isAvailableOn(version) }
                ?: defaultFor(version)
    }
}
