package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private data class CreatorVideoDraft(val uri:Uri,val title:String,val description:String,val addedAt:Long)

@Composable
internal fun MoviesCreatorWorkspaceScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("nearer_movies_creator",Context.MODE_PRIVATE)}
    var channelName by remember { mutableStateOf(prefs.getString("channel_name","").orEmpty()) }
    var channelAbout by remember { mutableStateOf(prefs.getString("channel_about","").orEmpty()) }
    var channelCategory by remember { mutableStateOf(prefs.getString("channel_category","Entertainment").orEmpty()) }
    var showProfile by remember { mutableStateOf(channelName.isBlank()) }
    var notice by remember { mutableStateOf("Creator drafts are stored privately on this phone.") }
    val drafts=remember(context){
        mutableStateListOf<CreatorVideoDraft>().apply {
            val saved=runCatching{JSONArray(prefs.getString("video_drafts","[]"))}.getOrNull()
            if(saved!=null) for(i in 0 until saved.length()){
                val item=saved.optJSONObject(i)?:continue
                val uri=runCatching{Uri.parse(item.optString("uri"))}.getOrNull()?:continue
                val permitted=context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isReadPermission}
                if(permitted) add(CreatorVideoDraft(uri,item.optString("title"),item.optString("description"),item.optLong("addedAt")))
            }
        }
    }
    fun saveDrafts(){
        val array=JSONArray()
        drafts.forEach{array.put(JSONObject().put("uri",it.uri.toString()).put("title",it.title)
            .put("description",it.description).put("addedAt",it.addedAt))}
        prefs.edit().putString("video_drafts",array.toString()).apply()
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
        uris.forEach{uri->
            runCatching{
                context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if(drafts.none{it.uri==uri}){
                    val title=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{
                        if(it.moveToFirst()) it.getString(0) else null
                    }?:uri.lastPathSegment?.substringAfterLast('/')?:"Untitled video"
                    drafts.add(0,CreatorVideoDraft(uri,title.substringBeforeLast('.',title),"",System.currentTimeMillis()))
                }
            }
        }
        saveDrafts()
        notice=uris.size.toString()+" video(s) added to private creator drafts."
    }
    Column(Modifier.fillMaxSize().background(Midnight).padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text("‹  MOVIES",color=Mint,fontSize=12.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.clickable(onClick=onBack).padding(vertical=8.dp))
            Text("CREATOR WORKSPACE",color=Gold,fontSize=9.sp,fontWeight=FontWeight.Bold)
        }
        Surface(color=Panel,shape=RoundedCornerShape(20.dp)){
            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Your channel",color=White,fontSize=21.sp,fontWeight=FontWeight.ExtraBold)
                Text(if(channelName.isBlank())"Set up a creator identity" else channelName,color=Mint,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text(if(channelAbout.isBlank())"A home for your films, skits, lessons and creative work." else channelAbout,
                    color=Muted,fontSize=11.sp,lineHeight=16.sp)
                Text("CATEGORY · "+channelCategory,color=Gold,fontSize=9.sp,fontWeight=FontWeight.Bold)
                Button(onClick={showProfile=!showProfile},modifier=Modifier.fillMaxWidth(),
                    colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Midnight)){
                    Text(if(showProfile)"Close channel editor" else "Edit channel profile")
                }
                if(showProfile){
                    TextField(value=channelName,onValueChange={channelName=it},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,placeholder={Text("Channel name")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    TextField(value=channelCategory,onValueChange={channelCategory=it},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,placeholder={Text("Category")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    TextField(value=channelAbout,onValueChange={channelAbout=it},modifier=Modifier.fillMaxWidth(),
                        minLines=2,placeholder={Text("Describe your channel")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    Button(onClick={
                        prefs.edit().putString("channel_name",channelName.trim())
                            .putString("channel_category",channelCategory.trim())
                            .putString("channel_about",channelAbout.trim()).apply()
                        showProfile=false;notice="Creator profile saved locally."
                    },enabled=channelName.isNotBlank(),modifier=Modifier.fillMaxWidth(),
                        colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Midnight)){
                        Text("Save channel profile")
                    }
                }
                TextButton(onClick={
                    val card="NEARER MOVIES CHANNEL\n"+channelName+"\n"+channelAbout+
                        "\nCategory: "+channelCategory+"\nCreator profile shared from NeaRer."
                    runCatching{context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,card),
                        "Share creator channel"))}
                },enabled=channelName.isNotBlank()){Text("SHARE CHANNEL CARD",color=Mint)}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column {
                Text("VIDEO DRAFTS",color=White,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(drafts.size.toString()+" private drafts",color=Muted,fontSize=10.sp)
            }
            Button(onClick={picker.launch(arrayOf("video/*"))},
                colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Midnight)){
                Text("＋ Add videos")
            }
        }
        if(drafts.isEmpty()){
            Surface(color=Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f).fillMaxWidth()){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("Start your first production",color=White,fontWeight=FontWeight.Bold)
                    Text("Add a video, prepare its title and description, then share it through an app you choose. Public hosting and discovery will be connected only when NeaRer has a real publishing service.",
                        color=Muted,fontSize=12.sp,lineHeight=18.sp)
                }
            }
        }else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(drafts,key={it.uri.toString()}){draft->
                Surface(color=Panel,shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(draft.title,color=White,fontSize=14.sp,fontWeight=FontWeight.Bold)
                        Text("PRIVATE CREATOR DRAFT",color=Gold,fontSize=9.sp,fontWeight=FontWeight.Bold)
                        Text(draft.description.ifBlank{"Add a description before sharing."},color=Muted,fontSize=11.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            TextButton(onClick={
                                val index=drafts.indexOfFirst{it.uri==draft.uri}
                                if(index>=0) drafts[index]=draft.copy(description=if(draft.description.isBlank())"Created with NeaRer Movies." else "")
                                saveDrafts()
                            }){Text(if(draft.description.isBlank())"ADD DESCRIPTION" else "CLEAR DESCRIPTION",color=Mint,fontSize=9.sp)}
                            TextButton(onClick={
                                runCatching{
                                    val send=Intent(Intent.ACTION_SEND).setType("video/*")
                                        .putExtra(Intent.EXTRA_STREAM,draft.uri)
                                        .putExtra(Intent.EXTRA_TEXT,draft.title+"\n"+draft.description+"\nShared via NeaRer Movies")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(Intent.createChooser(send,"Share video draft"))
                                }.onSuccess{notice="Share sheet opened. Choose where to send your video."}
                                    .onFailure{notice="Could not share this video: "+it.message}
                            }){Text("SHARE VIDEO",color=Gold,fontSize=9.sp)}
                            TextButton(onClick={
                                runCatching{context.contentResolver.releasePersistableUriPermission(draft.uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
                                drafts.remove(draft);saveDrafts()
                            }){Text("REMOVE",color=Muted,fontSize=9.sp)}
                        }
                    }
                }
            }
        }
        Text(notice,color=Muted,fontSize=10.sp,lineHeight=15.sp)
    }
}
