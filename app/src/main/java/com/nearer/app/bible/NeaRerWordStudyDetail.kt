package com.nearer.app.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Study detail surface. It is only populated by trusted, imported lexical/alignment data. */
@Composable
fun NeaRerWordStudyDetail(
    surfaceWord:String,
    entries:List<BibleWordStudyEntry>,
    passageReference:String,
    onSearchOriginalLanguage:(BiblicalLanguage,String)->Unit,
    onClose:()->Unit
) {
    Card(shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF1A2940)),
        modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("WORD STUDY",color=Color(0xFFFFD12D),fontSize=10.sp,fontWeight=FontWeight.Bold)
                TextButton(onClick=onClose) { Text("CLOSE",color=Color(0xFF69E0C0)) }
            }
            Text(surfaceWord,color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(passageReference,color=Color(0xFFA8B6C9),fontSize=11.sp)
            if(entries.isEmpty()) {
                Text("Original-language mapping is not available for this word in this passage yet. NeaRer will not guess a Greek, Hebrew or Aramaic equivalent.",
                    color=Color(0xFFD5DCE7),fontSize=13.sp,lineHeight=19.sp)
            } else {
                entries.forEachIndexed { index, entry ->
                    if(index>0) Text("Another original-language link",color=Color(0xFFFFD12D),fontSize=10.sp,fontWeight=FontWeight.Bold)
                    Text(entry.lemma,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Text(entry.transliteration+" · "+entry.language.displayName,color=Color(0xFF69E0C0),fontSize=13.sp)
                    entry.pronunciation?.let { Text("Pronunciation: "+it,color=Color(0xFFA8B6C9),fontSize=12.sp) }
                    entry.strongNumber?.let { Text("Strong's: "+it,color=Color(0xFFFFD12D),fontSize=11.sp) }
                    entry.partOfSpeech?.let { Text("Grammar: "+it,color=Color(0xFFA8B6C9),fontSize=11.sp) }
                    Text(entry.gloss,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    Text(entry.definition,color=Color(0xFFD5DCE7),fontSize=13.sp,lineHeight=19.sp)
                    Text("Source: "+entry.sourceName+" · "+entry.attribution,color=Color(0xFFA8B6C9),fontSize=10.sp)
                    Text("Licence: "+entry.licenseId,color=Color(0xFFA8B6C9),fontSize=9.sp)
                }
            }
            Text(BibleWordStudyCopy.CONTEXT_NOTICE,color=Color(0xFFA8B6C9),fontSize=11.sp,lineHeight=16.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                BiblicalLanguage.values().forEach { language ->
                    TextButton(onClick={onSearchOriginalLanguage(language,surfaceWord)}) {
                        Text(language.displayName,color=Color(0xFF69E0C0),fontSize=9.sp)
                    }
                }
            }
        }
    }
}
