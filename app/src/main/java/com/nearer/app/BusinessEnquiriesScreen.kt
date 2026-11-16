package com.nearer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
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

private data class BusinessEnquiry(
    val id:String,val customer:String,val contact:String,val listing:String,
    val message:String,val status:String,val createdAt:Long
)

@Composable
internal fun BusinessEnquiriesScreen(businessName:String,onBack:()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("nearer_business_enquiries",Context.MODE_PRIVATE) }
    val enquiries=remember(context) {
        mutableStateListOf<BusinessEnquiry>().apply {
            val saved=runCatching { JSONArray(prefs.getString("items","[]")) }.getOrNull()
            if(saved!=null) for(i in 0 until saved.length()) {
                val item=saved.optJSONObject(i) ?: continue
                add(BusinessEnquiry(item.optString("id"),item.optString("customer"),item.optString("contact"),
                    item.optString("listing"),item.optString("message"),item.optString("status","NEW"),
                    item.optLong("createdAt")))
            }
        }
    }
    var showForm by remember { mutableStateOf(false) }
    var customer by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var notice by remember { mutableStateOf("Enquiries are private and stored on this device.") }

    fun save() {
        val array=JSONArray()
        enquiries.forEach { e ->
            array.put(JSONObject().put("id",e.id).put("customer",e.customer).put("contact",e.contact)
                .put("listing",e.listing).put("message",e.message).put("status",e.status)
                .put("createdAt",e.createdAt))
        }
        prefs.edit().putString("items",array.toString()).apply()
    }
    val visible=enquiries.filter { filter=="ALL" || it.status==filter }

    Column(Modifier.fillMaxSize().background(Midnight).padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("‹  BUSINESS DESK",color=Mint,fontSize=12.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.clickable(onClick=onBack).padding(vertical=8.dp))
            Text("PRIVATE ENQUIRY INBOX",color=Gold,fontSize=9.sp,fontWeight=FontWeight.Bold)
        }
        Surface(color=Panel,shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text("Customer enquiries",color=White,fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
                Text(if(businessName.isBlank()) "Keep track of customer interest." else "Enquiries for " + businessName,
                    color=Mint,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                Text("Record calls, walk-ins and messages while offline. Prepare a reply when you are ready; these are not simulated live online messages.",
                    color=Muted,fontSize=11.sp,lineHeight=16.sp)
                Button(onClick={showForm=!showForm},modifier=Modifier.fillMaxWidth(),
                    colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Midnight)) {
                    Text(if(showForm) "Close form" else "＋  Record an enquiry",fontWeight=FontWeight.Bold)
                }
            }
        }
        if(showForm) {
            Surface(color=Panel,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextField(value=customer,onValueChange={customer=it},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,placeholder={Text("Customer name")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    TextField(value=contact,onValueChange={contact=it},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,placeholder={Text("Phone or email (optional)")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    TextField(value=listing,onValueChange={listing=it},modifier=Modifier.fillMaxWidth(),
                        singleLine=true,placeholder={Text("Product or service they asked about")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    TextField(value=message,onValueChange={message=it},modifier=Modifier.fillMaxWidth(),
                        minLines=3,placeholder={Text("What did they ask?")},colors=TextFieldDefaults.colors(
                            focusedContainerColor=Midnight,unfocusedContainerColor=Midnight,
                            focusedTextColor=White,unfocusedTextColor=White,cursorColor=Mint,
                            focusedIndicatorColor=Mint,unfocusedIndicatorColor=Panel))
                    Button(onClick={
                        enquiries.add(0,BusinessEnquiry(java.util.UUID.randomUUID().toString(),customer.trim(),
                            contact.trim(),listing.trim(),message.trim(),"NEW",System.currentTimeMillis()))
                        save();customer="";contact="";listing="";message="";showForm=false
                        notice="Enquiry saved on this device."
                    },enabled=customer.isNotBlank() && (message.isNotBlank() || listing.isNotBlank()),
                        modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Midnight)) {
                        Text("Save enquiry")
                    }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("ALL","NEW","REPLIED","CLOSED").forEach { item ->
                Surface(color=if(filter==item) Mint.copy(alpha=.18f) else Panel,
                    shape=RoundedCornerShape(9.dp),modifier=Modifier.clickable { filter=item }) {
                    Text(item,color=if(filter==item) Mint else Muted,fontSize=9.sp,fontWeight=FontWeight.Bold,
                        modifier=Modifier.padding(horizontal=10.dp,vertical=9.dp))
                }
            }
        }
        if(visible.isEmpty()) {
            Surface(color=Panel,shape=RoundedCornerShape(16.dp),modifier=Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("No enquiries here yet",color=White,fontWeight=FontWeight.Bold)
                    Text("Add a customer enquiry to keep a simple offline record of interest, follow-ups and outcomes.",
                        color=Muted,fontSize=12.sp,lineHeight=18.sp)
                }
            }
        } else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(visible,key={it.id}) { item ->
                Surface(color=Panel,shape=RoundedCornerShape(15.dp)) {
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(item.customer,color=White,fontSize=15.sp,fontWeight=FontWeight.Bold)
                            Text(item.status,color=if(item.status=="NEW") Gold else Mint,fontSize=9.sp,fontWeight=FontWeight.Bold)
                        }
                        if(item.listing.isNotBlank()) Text("Interested in: " + item.listing,color=Mint,fontSize=11.sp)
                        if(item.contact.isNotBlank()) Text(item.contact,color=Muted,fontSize=10.sp)
                        if(item.message.isNotBlank()) Text(item.message,color=White,fontSize=12.sp,lineHeight=17.sp)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick={
                                val body="Hello " + item.customer + ", thank you for contacting " +
                                    businessName.ifBlank{"us"} + ". We have received your enquiry and will get back to you shortly."
                                runCatching {
                                    val intent=if(item.contact.contains("@")) {
                                        Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:" + Uri.encode(item.contact)))
                                            .putExtra(Intent.EXTRA_SUBJECT,"Your enquiry to " + businessName)
                                            .putExtra(Intent.EXTRA_TEXT,body)
                                    } else if(item.contact.isNotBlank()) {
                                        Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:" + Uri.encode(item.contact)))
                                            .putExtra("sms_body",body)
                                    } else Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,body)
                                    context.startActivity(Intent.createChooser(intent,"Prepare customer reply"))
                                }.onSuccess { notice="Reply prepared. Review and send it in your chosen app." }
                                    .onFailure { notice="Could not open a reply app: " + it.message }
                            }) { Text("PREPARE REPLY",color=Mint,fontSize=10.sp) }
                            TextButton(onClick={
                                val index=enquiries.indexOfFirst { it.id==item.id }
                                if(index>=0) enquiries[index]=item.copy(status=if(item.status=="NEW") "REPLIED" else "CLOSED")
                                save()
                            }) { Text(if(item.status=="NEW") "MARK REPLIED" else "CLOSE",color=Gold,fontSize=10.sp) }
                            TextButton(onClick={enquiries.remove(item);save()}) { Text("DELETE",color=Muted,fontSize=10.sp) }
                        }
                    }
                }
            }
        }
        Text(notice,color=Muted,fontSize=10.sp,lineHeight=15.sp)
    }
}
