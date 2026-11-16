package com.nearer.app.bible

enum class BiblePackRightsStatus { NOT_REVIEWED, PERMISSION_REQUIRED, APPROVED_FOR_DISTRIBUTION }

data class BibleLanguageCatalogueLead(
    val languageName:String,
    val country:String,
    val publisherAvailabilityNote:String,
    val nearerPackRights:BiblePackRightsStatus=BiblePackRightsStatus.PERMISSION_REQUIRED,
    val nearerPackInstalled:Boolean=false
)

/**
 * Discovery-only Nigerian language leads based on the Bible Society of Nigeria's public
 * Bible-download guide. These entries are NOT NeaRer downloads and do not grant redistribution
 * rights. Exact edition, publisher, canon, copyright and offline distribution permission must
 * be confirmed before a NeaRer pack is published.
 */
object BibleLanguageCatalogue {
    val nigeriaLeads=listOf(
        BibleLanguageCatalogueLead("Efik","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Yoruba","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Igbo","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Hausa","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Kalabari","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Bura-Pabir","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Gun","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Igala","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Urhobo","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Tiv","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Kinke","Nigeria","BSN/YouVersion guide; spelling/edition to confirm"),
        BibleLanguageCatalogueLead("Bokyi","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Yala","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Eggon","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Idoma","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Ebira","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Basa","Nigeria","BSN/YouVersion guide"),
        BibleLanguageCatalogueLead("Igede","Nigeria","BSN/YouVersion guide")
    )
}
