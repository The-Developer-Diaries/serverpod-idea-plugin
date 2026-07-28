package dev.serverpod.idea.wizard

/**
 * Serverpod requires a valid Dart package name, which is stricter than what the
 * New Project wizard accepts as a project name.
 */
object ServerpodNaming {

    private val PACKAGE_NAME_PATTERN = Regex("[a-z][a-z0-9_]*")

    private val SERVERPOD_SUFFIXES = listOf("_server", "_client", "_flutter")

    private val DART_RESERVED_WORDS = setOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "covariant", "default", "deferred", "do", "dynamic", "else", "enum", "export",
        "extends", "extension", "external", "factory", "false", "final", "finally", "for", "get",
        "hide", "if", "implements", "import", "in", "interface", "is", "late", "library", "mixin",
        "new", "null", "on", "operator", "part", "required", "rethrow", "return", "set", "show",
        "static", "super", "switch", "sync", "this", "throw", "true", "try", "typedef", "var",
        "void", "while", "with", "yield",
    )

    /** Turns an arbitrary project name such as "My App" into "my_app". */
    fun suggestPackageName(projectName: String): String {
        val sanitized = projectName.trim()
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')

        return when {
            sanitized.isEmpty() -> "my_app"
            sanitized.first().isDigit() -> "app_$sanitized"
            else -> sanitized
        }
    }

    /** Returns a human-readable problem with [value], or null when it is usable. */
    fun packageNameError(value: String): String? {
        val name = value.trim()
        return when {
            name.isEmpty() -> "Package name is required."
            !PACKAGE_NAME_PATTERN.matches(name) ->
                "Use lowercase letters, digits, and underscores, starting with a letter."

            name in DART_RESERVED_WORDS -> "'$name' is a Dart reserved word."
            else -> SERVERPOD_SUFFIXES.firstOrNull { name.endsWith(it) }
                ?.let { "Drop the '$it' suffix; Serverpod adds it." }
        }
    }
}
