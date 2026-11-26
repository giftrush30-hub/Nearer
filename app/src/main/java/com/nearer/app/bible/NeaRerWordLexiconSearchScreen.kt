package com.nearer.app.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NeaRerWordLexiconSearchScreen(
    engine:BibleWordStudyEngine,
    initialQuery:String,
    language:BiblicalLanguage,
    onSelect:(BibleWordStudyEntry)->Unit,
    onBack:()->Unit,
    modifier:Modifier=Modifier
) {
    var query by remember(language,initialQuery){mutableStateOf(initialQuery)}
    val results=remember(language,query){engine.search(language,query)}
    Column(modifier.fillMaxSize().background(Color(0xFF101A2B)).padding(15.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        TextButton(onClick=onBack){Text("‹ BACK TO READING",color=Color(0xFFFFD12D))}
        Text(language.displayName+" word search",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text("Search original script, transliteration or English gloss in the installed, attributed lexicon.",
            color=Color(0xFFA8B6C9),fontSize=11.sp)
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),
            singleLine=true,label={Text("Lemma, transliteration or meaning")})
        if(query.trim().length<2) Text("Enter at least two characters to search.",color=Color(0xFFA8B6C9),fontSize=11.sp)
        else if(results.isEmpty()) Text("No matching entries in the installed dataset.",color=Color(0xFFA8B6C9),fontSize=12.sp)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)) {
            items(results,key={it.id}) { entry ->
                Surface(color=Color(0xFF1A2940),modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(entry.lemma,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                        Text(entry.transliteration+" · "+entry.gloss,color=Color(0xFF69E0C0),fontSize=12.sp)
                        Text(entry.partOfSpeech ?: entry.language.displayName,color=Color(0xFFA8B6C9),fontSize=10.sp)
                        Text(entry.definition,color=Color(0xFFD5DCE7),fontSize=12.sp)
                        TextButton(onClick={onSelect(entry)}){Text("OPEN WORD STUDY",color=Color(0xFFFFD12D))}
                    }
                }
            }
        }
    }
}
