package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private data class BusinessDraft(
    val id: Long,
    val title: String,
    val price: String,
    val category: String,
    val description: String,
    val imageUri: String
)

@Composable
internal fun BusinessCenterWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("nearer_business_center", Context.MODE_PRIVATE) }
    var businessName by remember { mutableStateOf(prefs.getString("business_name", "").orEmpty()) }
    var tagline by remember { mutableStateOf(prefs.getString("tagline", "").orEmpty()) }
    var contact by remember { mutableStateOf(prefs.getString("contact", "").orEmpty()) }
    var showEditor by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("PRODUCT") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var profileSaved by remember { mutableStateOf(businessName.isNotBlank()) }
    val drafts = remember(context) {
        mutableStateListOf<BusinessDraft>().apply {
            val array = runCatching { JSONArray(prefs.getString("drafts", "[]")) }.getOrNull()
            if (array != null) for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(BusinessDraft(item.optLong("id"), item.optString("title"), item.optString("price"), item.optString("category", "PRODUCT"), item.optString("description"), item.optString("image")))
            }
        }
    }

    fun saveDrafts() {
        val array = JSONArray()
        drafts.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title).put("price", item.price).put("category", item.category).put("description", item.description).put("image", item.imageUri))
        }
        prefs.edit().putString("drafts", array.toString()).apply()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            imageUri = uri
        }
    }

    @Composable
    fun fieldColors() = TextFieldDefaults.colors(
        focusedContainerColor = Midnight,
        unfocusedContainerColor = Midnight,
        focusedTextColor = White,
        unfocusedTextColor = White,
        cursorColor = Mint,
        focusedIndicatorColor = Mint,
        unfocusedIndicatorColor = Panel,
        focusedPlaceholderColor = Muted,
        unfocusedPlaceholderColor = Muted
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Midnight).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  MORE", color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
            Text("LOCAL BUSINESS DESK", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Surface(color = Panel, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("NEARER BUSINESS", color = White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your work deserves a beautiful storefront.", color = Mint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Create a business identity and prepare a product or service catalogue—even when you are offline. Your drafts stay on this phone until you choose to share them.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Surface(color = Midnight, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (businessName.isBlank()) "YOUR BUSINESS PROFILE" else businessName, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (tagline.isBlank()) "Add a short introduction to your business" else tagline, color = Muted, fontSize = 11.sp)
                        Text(if (profileSaved) "PROFILE SAVED ON THIS DEVICE" else "PROFILE NOT SAVED YET", color = if (profileSaved) Mint else Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("BUSINESS IDENTITY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                TextField(value = businessName, onValueChange = { businessName = it; profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Business or brand name") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = tagline, onValueChange = { tagline = it; profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What do you offer?") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = contact, onValueChange = { contact = it; profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Public contact number (optional)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                Button(onClick = {
                    prefs.edit().putString("business_name", businessName.trim()).putString("tagline", tagline.trim()).putString("contact", contact.trim()).apply()
                    profileSaved = businessName.isNotBlank()
                }, enabled = businessName.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("Save business identity", fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOUR CATALOGUE", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${drafts.size} local drafts", color = Muted, fontSize = 10.sp)
            }
            Button(onClick = { showEditor = !showEditor }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                Text(if (showEditor) "Close" else "+ Add listing", fontWeight = FontWeight.Bold)
            }
        }

        if (showEditor) {
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CREATE A LISTING", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    TextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Product or service name") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PRODUCT", "SERVICE").forEach { item ->
                            Surface(color = if (category == item) Mint.copy(alpha = 0.18f) else Midnight, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { category = item }) {
                                Text(item, color = if (category == item) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                        }
                    }
                    TextField(value = price, onValueChange = { price = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Price (e.g. ₦15,000 or Ask for price)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    TextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Describe what makes this offer useful and special") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    if (imageUri != null) {
                        AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { it.setImageURI(imageUri) }, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                        Text(if (imageUri == null) "＋ Add product photo" else "Change product photo")
                    }
                    Button(
                        onClick = {
                            drafts.add(0, BusinessDraft(System.currentTimeMillis(), title.trim(), price.trim(), category, description.trim(), imageUri?.toString().orEmpty()))
                            saveDrafts()
                            title = ""; price = ""; description = ""; imageUri = null; showEditor = false
                        },
                        enabled = title.isNotBlank() && profileSaved,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) {
                        Text("Save as local draft", fontWeight = FontWeight.Bold)
                    }
                    if (!profileSaved) Text("Save your business identity first so your catalogue has an owner.", color = Gold, fontSize = 10.sp)
                }
            }
        }

        if (drafts.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Your storefront is waiting", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Create your first product or service listing. You can prepare it now and share it when you are ready.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else {
            drafts.forEach { draft ->
                Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(draft.category, color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("LOCAL DRAFT", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        if (draft.imageUri.isNotBlank()) {
                            val uri = Uri.parse(draft.imageUri)
                            AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }, update = { it.setImageURI(uri) }, modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(12.dp)))
                        }
                        Text(draft.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (draft.price.isNotBlank()) Text(draft.price, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (draft.description.isNotBlank()) Text(draft.description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val shareText = buildString {
                                    append(draft.title)
                                    if (draft.price.isNotBlank()) append("\nPrice: ${draft.price}")
                                    if (draft.description.isNotBlank()) append("\n${draft.description}")
                                    if (businessName.isNotBlank()) append("\nFrom: ${businessName}")
                                    if (contact.isNotBlank()) append("\nContact: ${contact}")
                                    append("\nShared via NeaRer Business")
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), "Share business listing"))
                                }
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                                Text("Share listing", fontWeight = FontWeight.Bold)
                            }
                            Text("DELETE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                drafts.remove(draft); saveDrafts()
                                if (draft.imageUri.isNotBlank()) runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(draft.imageUri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            }.padding(12.dp))
                        }
                    }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("LOCAL NOW. CONNECTED WHEN READY.", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Your profile and catalogue drafts are stored on this device. Sharing uses Android's share sheet. Public storefronts, online orders, payments and discovery are later phases—not silently simulated here.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
