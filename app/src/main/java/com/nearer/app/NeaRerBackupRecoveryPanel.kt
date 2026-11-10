package com.nearer.app

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * SAF-based encrypted backup and explicitly confirmed local restore.
 * No broad storage permission is needed; the user chooses each document.
 */
@Composable
internal fun NeaRerBackupRecoveryPanel(chatDatabase: NeaRerChatHistoryDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveUri by remember { mutableStateOf<Uri?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordAgain by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var prepared by remember { mutableStateOf<NeaRerBackupManager.PreparedRestore?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Create an encrypted local backup or restore one from another phone.") }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            saveUri = uri
            showBackupPassword = true
        }
    }
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            restoreUri = uri
            showRestorePassword = true
        }
    }

    Surface(modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = Panel) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("LOCAL BACKUP & RECOVERY", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Keep your chats and received files safe.", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "Backups are encrypted with a password you choose. Save the backup somewhere safe and remember that password — NeaRer cannot recover it for you.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { createDocument.launch("NeaRer-backup.nrbackup") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Midnight)
                ) { Text("Back up now", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { openDocument.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Midnight)
                ) { Text("Restore backup", fontWeight = FontWeight.Bold) }
            }
            if (busy) Text("Working securely… keep NeaRer open.", color = BlueAccent, fontSize = 12.sp)
            Text(notice, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }

    if (showBackupPassword) {
        AlertDialog(
            onDismissRequest = { if (!busy) { showBackupPassword = false; backupPassword = ""; backupPasswordAgain = ""; saveUri = null } },
            title = { Text("Protect your backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use at least 12 characters. You will need this exact password to restore the backup.")
                    OutlinedTextField(
                        value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("Backup password") }, visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = backupPasswordAgain, onValueChange = { backupPasswordAgain = it },
                        label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && backupPassword.length >= 12 && backupPassword == backupPasswordAgain && saveUri != null,
                    onClick = {
                        val targetUri = saveUri ?: return@TextButton
                        val secret = backupPassword.toCharArray()
                        backupPassword = ""; backupPasswordAgain = ""
                        showBackupPassword = false
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val temp = File(context.cacheDir, "nearer-export-${UUID.randomUUID()}.nrbackup")
                                    try {
                                        val summary = NeaRerBackupManager.createEncryptedBackup(context, chatDatabase, temp, secret)
                                        val output = context.contentResolver.openOutputStream(targetUri, "w")
                                            ?: throw IOException("Could not open the selected backup destination")
                                        FileInputStream(temp).use { input -> output.use { input.copyTo(it, 64 * 1024); it.flush() } }
                                        summary
                                    } finally {
                                        temp.delete()
                                        secret.fill('\u0000')
                                    }
                                }
                            }
                            busy = false
                            notice = result.fold(
                                { "Backup saved: ${it.chatMessages} chats, ${it.receivedFiles} received files${if (it.includesProfilePhoto) ", profile photo included" else ""}." },
                                { "Backup failed: ${it.message ?: "unexpected error"}" }
                            )
                            saveUri = null
                        }
                    }
                ) { Text("Create encrypted backup") }
            },
            dismissButton = { TextButton(onClick = { showBackupPassword = false; backupPassword = ""; backupPasswordAgain = ""; saveUri = null }) { Text("Cancel") } }
        )
    }

    if (showRestorePassword) {
        AlertDialog(
            onDismissRequest = { if (!busy) { showRestorePassword = false; restorePassword = ""; restoreUri = null } },
            title = { Text("Unlock backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the password used when this backup was created.")
                    OutlinedTextField(
                        value = restorePassword, onValueChange = { restorePassword = it },
                        label = { Text("Backup password") }, visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && restorePassword.length >= 12 && restoreUri != null,
                    onClick = {
                        val sourceUri = restoreUri ?: return@TextButton
                        val secret = restorePassword.toCharArray()
                        restorePassword = ""
                        showRestorePassword = false
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    val temp = File(context.cacheDir, "nearer-import-${UUID.randomUUID()}.nrbackup")
                                    try {
                                        val input = context.contentResolver.openInputStream(sourceUri)
                                            ?: throw IOException("Could not read the selected backup")
                                        input.use { source -> FileOutputStream(temp).use { source.copyTo(it, 64 * 1024); it.fd.sync() } }
                                        NeaRerBackupManager.prepareEncryptedBackup(context, temp, secret)
                                    } finally {
                                        temp.delete()
                                        secret.fill('\u0000')
                                    }
                                }
                            }
                            busy = false
                            result.fold(
                                { staged ->
                                    prepared?.close()
                                    prepared = staged
                                    restoreUri = null
                                    notice = "Backup verified. Review its contents before choosing whether to replace this phone's local data."
                                },
                                { failure ->
                                    notice = "Could not unlock backup: ${failure.message ?: "check the password and file"}"
                                    restoreUri = null
                                }
                            )
                        }
                    }
                ) { Text("Verify backup") }
            },
            dismissButton = { TextButton(onClick = { showRestorePassword = false; restorePassword = ""; restoreUri = null }) { Text("Cancel") } }
        )
    }

    val staged = prepared
    if (staged != null) {
        val preview = staged.preview
        AlertDialog(
            onDismissRequest = { if (!busy) { prepared?.close(); prepared = null } },
            title = { Text("Review backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Profile: ${preview.displayName}", fontWeight = FontWeight.Bold)
                    Text("${preview.chatMessages} chat messages")
                    Text("${preview.receivedFiles} received files • ${preview.receivedBytes / (1024 * 1024)} MB")
                    Text(if (preview.includesProfilePhoto) "Profile photo included" else "No profile photo included")
                    Text("Device permissions, NeaRer identity credentials, relay consent and live connections will NOT be transferred.", color = Muted, fontSize = 12.sp)
                    Text("Continuing replaces local chats, profile preferences and received files on this phone.", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { showRestoreConfirmation = true }) {
                    Text("Continue to confirmation", color = Gold)
                }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { prepared?.close(); prepared = null }) { Text("Cancel") } }
        )
    }

    if (showRestoreConfirmation && prepared != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text("Replace this phone's local data?") },
            text = { Text("This will replace the current chat archive, portable profile/community data and received-files folder with the verified backup. This cannot be undone from inside NeaRer. Make sure you have a separate backup of anything you want to keep.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val toApply = prepared ?: return@TextButton
                        showRestoreConfirmation = false
                        busy = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    NeaRerBackupRestoreTransaction.apply(context, chatDatabase, toApply, explicitlyConfirmed = true)
                                }
                            }
                            toApply.close()
                            prepared = null
                            busy = false
                            result.fold(
                                {
                                    notice = "Restore complete. NeaRer is restarting to reload your recovered data."
                                    (context as? Activity)?.recreate()
                                },
                                { failure -> notice = "Restore did not complete: ${failure.message ?: "unexpected error"}" }
                            )
                        }
                    }
                ) { Text("Replace local data", color = Gold) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showRestoreConfirmation = false }) { Text("Go back") } }
        )
    }
}
