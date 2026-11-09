package com.nearer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardFormatPolicyTest {
    @Test fun exportsNamePhoneAndEmailInVCard30Format() {
        val vcard = VCardFormatPolicy.create("Ada Lovelace", listOf("+234 800 123 4567"), listOf("ada@example.com"))
        assertTrue(vcard.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(vcard.contains("FN:Ada Lovelace\r\n"))
        assertTrue(vcard.contains("TEL;TYPE=CELL:+234 800 123 4567\r\n"))
        assertTrue(vcard.contains("EMAIL;TYPE=INTERNET:ada@example.com\r\n"))
        assertTrue(vcard.endsWith("END:VCARD\r\n"))
    }

    @Test fun escapesVCardSeparatorsAndLineBreaks() {
        val vcard = VCardFormatPolicy.create("A;B, C\nInjected", emptyList(), emptyList())
        assertTrue(vcard.contains("FN:A\\;B\\, C\\nInjected\r\n"))
        assertFalse(vcard.contains("\nInjected"))
    }

    @Test fun omitsBlankPhoneAndEmailValues() {
        val vcard = VCardFormatPolicy.create("Contact", listOf("", "  "), listOf(""))
        assertFalse(vcard.contains("TEL;"))
        assertFalse(vcard.contains("EMAIL;"))
    }
}
