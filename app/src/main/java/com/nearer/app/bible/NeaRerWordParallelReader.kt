package com.nearer.app.bible

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BibleParallelVerse(val reference:String,val primary:BibleVerse?,val secondary:BibleVerse?)

/** Side-by-side comparison. Caller provides matching references from installed local packs. */
@Composable
fun NeaRerWordParallelReader(
    primaryName:String,
    secondaryName:String,
    verses:List<BibleParallelVerse>,
    modifier:Modifier=Modifier
) {
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        items(verses,key={it.reference}) { item ->
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF1A2940))) {
                Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(item.reference,color=Color(0xFFFFD12D),style=MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(primaryName,color=Color(0xFF69E0C0),fontSize=10.sp)
                            Text(item.primary?.text ?: "Not available in this translation.",
                                color=Color.White,fontSize=14.sp,lineHeight=21.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(secondaryName,color=Color(0xFF69E0C0),fontSize=10.sp)
                            Text(item.secondary?.text ?: "Not available in this translation.",
                                color=Color.White,fontSize=14.sp,lineHeight=21.sp)
                        }
                    }
                }
            }
        }
    }
}
