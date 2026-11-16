package com.nearer.app.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import java.util.UUID

/** Local-only study journal. Host owns persistence through the supplied database. */
@Composable
fun NeaRerWordStudyJournalScreen(
    notes:List<BibleStudyNote>,
    bookmarks:List<BibleBookmark>,
    highlights:List<BibleVerseHighlight>,
    onSaveNote:(BibleStudyNote)->Unit,
    onDeleteNote:(String)->Unit,
    onDeleteBookmark:(String)->Unit,
    onDeleteHighlight:(String)->Unit,
    onOpenReference:(String,String,Int,Int?)->Unit,
    defaultTranslationId:String?=null,
    defaultBookId:String?=null,
    defaultChapter:Int?=null,
    defaultVerse:Int?=null,
    modifier:Modifier=Modifier
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("NOTES") }
    Column(modifier.fillMaxSize().background(Color(0xFF101A2B)).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("MY WORD JOURNAL",color=Color(0xFFFFD12D),fontSize=11.sp,fontWeight=FontWeight.Bold)
        Text("Keep what you learn",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
        Text("Your notes, bookmarks and highlights stay on this device.",color=Color(0xFFA8B6C9),fontSize=12.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={filter="NOTES"}) { Text("STUDY NOTES",color=if(filter=="NOTES") Color(0xFF69E0C0) else Color.White) }
            TextButton(onClick={filter="BOOKMARKS"}) { Text("BOOKMARKS",color=if(filter=="BOOKMARKS") Color(0xFF69E0C0) else Color.White) }
            TextButton(onClick={filter="HIGHLIGHTS"}) { Text("HIGHLIGHTS",color=if(filter=="HIGHLIGHTS") Color(0xFF69E0C0) else Color.White) }
        }
        if(filter=="NOTES") {
            Surface(color=Color(0xFF1A2940)) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(value=title,onValueChange={title=it},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,label={Text("Note title")})
                    OutlinedTextField(value=body,onValueChange={body=it},modifier=Modifier.fillMaxWidth(),
                        minLines=3,label={Text("What did you learn?")})
                    Button(onClick={
                        onSaveNote(BibleStudyNote(UUID.randomUUID().toString(),defaultTranslationId,defaultBookId,
                            defaultChapter,defaultVerse,title.trim().ifBlank{"My Bible study"},body.trim(),
                            0xFFFFD12D,System.currentTimeMillis()))
                        title="";body=""
                    },enabled=body.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Save private note") }
                }
            }
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(notes,key={it.id}) { note ->
                    Surface(color=Color(0xFF1A2940)) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(note.title,color=Color.White,fontWeight=FontWeight.Bold)
                            Text(note.body,color=Color(0xFFD5DCE7),fontSize=13.sp)
                            Row {
                                TextButton(onClick={onDeleteNote(note.id)}) { Text("DELETE",color=Color(0xFFFFD12D)) }
                                if(note.translationId!=null && note.bookId!=null && note.chapter!=null)
                                    TextButton(onClick={onOpenReference(note.translationId,note.bookId,note.chapter,note.verse)}) {
                                        Text("OPEN PASSAGE",color=Color(0xFF69E0C0))
                                    }
                            }
                        }
                    }
                }
            }
        } else if(filter=="BOOKMARKS") {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(bookmarks,key={it.id}) { mark ->
                    Surface(color=Color(0xFF1A2940)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(mark.bookId+" "+mark.chapter+(mark.verse?.let{":$it"}?:""),
                                    color=Color.White,fontWeight=FontWeight.Bold)
                                Text("Saved bookmark",color=Color(0xFFA8B6C9),fontSize=11.sp)
                            }
                            TextButton(onClick={onOpenReference(mark.translationId,mark.bookId,mark.chapter,mark.verse)}) {
                                Text("OPEN",color=Color(0xFF69E0C0))
                            }
                            TextButton(onClick={onDeleteBookmark(mark.id)}) { Text("REMOVE",color=Color(0xFFFFD12D)) }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(highlights,key={it.id}) { highlight ->
                    Surface(color=Color(0xFF1A2940)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(highlight.bookId+" "+highlight.chapter+":"+highlight.verse,
                                    color=Color.White,fontWeight=FontWeight.Bold)
                                Text("Highlighted verse",color=Color(0xFFA8B6C9),fontSize=11.sp)
                            }
                            TextButton(onClick={onOpenReference(highlight.translationId,highlight.bookId,highlight.chapter,highlight.verse)}) {
                                Text("OPEN",color=Color(0xFF69E0C0))
                            }
                            TextButton(onClick={onDeleteHighlight(highlight.id)}) {
                                Text("REMOVE",color=Color(0xFFFFD12D))
                            }
                        }
                    }
                }
            }
        }
    }
}
