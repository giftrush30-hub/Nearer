package com.nearer.app.bible

data class BibleCanonBookSpec(val usfmCode:String,val chapterCount:Int)

/** Canon metadata used to verify that a pack is genuinely complete, not just self-labelled. */
object BibleCanonChapterCounts {
    val protestant66:Map<Int,BibleCanonBookSpec> = listOf(
        "GEN" to 50,"EXO" to 40,"LEV" to 27,"NUM" to 36,"DEU" to 34,
        "JOS" to 24,"JDG" to 21,"RUT" to 4,"1SA" to 31,"2SA" to 24,
        "1KI" to 22,"2KI" to 25,"1CH" to 29,"2CH" to 36,"EZR" to 10,
        "NEH" to 13,"EST" to 10,"JOB" to 42,"PSA" to 150,"PRO" to 31,
        "ECC" to 12,"SNG" to 8,"ISA" to 66,"JER" to 52,"LAM" to 5,
        "EZK" to 48,"DAN" to 12,"HOS" to 14,"JOL" to 3,"AMO" to 9,
        "OBA" to 1,"JON" to 4,"MIC" to 7,"NAM" to 3,"HAB" to 3,
        "ZEP" to 3,"HAG" to 2,"ZEC" to 14,"MAL" to 4,
        "MAT" to 28,"MRK" to 16,"LUK" to 24,"JHN" to 21,"ACT" to 28,
        "ROM" to 16,"1CO" to 16,"2CO" to 13,"GAL" to 6,"EPH" to 6,
        "PHP" to 4,"COL" to 4,"1TH" to 5,"2TH" to 3,"1TI" to 6,
        "2TI" to 4,"TIT" to 3,"PHM" to 1,"HEB" to 13,"JAS" to 5,
        "1PE" to 5,"2PE" to 3,"1JN" to 5,"2JN" to 1,"3JN" to 1,
        "JUD" to 1,"REV" to 22
    ).mapIndexed { index, pair -> (index+1) to BibleCanonBookSpec(pair.first,pair.second) }.toMap()
}
