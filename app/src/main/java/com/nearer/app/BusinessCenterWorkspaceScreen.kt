package com.nearer.app

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
internal fun BusinessCenterWorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("nearer_business_center", Context.MODE_PRIVATE) }
    var businessName by remember { mutableStateOf(prefs.getString("business_name", "").orEmpty().take(80)) }
    var tagline by remember { mutableStateOf(prefs.getString("tagline", "").orEmpty().take(120)) }
    var contact by remember { mutableStateOf(prefs.getString("contact", "").orEmpty().take(40)) }
    var businessCategory by remember { mutableStateOf(prefs.getString("business_category", "General").orEmpty().take(40)) }
    var serviceArea by remember { mutableStateOf(prefs.getString("service_area", "").orEmpty().take(100)) }
    var businessHours by remember { mutableStateOf(prefs.getString("business_hours", "").orEmpty().take(100)) }
    var statusMessage by remember { mutableStateOf("") }
    var photoMessage by remember { mutableStateOf("") }
    var photoImportToken by remember { mutableStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editingDraftId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var catalogueFilter by remember { mutableStateOf("ALL") }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("PRODUCT") }
    var itemCategory by remember { mutableStateOf("General") }
    var availability by remember { mutableStateOf("AVAILABLE") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var profileSaved by remember { mutableStateOf(businessName.isNotBlank()) }
    val rawDrafts = remember(context) { prefs.getString("drafts", "[]") }
    var catalogueReadOnly by remember(context) { mutableStateOf(!BusinessDraftCodec.canDecode(rawDrafts)) }
    val drafts = remember(context) {
        mutableStateListOf<BusinessDraft>().apply {
            addAll(BusinessDraftCodec.decode(rawDrafts))
        }
    }


    val visibleDrafts = drafts.filter { draft ->
        val matchesSearch = searchQuery.isBlank() ||
            draft.title.contains(searchQuery, true) ||
            draft.itemCategory.contains(searchQuery, true) ||
            draft.price.contains(searchQuery, true) ||
            draft.description.contains(searchQuery, true) ||
            draft.availability.contains(searchQuery, true)
        val matchesFilter = catalogueFilter == "ALL" || draft.kind == catalogueFilter
        matchesSearch && matchesFilter
    }

    fun editDraft(draft: BusinessDraft) {
        editingDraftId = draft.id
        title = draft.title
        price = draft.price
        description = draft.description
        category = draft.kind
        itemCategory = draft.itemCategory
        availability = draft.availability
        imageUri = draft.imageUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        showEditor = true
    }

    fun saveDrafts() {
        if (!catalogueReadOnly) {
            prefs.edit().putString("drafts", BusinessDraftCodec.encode(drafts)).apply()
        }
    }

    fun releaseImageIfUnused(uri: Uri?) {
        if (uri != null && drafts.none { it.imageUri == uri.toString() }) {
            BusinessPhotoStorage.deleteIfManaged(context, uri)
        }
    }

    fun resetEditor() {
        photoImportToken += 1
        releaseImageIfUnused(imageUri)
        title = ""
        price = ""
        description = ""
        category = "PRODUCT"
        itemCategory = "General"
        availability = "AVAILABLE"
        imageUri = null
        editingDraftId = null
        photoMessage = ""
        showEditor = false
    }

    fun nextDraftId(): Long = maxOf(System.currentTimeMillis(), (drafts.maxOfOrNull { it.id } ?: 0L) + 1L)

    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val previous = imageUri
            val requestToken = photoImportToken + 1
            photoImportToken = requestToken
            photoMessage = "Importing and preparing your photo…"
            val alreadyPersisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            val temporaryPersistableGrant = !alreadyPersisted && runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isSuccess
            scope.launch {
                try {
                    val imported = BusinessPhotoStorage.import(context, uri)
                    if (requestToken == photoImportToken && showEditor) {
                        if (imported != null) {
                            imageUri = imported
                            photoMessage = "Photo copied into NeaRer's private business storage."
                            if (previous != imported) releaseImageIfUnused(previous)
                        } else {
                            photoMessage = "NeaRer could not import that image. Choose a readable image file up to 30 MB."
                        }
                    } else if (imported != null) {
                        BusinessPhotoStorage.deleteIfManaged(context, imported)
                    }
                } finally {
                    if (temporaryPersistableGrant) {
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                }
            }
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
                        if (businessCategory.isNotBlank()) Text(businessCategory, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (serviceArea.isNotBlank()) Text("Area · $serviceArea", color = Muted, fontSize = 10.sp)
                        if (businessHours.isNotBlank()) Text("Hours · $businessHours", color = Muted, fontSize = 10.sp)
                        Text(if (profileSaved) "PROFILE SAVED ON THIS DEVICE" else "PROFILE NOT SAVED YET", color = if (profileSaved) Mint else Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("BUSINESS IDENTITY", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                TextField(value = businessName, onValueChange = { businessName = it.take(80); profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Business or brand name") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = tagline, onValueChange = { tagline = it.take(120); profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("What do you offer?") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = businessCategory, onValueChange = { businessCategory = it.take(40); profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Business category (e.g. Fashion, Food, Repairs)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = serviceArea, onValueChange = { serviceArea = it.take(100); profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Location or service area (optional)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = businessHours, onValueChange = { businessHours = it.take(100); profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Business hours (optional)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                TextField(value = contact, onValueChange = { contact = it.take(40); profileSaved = false }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Public contact number (optional)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                Button(onClick = {
                    prefs.edit()
                        .putString("business_name", businessName.trim())
                        .putString("tagline", tagline.trim())
                        .putString("contact", contact.trim())
                        .putString("business_category", businessCategory.trim())
                        .putString("service_area", serviceArea.trim())
                        .putString("business_hours", businessHours.trim())
                        .apply()
                    profileSaved = businessName.isNotBlank()
                    statusMessage = "Business identity saved on this phone."
                }, enabled = businessName.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                    Text("Save business identity", fontWeight = FontWeight.Bold)
                }
                Button(onClick = {
                    val card = buildString {
                        append(businessName.trim())
                        if (tagline.isNotBlank()) append("\n$tagline")
                        if (businessCategory.isNotBlank()) append("\nCategory: $businessCategory")
                        if (serviceArea.isNotBlank()) append("\nArea: $serviceArea")
                        if (businessHours.isNotBlank()) append("\nHours: $businessHours")
                        if (contact.isNotBlank()) append("\nContact: $contact")
                        append("\nShared via NeaRer Business")
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card), "Share business card"))
                    }.onFailure { statusMessage = "Could not open sharing: ${it.message ?: "unknown error"}" }
                }, enabled = profileSaved, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                    Text("Share business card")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("YOUR CATALOGUE", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${drafts.size}/${BusinessDraftCodec.MAX_DRAFTS} local listings", color = Muted, fontSize = 10.sp)
            }
            Button(onClick = {
                if (showEditor) {
                    resetEditor()
                } else if (drafts.size >= BusinessDraftCodec.MAX_DRAFTS) {
                    statusMessage = "Your local catalogue has reached its ${BusinessDraftCodec.MAX_DRAFTS}-listing limit."
                } else {
                    editingDraftId = null
                    title = ""
                    price = ""
                    description = ""
                    imageUri = null
                    category = "PRODUCT"
                    itemCategory = "General"
                    availability = "AVAILABLE"
                    photoMessage = ""
                    photoImportToken += 1
                    showEditor = true
                }
            }, enabled = !catalogueReadOnly && (showEditor || drafts.size < BusinessDraftCodec.MAX_DRAFTS), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                Text(if (showEditor) "Close" else "+ Add listing", fontWeight = FontWeight.Bold)
            }
        }
        if (drafts.isNotEmpty()) {
            Button(onClick = {
                val catalogueText = buildString {
                    append(businessName.ifBlank { "NeaRer Business Catalogue" })
                    if (tagline.isNotBlank()) append("\n$tagline")
                    if (businessCategory.isNotBlank()) append("\nCategory: $businessCategory")
                    if (serviceArea.isNotBlank()) append("\nArea: $serviceArea")
                    if (businessHours.isNotBlank()) append("\nHours: $businessHours")
                    if (contact.isNotBlank()) append("\nContact: $contact")
                    append("\n\nCATALOGUE")
                    drafts.forEach { item ->
                        append("\n\n• ${item.title}")
                        if (item.itemCategory.isNotBlank()) append(" · ${item.itemCategory}")
                        if (item.price.isNotBlank()) append("\nPrice: ${item.price}")
                        append("\nStatus: ${item.availability.replace('_', ' ').lowercase()}")
                        if (item.description.isNotBlank()) append("\n${item.description.take(240)}")
                    }
                    append("\n\nShared via NeaRer Business")
                }
                runCatching {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, catalogueText), "Share business catalogue"))
                }.onFailure { statusMessage = "Could not share catalogue: ${it.message ?: "unknown error"}" }
            }, enabled = profileSaved && !catalogueReadOnly, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                Text("Share catalogue summary")
            }
        }

        if (showEditor) {
            Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (editingDraftId == null) "CREATE A LISTING" else "EDIT LISTING", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    TextField(value = title, onValueChange = { title = it.take(BusinessDraftCodec.MAX_TITLE_LENGTH) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Product or service name") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PRODUCT", "SERVICE").forEach { item ->
                            Surface(color = if (category == item) Mint.copy(alpha = 0.18f) else Midnight, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { category = item }) {
                                Text(item, color = if (category == item) Mint else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                        }
                    }
                    TextField(value = itemCategory, onValueChange = { itemCategory = it.take(BusinessDraftCodec.MAX_CATEGORY_LENGTH) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Category (e.g. Fashion, Food, Repairs)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    TextField(value = price, onValueChange = { price = it.take(BusinessDraftCodec.MAX_PRICE_LENGTH) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Price (e.g. ₦15,000 or Ask for price)") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    Text("AVAILABILITY", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AVAILABLE", "MADE_TO_ORDER", "OUT_OF_STOCK").forEach { state ->
                            Surface(color = if (availability == state) Mint.copy(alpha = 0.18f) else Midnight, shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f).clickable { availability = state }) {
                                Text(state.replace('_', ' '), color = if (availability == state) Mint else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 10.dp))
                            }
                        }
                    }
                    TextField(value = description, onValueChange = { description = it.take(BusinessDraftCodec.MAX_DESCRIPTION_LENGTH) }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("Describe what makes this offer useful and special") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                    if (imageUri != null) {
                        BusinessDraftPhoto(uri = imageUri!!, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    if (photoMessage.isNotBlank()) Text(photoMessage, color = Muted, fontSize = 10.sp)

                    Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Midnight, contentColor = Mint)) {
                        Text(if (imageUri == null) "＋ Add product photo" else "Change product photo")
                    }
                    Button(
                        onClick = {
                            val newImage = imageUri?.toString().orEmpty()
                            val existingIndex = editingDraftId?.let { id -> drafts.indexOfFirst { it.id == id } } ?: -1
                            if (existingIndex >= 0) {
                                val oldImage = drafts[existingIndex].imageUri
                                drafts[existingIndex] = BusinessDraftCodec.normalize(
                                    BusinessDraft(editingDraftId!!, title, price, category, itemCategory, description, newImage, availability)
                                )
                                if (oldImage.isNotBlank() && oldImage != newImage) releaseImageIfUnused(Uri.parse(oldImage))
                                statusMessage = "Listing updated on this phone."
                            } else if (drafts.size < BusinessDraftCodec.MAX_DRAFTS) {
                                drafts.add(0, BusinessDraftCodec.normalize(
                                    BusinessDraft(nextDraftId(), title, price, category, itemCategory, description, newImage, availability)
                                ))
                                statusMessage = "Listing saved as a local draft."
                            } else {
                                statusMessage = "Your catalogue has reached its listing limit."
                            }
                            saveDrafts()
                            resetEditor()
                        },
                        enabled = !catalogueReadOnly && title.isNotBlank() && profileSaved && (editingDraftId != null || drafts.size < BusinessDraftCodec.MAX_DRAFTS),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)
                    ) {
                        Text(if (editingDraftId == null) "Save as local draft" else "Update listing", fontWeight = FontWeight.Bold)
                    }
                    if (!profileSaved) Text("Save your business identity first so your catalogue has an owner.", color = Gold, fontSize = 10.sp)
                }
            }
        }

        if (drafts.isNotEmpty()) {
            TextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Search your listings") }, shape = RoundedCornerShape(12.dp), colors = fieldColors())
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("ALL", "PRODUCT", "SERVICE").forEach { filter ->
                    Surface(color = if (catalogueFilter == filter) Mint.copy(alpha = 0.18f) else Panel, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { catalogueFilter = filter }) {
                        Text(filter, color = if (catalogueFilter == filter) Mint else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                    }
                }
            }
        }
        if (catalogueReadOnly) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Catalogue protected", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Update NeaRer to a compatible version before changing these saved listings.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else if (drafts.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Your storefront is waiting", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Create your first product or service listing. You can prepare it now and share it when you are ready.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        } else if (visibleDrafts.isEmpty()) {
            Text("No listings match this search or filter.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        } else {
            visibleDrafts.forEach { draft ->
                Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${draft.kind} · ${draft.itemCategory}", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text(draft.availability.replace('_', ' '), color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        if (draft.imageUri.isNotBlank()) {
                            BusinessDraftPhoto(uri = Uri.parse(draft.imageUri), modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(12.dp)))
                        }
                        Text(draft.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (draft.price.isNotBlank()) Text(draft.price, color = Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (draft.description.isNotBlank()) Text(draft.description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val shareText = buildString {
                                    append(draft.title)
                                    append("\nType: ${draft.kind}")
                                    if (draft.itemCategory.isNotBlank()) append("\nCategory: ${draft.itemCategory}")
                                    if (draft.price.isNotBlank()) append("\nPrice: ${draft.price}")
                                    append("\nAvailability: ${draft.availability.replace('_', ' ').lowercase()}")
                                    if (draft.description.isNotBlank()) append("\n${draft.description}")
                                    if (businessName.isNotBlank()) append("\nFrom: ${businessName}")
                                    if (businessCategory.isNotBlank()) append("\nBusiness category: ${businessCategory}")
                                    if (serviceArea.isNotBlank()) append("\nArea: ${serviceArea}")
                                    if (businessHours.isNotBlank()) append("\nHours: ${businessHours}")
                                    if (contact.isNotBlank()) append("\nContact: ${contact}")
                                    append("\nShared via NeaRer Business")
                                }
                                runCatching {
                                    val photo = draft.imageUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = if (photo != null) "image/*" else "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        if (photo != null) {
                                            putExtra(Intent.EXTRA_STREAM, photo)
                                            clipData = ClipData.newUri(context.contentResolver, draft.title, photo)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share business listing"))
                                }
                            }, enabled = profileSaved && !catalogueReadOnly, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Midnight)) {
                                Text("Share listing", fontWeight = FontWeight.Bold)
                            }
                            Text("EDIT", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !catalogueReadOnly) { editDraft(draft) }.padding(10.dp))
                            Text("DELETE", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !catalogueReadOnly) {
                                drafts.remove(draft)
                                saveDrafts()
                                if (draft.imageUri.isNotBlank()) releaseImageIfUnused(Uri.parse(draft.imageUri))
                                statusMessage = "Listing deleted from this phone."
                            }.padding(10.dp))
                        }
                    }
                }
            }
        }
        if (catalogueReadOnly) {
            Text("This catalogue was written by an unsupported or damaged version. NeaRer has preserved its stored data and disabled edits so it is not overwritten.", color = Gold, fontSize = 11.sp, lineHeight = 16.sp)
        }
        if (statusMessage.isNotBlank()) Text(statusMessage, color = Mint, fontSize = 11.sp, lineHeight = 16.sp)
        Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("LOCAL NOW. CONNECTED WHEN READY.", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Your profile and catalogue are stored on this phone. Share a business card, one listing or your text catalogue through Android's share sheet; choose Bluetooth, Quick Share or another available app. Public storefronts, online orders and payments are not simulated here.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}


@Composable
private fun BusinessDraftPhoto(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    null
                } else {
                    var sampleSize = 1
                    while (bounds.outWidth / sampleSize > 1_280 || bounds.outHeight / sampleSize > 960) {
                        sampleSize *= 2
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }
            }.getOrNull()
        }
    }
    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.DKGRAY)
            }
        },
        update = { it.setImageBitmap(bitmap) },
        modifier = modifier
    )
}
