package dev.serverpod.idea.cli

/**
 * A Serverpod CLI version, so the plugin can offer the surface the installed CLI
 * actually has rather than the one it was written against.
 */
data class ServerpodVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** The `rc.1` of `4.0.0-rc.1`, or null for a stable release. */
    val preRelease: String? = null,
) : Comparable<ServerpodVersion> {

    /**
     * Whether this version has the surface introduced in [release].
     *
     * A pre-release is treated as already carrying what its release line adds:
     * `4.0.0-rc.1` ships `serverpod start`, even though semver orders it below
     * `4.0.0`. Comparing the numeric part alone is what makes betas usable.
     */
    fun hasSurfaceOf(release: ServerpodVersion): Boolean = compareNumbers(release) >= 0

    /** Semver ordering, where a pre-release sorts below the release it leads to. */
    override fun compareTo(other: ServerpodVersion): Int {
        compareNumbers(other).let { if (it != 0) return it }

        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + preRelease?.let { "-$it" }.orEmpty()

    private fun compareNumbers(other: ServerpodVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {

        /** Jetstream, the release that reshaped the CLI the plugin drives. */
        val V4 = ServerpodVersion(4, 0, 0)

        // `serverpod version` prints "Serverpod version: 4.0.0-rc.1". Build
        // metadata after a `+` carries no ordering, so it is dropped.
        private val PATTERN = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?""")

        /** Reads the first version in [text], or null when there is none. */
        fun parse(text: String?): ServerpodVersion? {
            val match = text?.let { PATTERN.find(it) } ?: return null
            val (major, minor, patch, preRelease) = match.destructured

            return ServerpodVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                preRelease = preRelease.takeIf { it.isNotEmpty() },
            )
        }

        /**
         * Compares dot-separated pre-release identifiers per semver: numeric ones
         * compare as numbers, and a numeric identifier sorts below an alphanumeric
         * one, so `rc.2` follows `rc.1` and `rc.1` follows `beta.9`.
         */
        private fun comparePreRelease(left: String, right: String): Int {
            val leftParts = left.split('.')
            val rightParts = right.split('.')

            for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
                val leftPart = leftParts.getOrNull(index) ?: return -1
                val rightPart = rightParts.getOrNull(index) ?: return 1
                if (leftPart == rightPart) continue

                val leftNumber = leftPart.toIntOrNull()
                val rightNumber = rightPart.toIntOrNull()

                return when {
                    leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                    leftNumber != null -> -1
                    rightNumber != null -> 1
                    else -> leftPart.compareTo(rightPart)
                }
            }

            return 0
        }
    }
}
