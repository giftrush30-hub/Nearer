package com.nearer.app.bible

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BibleLibraryItem(
    val translation:BibleTranslation,
    val coverage:BibleCoverage,
    val availableOffline:Boolean,
    val downloadSizeLabel:String?=null
)

/** Searchable language/version catalogue. Download and licence actions are supplied by the host. */
@Composable
fun NeaRerWordLibraryScreen(
    items:List<BibleLibraryItem>,
    onOpen:(BibleTranslation)->Unit,
    onDownload:(BibleTranslation)->Unit,
    modifier:Modifier=Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered=items.filter {
        val q=query.trim()
        q.isBlank() || it.translation.name.contains(q,true) ||
            it.translation.languageName.contains(q,true) ||
            it.translation.abbreviation.contains(q,true)
    }
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Bible library",style=MaterialTheme.typography.headlineSmall,color=Color.White)
        Text("Find Scripture by language or translation. Downloaded packs remain available offline.",
            color=Color(0xFFA8B6C9),fontSize=12.sp)
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),
            singleLine=true,label={Text("Search language or version")})
        LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)) {
            items(filtered,key={it.translation.id}) { item ->
                Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF1A2940))) {
                    Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(item.translation.name,color=Color.White,style=MaterialTheme.typography.titleMedium)
                        Text("${item.translation.languageName} • ${item.translation.abbreviation}",
                            color=Color(0xFF69E0C0),fontSize=12.sp)
                        Text(when(item.coverage) {
                            BibleCoverage.COMPLETE_BIBLE->"Complete Bible"
                            BibleCoverage.OLD_TESTAMENT->"Old Testament"
                            BibleCoverage.NEW_TESTAMENT->"New Testament"
                            BibleCoverage.PARTIAL->"Partial translation"
                        },color=Color(0xFFA8B6C9),fontSize=11.sp)
                        item.translation.copyrightNotice?.let {
                            Text(it,color=Color(0xFFA8B6C9),fontSize=9.sp)
                        }
                        Text("Licence: "+item.translation.licenseId+" · Source: "+item.translation.sourceId,
                            color=Color(0xFFA8B6C9),fontSize=9.sp)
                        item.translation.attribution?.let {
                            Text(it,color=Color(0xFFA8B6C9),fontSize=9.sp)
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            if(item.availableOffline) Button(onClick={onOpen(item.translation)}) { Text("Read offline") }
                            else Button(onClick={onDownload(item.translation)}) {
                                Text("Download" + (item.downloadSizeLabel?.let{" • $it"} ?: ""))
                            }
                        }
                    }
                }
            }
        }
    }
}
