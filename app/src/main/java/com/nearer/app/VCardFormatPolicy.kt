package com.nearer.app

/** Pure vCard 3.0 text formatting so contact exports are deterministic and testable. */
internal object VCardFormatPolicy {
    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r", "")
        .replace("\n", "\\n")

    fun create(displayName: String, phoneNumbers: List<String>, emailAddresses: List<String>): String {
        val lines = ArrayList<String>()
        lines += "BEGIN:VCARD"
        lines += "VERSION:3.0"
        lines += "N:;" + escape(displayName) + ";;;"
        lines += "FN:" + escape(displayName)
        phoneNumbers.filter { it.isNotBlank() }.forEach { lines += "TEL;TYPE=CELL:" + escape(it) }
        emailAddresses.filter { it.isNotBlank() }.forEach { lines += "EMAIL;TYPE=INTERNET:" + escape(it) }
        lines += "END:VCARD"
        return lines.joinToString("\r\n", postfix = "\r\n")
    }
}
