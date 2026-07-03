package com.kni.ui.compose.screens

import org.json.JSONObject

/**
 * Markdown renderers for the Transaction Detail screen "Copy" actions. Each tab
 * can be copied on its own, or the whole exchange copied at once, in a clean
 * Markdown layout that pastes readably into issues, chats, and docs.
 */

private fun headersMarkdown(json: String): String {
    val pairs = try {
        val obj = JSONObject(json)
        obj.keys().asSequence().map { it to obj.optString(it) }.toList()
    } catch (e: Exception) {
        emptyList()
    }
    if (pairs.isEmpty()) return "_(none)_"
    return pairs.joinToString("\n") { (k, v) -> "- `$k`: $v" }
}

private fun bodyMarkdown(body: String): String {
    if (body.isBlank()) return "_(empty)_"
    val trimmed = body.trim()
    val lang = if (trimmed.startsWith("{") || trimmed.startsWith("[")) "json" else ""
    return "```$lang\n$body\n```"
}

fun DetailData.requestMarkdown(): String = buildString {
    appendLine("### Request")
    appendLine()
    appendLine("`$method $url`")
    appendLine()
    appendLine("**Headers**")
    appendLine(headersMarkdown(requestHeaders))
    appendLine()
    appendLine("**Body**")
    appendLine(bodyMarkdown(requestBody))
}.trimEnd()

fun DetailData.responseMarkdown(): String = buildString {
    appendLine("### Response")
    appendLine()
    appendLine("Status: `${if (status == 0) "—" else status.toString()}`")
    appendLine()
    appendLine("**Headers**")
    appendLine(headersMarkdown(responseHeaders))
    appendLine()
    appendLine("**Body**")
    appendLine(bodyMarkdown(responseBody))
}.trimEnd()

fun DetailData.timingMarkdown(): String = buildString {
    appendLine("### Timing")
    appendLine()
    appendLine("- Duration: `$durationMs ms`")
    appendLine("- Request size: `$reqSize bytes`")
    appendLine("- Response size: `$respSize bytes`")
}.trimEnd()

fun DetailData.tlsMarkdown(): String = buildString {
    appendLine("### TLS")
    appendLine()
    appendLine("- Scheme: `${scheme.uppercase()}`")
    appendLine("- Host (SNI): `$host`")
    if (scheme == "https") {
        appendLine("- Version: `${tlsVersion.ifBlank { "—" }}`")
        appendLine("- Cipher: `${tlsCipher.ifBlank { "—" }}`")
        appendLine("- Certificate: ${tlsCert.ifBlank { "—" }}")
    }
}.trimEnd()

/** The full exchange as a single Markdown document. */
fun DetailData.toMarkdown(): String = buildString {
    appendLine("# $method $url")
    appendLine()
    appendLine(requestMarkdown())
    appendLine()
    appendLine(responseMarkdown())
    appendLine()
    appendLine(timingMarkdown())
    appendLine()
    appendLine(tlsMarkdown())
}.trimEnd()
