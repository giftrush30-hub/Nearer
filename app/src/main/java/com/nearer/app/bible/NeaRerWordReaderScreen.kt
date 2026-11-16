package com.nearer.app.bible

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BibleReaderScreenState(
    val translationName:String,val bookName:String,val chapter:Int,val verses:List<BibleVerse>,
    val mode:BibleReadingMode=BibleReadingMode.BOOK_PAGE,val hasPreviousChapter:Boolean=false,
    val hasNextChapter:Boolean=true,val isRightToLeft:Boolean=false
)

/** Isolated reader surface; the app navigation/ViewModel supplies installed verses and state. */
@Composable
fun NeaRerWordReaderScreen(
    state:BibleReaderScreenState,onPreviousChapter:()->Unit,onNextChapter:()->Unit,
    onModeChanged:(BibleReadingMode)->Unit,onWordSelected:(BibleVerse,String,Int)->Unit,
    onVerseSelected:(BibleVerse)->Unit,modifier:Modifier=Modifier
) {
    val rotation= remember { Animatable(0f) }
    var lastChapter by remember { mutableStateOf(state.chapter) }
    var flipDirection by remember { mutableStateOf(78f) }
    LaunchedEffect(state.bookName,state.chapter) {
        flipDirection=if(state.chapter>=lastChapter) 78f else -78f
        lastChapter=state.chapter
        rotation.snapTo(flipDirection)
        rotation.animateTo(0f,tween(520))
    }
    CompositionLocalProvider(LocalLayoutDirection provides
        if(state.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr) {
    Column(modifier.fillMaxSize().background(Color(0xFF101A2B)).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column {
            Text("NEARER WORD",color=Color(0xFF69E0C0),fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
            Text("${state.bookName} ${state.chapter}",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text(state.translationName,color=Color(0xFFA8B6C9),fontSize=12.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            BibleReadingMode.values().forEach { mode ->
                OutlinedButton(onClick={onModeChanged(mode)}) {
                    Text(when(mode){BibleReadingMode.BOOK_PAGE->"Book";BibleReadingMode.SCROLL->"Scroll";BibleReadingMode.STUDY->"Study"},
                        color=if(state.mode==mode) Color(0xFFFFD12D) else Color.White)
                }
            }
        }
        Card(Modifier.fillMaxWidth().weight(1f).graphicsLayer {
            rotationY=rotation.value
            cameraDistance=10*density
            transformOrigin=androidx.compose.ui.graphics.TransformOrigin(
                if(flipDirection>0f) 0f else 1f,0.5f)
            shadowElevation=(kotlin.math.abs(rotation.value)/78f)*22f*density
        },shape=RoundedCornerShape(8.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFF6F0E3)),
            elevation=CardDefaults.cardElevation(7.dp)) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text("${state.bookName.uppercase()}  •  ${state.chapter}",color=Color(0xFFC59A32),
                    fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                if(state.verses.isEmpty()) {
                    Text("This chapter is not installed on this device yet. Download a licensed Bible pack to read it offline.",
                        color=Color(0xFF6E6A61),fontFamily=FontFamily.Serif,fontSize=16.sp,lineHeight=25.sp)
                } else LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(state.verses,key={"${it.translationId}:${it.bookId}:${it.chapter}:${it.verse}"}) { verse ->
                        val annotated=remember(verse.text){buildAnnotatedString {
                            val regex=Regex("\\S+"); var end=0
                            regex.findAll(verse.text).forEach { match ->
                                append(verse.text.substring(end,match.range.first))
                                val raw=match.value
                                val word=raw.trim(',', '.', ';', ':', '!', '?', '(', ')', '[', ']', '"', '“', '”')
                                if(word.isNotBlank()) {
                                    pushStringAnnotation("word",word); pushStyle(SpanStyle(fontWeight=FontWeight.Medium))
                                    append(raw); pop(); pop()
                                } else append(raw)
                                end=match.range.last+1
                            }
                            if(end<verse.text.length) append(verse.text.substring(end))
                        }}
                        val versePrefix="${verse.verse}  "
                        ClickableText(
                            text=buildAnnotatedString {
                                pushStyle(SpanStyle(color=Color(0xFF1D2634),fontFamily=FontFamily.Serif,fontSize=18.sp,lineHeight=29.sp))
                                append(versePrefix); append(annotated); pop()
                            },
                            onClick={offset->
                                val wordOffset=offset-versePrefix.length
                                val found=if(wordOffset in 0 until annotated.length)
                                    annotated.getStringAnnotations("word",wordOffset,wordOffset+1).firstOrNull()
                                else null
                                if(found!=null) {
                                    val tokenIndex=Regex("\\S+").findAll(verse.text)
                                        .indexOfFirst { wordOffset in it.range }
                                    if(tokenIndex>=0) onWordSelected(verse,found.item,tokenIndex)
                                    else onVerseSelected(verse)
                                } else onVerseSelected(verse)
                            },modifier=Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("NEARER WORD  •  SCRIPTURE STUDY",color=Color(0xFF6E6A61),fontSize=8.sp,
                    modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            OutlinedButton(onClick=onPreviousChapter,enabled=state.hasPreviousChapter){Text("← Previous")}
            Button(onClick=onNextChapter,enabled=state.hasNextChapter){Text("Next →")}
        }
    }
    }
}
