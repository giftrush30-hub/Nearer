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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NeaRerWordCatalogueScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val allLeads = remember { BibleLanguageCatalogue.globalPublicDomainLeads + BibleLanguageCatalogue.nigeriaLeads }
    var query by remember { mutableStateOf("") }
    var showNigeriaOnly by remember { mutableStateOf(false) }
    val filtered = allLeads.filter { lead ->
        (!showNigeriaOnly || lead.country == "Nigeria") &&
            (query.isBlank() || lead.languageName.contains(query, true) ||
                lead.publisherAvailabilityNote.contains(query, true))
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF101A2B)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("NEARER WORD · LANGUAGES", color = Color(0xFFFFD12D), fontWeight = FontWeight.Bold)
                Text("Scripture library discovery", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onBack) { Text("BACK", color = Color.White) }
        }
        Text(
            "Find translation editions and check their rights status before importing. A free download on another platform is not permission for NeaRer to redistribute it.",
            color = Color(0xFFA8B6C9), fontSize = 12.sp
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search language or edition") }, singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showNigeriaOnly = false }) { Text(if (showNigeriaOnly) "All sources" else "All sources ✓") }
            OutlinedButton(onClick = { showNigeriaOnly = true }) { Text(if (showNigeriaOnly) "Nigeria ✓" else "Nigeria") }
        }
        Text("${filtered.size} discovery leads · rights review required where shown", color = Color(0xFFA8B6C9), fontSize = 11.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(filtered, key = { it.country + ":" + it.languageName }) { lead ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F0E3))
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(lead.languageName, color = Color(0xFF15233A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (lead.nearerPackRights == BiblePackRightsStatus.PUBLIC_DOMAIN_TEXT_WITH_NAME_AND_ATTRIBUTION_CONDITIONS)
                                    "PUBLIC DOMAIN"
                                else "PERMISSION REQUIRED",
                                color = if (lead.nearerPackRights == BiblePackRightsStatus.PUBLIC_DOMAIN_TEXT_WITH_NAME_AND_ATTRIBUTION_CONDITIONS)
                                    Color(0xFF26765C) else Color(0xFF9A6500),
                                fontSize = 9.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Text(lead.country, color = Color(0xFF6A788C), fontSize = 10.sp)
                        Text(lead.publisherAvailabilityNote, color = Color(0xFF34445A), fontSize = 12.sp)
                        if (lead.country == "Nigeria") {
                            Text(
                                "Publisher-platform lead only. NeaRer has not received redistribution permission for this edition.",
                                color = Color(0xFF8A5B00), fontSize = 10.sp
                            )
                            TextButton(onClick = {
                                runCatching { uriHandler.openUri(BibleLanguageCatalogue.bsnCurrentYouVersionPageUrl) }
                            }) { Text("Check Bible Society of Nigeria source") }
                        } else {
                            Text(
                                "Use a faithful, separately verified source pack and keep its name and attribution conditions.",
                                color = Color(0xFF26765C), fontSize = 10.sp
                            )
                            TextButton(onClick = {
                                runCatching { uriHandler.openUri("https://ebible.org/") }
                            }) { Text("Open eBible.org source") }
                        }
                    }
                }
            }
        }
        Text(
            "BSN reports 27 complete Nigerian-language Bibles, but individual catalogue rows may refer to an older edition, a New Testament, a project, or a different script. Confirm coverage with the rights holder.",
            color = Color(0xFFA8B6C9), fontSize = 10.sp
        )
    }
}
