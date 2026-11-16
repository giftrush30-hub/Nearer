package com.nearer.app.bible

enum class BiblePackRightsStatus {
    NOT_REVIEWED, PERMISSION_REQUIRED, APPROVED_FOR_DISTRIBUTION,
    PUBLIC_DOMAIN_TEXT_WITH_NAME_AND_ATTRIBUTION_CONDITIONS
}

data class BibleLanguageCatalogueLead(
    val languageName:String,
    val country:String,
    val publisherAvailabilityNote:String,
    val nearerPackRights:BiblePackRightsStatus=BiblePackRightsStatus.PERMISSION_REQUIRED,
    val nearerPackInstalled:Boolean=false
)

/**
 * Discovery leads only. BSN lists some editions on YouVersion and reports broader translation
 * activity. None of these listings grants NeaRer permission to extract, cache or redistribute
 * Scripture. Confirm the exact edition, rights holder, canon and licence in writing.
 */
object BibleLanguageCatalogue {
    const val bsnReportedCompleteNigerianLanguageCount=27
    const val bsnReportedOngoingOrRevisionProjectCount=10
    const val bsnCurrentYouVersionPageUrl="https://www.biblesociety-nigeria.org/bible-downloads/"
    const val bsnNewerGuideUrl="https://www.biblesociety-nigeria.org/download-install-and-read-the-bible-in-your-local-language/"
    const val bsnRightsContactUrl="https://www.biblesociety-nigeria.org/contact-us/"
    const val bsnRightsEmail="info@biblesociety-nigeria.org"
    const val bsnPublishingPartnershipEmail="oludaisi@biblesociety-nigeria.org"

    val globalPublicDomainLeads=listOf(
        BibleLanguageCatalogueLead(
            "World English Bible (WEBP)","Global",
            "eBible.org identifies the text as public domain; preserve the text faithfully and observe the World English Bible trademark conditions.",
            BiblePackRightsStatus.PUBLIC_DOMAIN_TEXT_WITH_NAME_AND_ATTRIBUTION_CONDITIONS
        )
    )

    /** The 17 language/edition rows on BSN's Bible-download page (including separate Hausa Ajami). */
    val bsnDownloadPageLeads=listOf(
        BibleLanguageCatalogueLead("Yoruba","Nigeria","YCE · Yoruba Bible (BM) · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Igbo","Nigeria","IGBOB · Bible · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Hausa","Nigeria","HAU · Hausa Bible · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Efik","Nigeria","ENA · Efik Old Bible With Sectional Headings · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Urhobo","Nigeria","UBV77 · Bible in Urhobo · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Tiv","Nigeria","TOBNR · Tiv Old Bible No Ref · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Igala","Nigeria","IGL70 · The Bible in Igala 1970 · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Hausa Ajami","Nigeria","hauAS · Hausa Ajami Bible · distinct script/edition · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Idoma","Nigeria","IFBCL · Idoma Common Language Bible · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Ebira","Nigeria","EBTP · Ebira Bible Translation Project · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Bura-Pabir","Nigeria","KNB · Bura Bible · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Kalabari","Nigeria","KBTP · Kalabari Bible Translation · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Igede","Nigeria","IBNO · Igede Bible Translation · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Kirike / Okrika","Nigeria","OBTP · Okrika Bible Translation Project · BSN/YouVersion"),
        BibleLanguageCatalogueLead("Eggon","Nigeria","EGONT74 · Eggon New Testament · partial-canon warning required"),
        BibleLanguageCatalogueLead("Basa / Rubassa","Nigeria","RBT · Rubassa Bible Translation · confirm exact language name"),
        BibleLanguageCatalogueLead("Bokyi","Nigeria","BKY · Bokyi Bible · BSN/YouVersion")
    )

    /** Additional language names in BSN's newer 18-language installation guide. */
    val bsnNewerGuideAdditionalLeads=listOf(
        BibleLanguageCatalogueLead("Gun","Nigeria","Named in BSN's newer YouVersion guide; exact edition ID to confirm"),
        BibleLanguageCatalogueLead("Yala","Nigeria","Named in BSN's newer YouVersion guide; exact edition ID to confirm"),
        BibleLanguageCatalogueLead("Kinke","Nigeria","Named in BSN's newer YouVersion guide; spelling and exact edition to confirm")
    )

    /** Compatibility aggregate for catalogue UI; all Nigerian entries still require permission. */
    val nigeriaLeads=bsnDownloadPageLeads+bsnNewerGuideAdditionalLeads
}
