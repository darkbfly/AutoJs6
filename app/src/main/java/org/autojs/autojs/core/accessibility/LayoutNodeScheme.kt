package org.autojs.autojs.core.accessibility

/**
 * Layout node capture scheme selected in Settings > Accessibility.
 */
enum class LayoutNodeScheme(val key: String) {
    BUILTIN("builtin"),
    DISGUISED_PLUGIN("disguised_plugin"),
    ;

    companion object {
        fun fromKey(key: String?): LayoutNodeScheme =
            entries.firstOrNull { it.key == key } ?: BUILTIN
    }
}
