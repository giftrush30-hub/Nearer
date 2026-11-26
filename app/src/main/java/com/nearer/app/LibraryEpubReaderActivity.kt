package com.nearer.app

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * Local EPUB 2/3 reader host. The source document stays at its Android document URI;
 * Readium reads it through ContentResolver and does not copy it into NeaRer storage.
 */
internal class LibraryEpubReaderActivity : FragmentActivity() {
    private var publicationToClose: org.readium.r2.shared.publication.Publication? = null
    private lateinit var navigatorContainer: FrameLayout
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium's navigator fragment needs a factory during Android process restoration.
        // We deliberately finish and reopen the document rather than restoring a fragment
        // without its in-memory Publication object.
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        val uriString = intent.getStringExtra(EXTRA_URI)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Your book" }
        if (uriString.isNullOrBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AndroidColor.rgb(16, 26, 43))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(AndroidColor.rgb(26, 41, 64))
        }
        val back = TextView(this).apply {
            text = "‹  LIBRARY"
            textSize = 13f
            setTextColor(AndroidColor.rgb(105, 224, 192))
            setPadding(0, dp(8), dp(16), dp(8))
            setOnClickListener { finish() }
        }
        titleText = TextView(this).apply {
            text = title
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(AndroidColor.WHITE)
        }
        toolbar.addView(back, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        toolbar.addView(titleText, LinearLayout.LayoutParams(0, dp(40), 1f))
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        navigatorContainer = FrameLayout(this).apply {
            id = View.generateViewId()
            setBackgroundColor(AndroidColor.rgb(250, 248, 242))
        }
        root.addView(navigatorContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        statusText = TextView(this).apply {
            text = "Opening your book…"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.WHITE)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        navigatorContainer.addView(statusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)

        lifecycleScope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(uriString)
                    val url = uri.toAbsoluteUrl()
                        ?: error("Android could not create a readable address for this book.")
                    val httpClient = DefaultHttpClient()
                    val assetRetriever = AssetRetriever(contentResolver, httpClient)
                    val asset = assetRetriever.retrieve(url)
                        .getOrElse { error("NeaRer could not read this EPUB file: $it") }
                    val parser = DefaultPublicationParser(
                        context = this@LibraryEpubReaderActivity,
                        httpClient = httpClient,
                        assetRetriever = assetRetriever,
                        pdfFactory = null
                    )
                    PublicationOpener(parser).open(asset, allowUserInteraction = false)
                        .getOrElse { error("This EPUB could not be opened: $it") }
                }
            }

            opened.onFailure { failure ->
                statusText.text = failure.message ?: "NeaRer could not open this EPUB."
            }.onSuccess { publication ->
                if (publication.isRestricted) {
                    publication.close()
                    statusText.text = "This book is protected by a licence NeaRer does not yet support. It has not been copied or altered."
                    return@onSuccess
                }
                if (!publication.conformsTo(org.readium.r2.shared.publication.Publication.Profile.EPUB)) {
                    publication.close()
                    statusText.text = "This file is not a supported EPUB publication."
                    return@onSuccess
                }

                publicationToClose = publication
                val progressPrefs = getSharedPreferences(PREFS_READING, Context.MODE_PRIVATE)
                val progressKey = "epub-progress:$uriString"
                val savedProgress = progressPrefs.getFloat(progressKey, 0f).toDouble().coerceIn(0.0, 1.0)
                val initialLocator = if (savedProgress > 0.0) {
                    withContext(Dispatchers.IO) { publication.locateProgression(savedProgress) }
                } else null

                val factory = EpubNavigatorFactory(
                    publication = publication,
                    configuration = EpubNavigatorFactory.Configuration(
                        defaults = EpubDefaults(scroll = false, pageMargins = 1.35)
                    )
                )
                supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                    initialLocator = initialLocator
                )
                statusText.visibility = View.GONE
                supportFragmentManager.beginTransaction()
                    .replace(
                        navigatorContainer.id,
                        EpubNavigatorFragment::class.java,
                        Bundle(),
                        NAVIGATOR_TAG
                    )
                    .commitNow()

                val navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)
                    as? EpubNavigatorFragment
                if (navigator == null) {
                    statusText.visibility = View.VISIBLE
                    statusText.text = "The EPUB reader could not be attached. Please reopen the book."
                    return@onSuccess
                }
                (navigator as? OverflowableNavigator)?.addInputListener(
                    DirectionalNavigationAdapter(navigator, animatedTransition = true)
                )
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        navigator.currentLocator.collect { locator ->
                            val progress = locator.locations.totalProgression
                            if (progress != null && progress.isFinite()) {
                                progressPrefs.edit()
                                    .putFloat(progressKey, progress.toFloat().coerceIn(0f, 1f))
                                    .apply()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            publicationToClose?.close()
            publicationToClose = null
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URI = "nearer.library.epub.uri"
        const val EXTRA_TITLE = "nearer.library.epub.title"
        private const val PREFS_READING = "nearer_library_reading"
        private const val NAVIGATOR_TAG = "nearer_library_epub_navigator"
    }
}
