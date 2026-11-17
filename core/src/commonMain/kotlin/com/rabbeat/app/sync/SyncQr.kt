package com.rabbeat.app.sync

/**
 * Payload painted into the desktop QR and read by the phone.
 * `rb1|<host:port>,<host:port>|<6 digit code>`
 */
object SyncQr {
    private const val PREFIX = "rb1"

    fun encode(hosts: List<String>, code: String): String =
        "$PREFIX|${hosts.joinToString(",")}|$code"

    fun decode(text: String): Payload? {
        val parts = text.trim().split('|')
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val hosts = parts[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val code = parts[2].trim()
        if (hosts.isEmpty() || code.length != 6 || code.any { !it.isDigit() }) return null
        return Payload(hosts, code)
    }

    data class Payload(val hosts: List<String>, val code: String)
}
