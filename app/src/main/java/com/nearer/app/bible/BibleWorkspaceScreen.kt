package com.nearer.app.bible

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Main offline Bible destination: import a licensed pack, select its books and read installed chapters. */
@Composable
fun BibleWorkspaceScreen(onBack:()->Unit) {
    val context= LocalContext.current
    val database= remember(context) { BibleLocalDatabase(context) }
    val readingPrefs=remember(context){context.getSharedPreferences("nearer_word_reading",android.content.Context.MODE_PRIVATE)}
    val scope= rememberCoroutineScope()
    var translations by remember { mutableStateOf(database.listTranslations()) }
    var selectedTranslation by remember {
        mutableStateOf(translations.firstOrNull{it.id==readingPrefs.getString("translation_id",null)} ?: translations.firstOrNull())
    }
    var compareTranslation by remember { mutableStateOf(translations.getOrNull(1)) }
    var showComparison by remember { mutableStateOf(false) }
    var books by remember { mutableStateOf(selectedTranslation?.let{database.listBooks(it.id)}.orEmpty()) }
    var selectedBook by remember {
        mutableStateOf(books.firstOrNull{it.id==readingPrefs.getString("book_id",null)} ?: books.firstOrNull())
    }
    var chapter by remember { mutableStateOf(readingPrefs.getInt("chapter",1).coerceIn(1,selectedBook?.chapterCount ?: 1)) }
    var verses by remember { mutableStateOf(selectedBook?.let{database.readChapter(selectedTranslation!!.id,it.id,chapter)}.orEmpty()) }
    var search by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<BibleVerse>()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Import a licensed NeaRer Bible pack to begin reading offline.") }
    var selectedWord by remember { mutableStateOf<Pair<BibleVerse,String>?>(null) }
    var showJournal by remember { mutableStateOf(false) }
    var selectedVerseNumber by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedTranslation?.id,selectedBook?.id,chapter) {
        selectedTranslation?.let{readingPrefs.edit().putString("translation_id",it.id).apply()}
        selectedBook?.let{readingPrefs.edit().putString("book_id",it.id).apply()}
        readingPrefs.edit().putInt("chapter",chapter).apply()
    }

    fun refresh() {
        translations=database.listTranslations()
        selectedTranslation=translations.firstOrNull()
        compareTranslation=translations.getOrNull(1)
        showComparison=false
        books=selectedTranslation?.let{database.listBooks(it.id)}.orEmpty()
        selectedBook=books.firstOrNull()
        chapter=1
        verses=if(selectedTranslation!=null && selectedBook!=null)
            database.readChapter(selectedTranslation!!.id,selectedBook!!.id,1) else emptyList()
    }
    val importerPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri? ->
        if(uri!=null) {
            busy=true
            notice="Installing and verifying your Bible pack…"
            scope.launch {
                val result=withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        val temp=File.createTempFile("nearer-word-",".zip",context.cacheDir)
                        context.contentResolver.openInputStream(uri)!!.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                        try {
                            BiblePackImporter(context,SqliteBiblePackInstallTarget(database)).import(temp)
                        } finally { temp.delete() }
                    }
                }
                busy=false
                result.onSuccess {
                    refresh()
                    notice="Installed "+it.packId+" with "+it.importedVerses+" verified verses. It is ready offline."
                }.onFailure { notice="Pack not installed: "+(it.message ?: "unknown error") }
            }
        }
    }

    if(showJournal) {
        NeaRerWordStudyJournalScreen(
            notes=database.listNotes(),bookmarks=database.listBookmarks(),
            onSaveNote={database.saveNote(it);notice="Private study note saved."},
            onDeleteNote={database.deleteNote(it);notice="Note deleted."},
            onDeleteBookmark={database.deleteBookmark(it);notice="Bookmark removed."},
            onOpenReference={translationId,bookId,chapterNo,verseNo->
                selectedTranslation=translations.firstOrNull{it.id==translationId}
                books=database.listBooks(translationId)
                selectedBook=books.firstOrNull{it.id==bookId}
                chapter=chapterNo
                verses=database.readChapter(translationId,bookId,chapterNo)
                showJournal=false
            },
            defaultTranslationId=selectedTranslation?.id,
            defaultBookId=selectedBook?.id,
            defaultChapter=chapter,
            defaultVerse=selectedVerseNumber,
            modifier=Modifier.fillMaxSize()
        )
        return
    }

    if(selectedWord!=null) {
        val selected=selectedWord!!
        val entry=BibleWordStudyEngine(database).findForVerseWord(selected.first,selected.second)
        Dialog(onDismissRequest={selectedWord=null}) {
            NeaRerWordStudyDetail(
                surfaceWord=selected.second,entry=entry,passageReference=selected.first.reference,
                onSearchOriginalLanguage={language,query->
                    notice="Search " + language.displayName + " for " + query + " in the installed lexical library."
                },
                onClose={selectedWord=null}
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101A2B)).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("‹  NEARER WORD",color=Color(0xFF69E0C0),fontSize=12.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.padding(vertical=8.dp))
            TextButton(onClick={onBack}) { Text("BACK",color=Color(0xFFFFD12D)) }
        }
        Text("Your Bible library",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold)
        Text("Private, offline reading after a licensed translation pack is installed.",color=Color(0xFFA8B6C9),fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={importerPicker.launch(arrayOf("application/zip","application/x-zip-compressed"))},
                enabled=!busy) { Text("＋ Install Bible pack") }
            OutlinedButton(onClick={showJournal=true}) { Text("My notes") }
        }
        if(busy) CircularProgressIndicator(color=Color(0xFF69E0C0))
        if(translations.isNotEmpty()) {
            Text("INSTALLED VERSIONS",color=Color(0xFFFFD12D),fontSize=10.sp,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                translations.forEach { translation ->
                    OutlinedButton(onClick={
                        selectedTranslation=translation
                        books=database.listBooks(translation.id)
                        selectedBook=books.firstOrNull()
                        chapter=1
                        verses=selectedBook?.let{database.readChapter(translation.id,it.id,1)}.orEmpty()
                    }) { Text(translation.abbreviation) }
                }
            }
        }
        if(translations.size>1 && selectedTranslation!=null) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(onClick={showComparison=false}) { Text("SINGLE",color=if(!showComparison) Color(0xFF69E0C0) else Color.White) }
                TextButton(onClick={showComparison=true}) { Text("COMPARE VERSIONS",color=if(showComparison) Color(0xFF69E0C0) else Color.White) }
            }
            if(showComparison) {
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    translations.filter{it.id!=selectedTranslation!!.id}.forEach { translation ->
                        OutlinedButton(onClick={compareTranslation=translation}) { Text(translation.abbreviation) }
                    }
                }
            }
        }
        if(books.isNotEmpty() && selectedTranslation!=null) {
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                OutlinedButton(onClick={
                    val index=books.indexOfFirst{it.id==selectedBook?.id}
                    selectedBook=books.getOrNull((index-1).coerceAtLeast(0))
                    chapter=1
                    verses=selectedBook?.let{database.readChapter(selectedTranslation!!.id,it.id,1)}.orEmpty()
                }) { Text("‹ Book") }
                OutlinedButton(onClick={
                    val index=books.indexOfFirst{it.id==selectedBook?.id}
                    selectedBook=books.getOrNull((index+1).coerceAtMost(books.lastIndex))
                    chapter=1
                    verses=selectedBook?.let{database.readChapter(selectedTranslation!!.id,it.id,1)}.orEmpty()
                }) { Text("Book ›") }
                OutlinedButton(onClick={
                    chapter=(chapter-1).coerceAtLeast(1)
                    verses=database.readChapter(selectedTranslation!!.id,selectedBook!!.id,chapter)
                }) { Text("−") }
                Text((selectedBook?.name ?: "")+" "+chapter,color=Color.White,fontWeight=FontWeight.Bold,
                    modifier=Modifier.padding(top=12.dp))
                OutlinedButton(onClick={
                    chapter=(chapter+1).coerceAtMost(selectedBook!!.chapterCount)
                    verses=database.readChapter(selectedTranslation!!.id,selectedBook!!.id,chapter)
                }) { Text("+") }
            }
            OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),
                singleLine=true,label={Text("Search this translation")})
            Button(onClick={
                searchResults=database.searchVerses(selectedTranslation!!.id,search)
                notice=searchResults.size.toString()+" matching verses"
            },enabled=search.isNotBlank()) { Text("Search verses") }
            val secondaryBook=if(showComparison && compareTranslation!=null)
                database.listBooks(compareTranslation!!.id).firstOrNull{it.canonicalNumber==selectedBook!!.canonicalNumber}
                else null
            val secondaryVerses=if(secondaryBook!=null)
                database.readChapter(compareTranslation!!.id,secondaryBook.id,chapter) else emptyList()
            if(showComparison && compareTranslation!=null && secondaryBook!=null) {
                val primaryMap=verses.associateBy{it.verse}
                val secondaryMap=secondaryVerses.associateBy{it.verse}
                val maxVerse=maxOf(primaryMap.keys.maxOrNull() ?: 0,secondaryMap.keys.maxOrNull() ?: 0)
                NeaRerWordParallelReader(
                    primaryName=selectedTranslation!!.abbreviation,
                    secondaryName=compareTranslation!!.abbreviation,
                    verses=(1..maxVerse).map{n->BibleParallelVerse(
                        selectedBook!!.name+" "+chapter+":"+n,primaryMap[n],secondaryMap[n])},
                    modifier=Modifier.weight(1f)
                )
            } else if(showComparison) {
                Text("This book is not available in the selected comparison translation.",
                    color=Color(0xFFA8B6C9),modifier=Modifier.weight(1f))
            } else if(searchResults.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
                    items(searchResults.size) { index ->
                        val verse=searchResults[index]
                        Text(verse.reference+"  "+verse.text,color=Color.White,
                            modifier=Modifier.fillMaxWidth().padding(vertical=6.dp))
                    }
                }
            } else {
                NeaRerWordReaderScreen(
                    state=BibleReaderScreenState(selectedTranslation!!.name,selectedBook!!.name,chapter,verses,
                        hasPreviousChapter=chapter>1,hasNextChapter=chapter<selectedBook!!.chapterCount),
                    onPreviousChapter={
                        chapter=(chapter-1).coerceAtLeast(1)
                        verses=database.readChapter(selectedTranslation!!.id,selectedBook!!.id,chapter)
                    },
                    onNextChapter={
                        chapter=(chapter+1).coerceAtMost(selectedBook!!.chapterCount)
                        verses=database.readChapter(selectedTranslation!!.id,selectedBook!!.id,chapter)
                    },
                    onModeChanged={},
                    onWordSelected={verse,word->selectedWord=verse to word},
                    onVerseSelected={verse->
                        selectedVerseNumber=verse.verse
                        database.saveBookmark(BibleBookmark(
                            java.util.UUID.randomUUID().toString(),verse.translationId,verse.bookId,
                            verse.chapter,verse.verse,System.currentTimeMillis()))
                        notice="Bookmarked "+verse.reference
                    },
                    modifier=Modifier.weight(1f)
                )
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("No Bible translation installed yet",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold)
                Text("Choose a Bible pack supplied by its publisher or a trusted distributor. NeaRer verifies its checksum, declared canon and verse counts before saving it. Packs remain separate from the APK so the app stays lightweight.",
                    color=Color(0xFFA8B6C9),fontSize=12.sp,lineHeight=18.sp)
            }
        }
        Text(notice,color=Color(0xFFA8B6C9),fontSize=10.sp,lineHeight=15.sp)
    }
}
