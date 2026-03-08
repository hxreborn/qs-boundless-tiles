package eu.hxreborn.qsboundlesstiles.hook

import java.lang.reflect.Field

internal fun ClassLoader.loadOrNull(name: String): Class<*>? =
    runCatching { loadClass(name) }.getOrNull()

internal fun Class<*>.accessibleFieldOrNull(name: String): Field? =
    runCatching { getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
