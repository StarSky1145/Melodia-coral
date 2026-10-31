package com.lin0721.linmusic.core.auth

// 把响应里的 Set-Cookie 并入已存的 Cookie 串：同名覆盖；空值与立即过期（Max-Age=0）的条目忽略，避免把有效会话刷没
fun mergeCookies(existing: String?, setCookieHeaders: List<String>): String {
    val merged = LinkedHashMap<String, String>()
    existing.orEmpty().split(';').map { it.trim() }.filter { it.contains('=') }.forEach { entry ->
        merged[entry.substringBefore('=').trim()] = entry.substringAfter('=').trim()
    }
    setCookieHeaders.forEach { header ->
        val parts = header.split(';').map { it.trim() }
        val pair = parts.first()
        if (!pair.contains('=')) return@forEach
        val name = pair.substringBefore('=').trim()
        val value = pair.substringAfter('=').trim()
        val expiredNow = parts.drop(1).any { it.equals("Max-Age=0", ignoreCase = true) }
        if (name.isEmpty() || value.isEmpty() || expiredNow) return@forEach
        merged[name] = value
    }
    return merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}
