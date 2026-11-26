package com.nearer.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class LibraryEpubReaderInstrumentedTest {
    @Test
    fun opensLocalEpubFromContentUriAndReadsItsMetadataAndSpine() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val epub = File(context.cacheDir, "nearer-reader-fixture.epub")
        createFixture(epub)

        try {
            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
            val asset = kotlinx.coroutines.runBlocking {
                assetRetriever.retrieve(epub).getOrElse { error("Fixture asset failed: $it") }
            }
            val parser = DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
            val publication = kotlinx.coroutines.runBlocking {
                PublicationOpener(parser).open(asset, allowUserInteraction = false)
                    .getOrElse { error("Fixture publication failed: $it") }
            }
            try {
                assertTrue(publication.conformsTo(Publication.Profile.EPUB))
                assertEquals(1, publication.readingOrder.size)
                assertEquals("chapter.xhtml", publication.readingOrder.single().href.toString().substringAfterLast('/'))
            } finally {
                publication.close()
            }
        } finally {
            epub.delete()
        }
    }

    private fun createFixture(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            }
            zip.putNextEntry(mimeEntry)
            zip.write(mimetype)
            zip.closeEntry()

            zipEntry(zip, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent())
            zipEntry(zip, "OEBPS/content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">urn:uuid:nearer-epub-test</dc:identifier>
                    <dc:title>NeaRer EPUB Fixture</dc:title>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
                  <spine><itemref idref="chapter"/></spine>
                </package>
            """.trimIndent())
            zipEntry(zip, "OEBPS/chapter.xhtml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
                  <head><title>Chapter One</title></head>
                  <body><h1>NeaRer works offline</h1><p>This is a local EPUB fixture.</p></body>
                </html>
            """.trimIndent())
        }
    }

    private fun zipEntry(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
