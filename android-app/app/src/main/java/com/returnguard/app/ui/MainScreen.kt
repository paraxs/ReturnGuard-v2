package com.returnguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onRunReminderNow: () -> Unit,
    onScanClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatusMessage()
    }

    LaunchedEffect(uiState.pendingOcrDraft) {
        if (uiState.pendingOcrDraft != null) {
            showAddDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ReturnGuard Android") },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Neu") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { showAddDialog = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionRow(
                onExportClick = onExportClick,
                onImportClick = onImportClick,
                onRunReminderNow = onRunReminderNow,
                onScanClick = onScanClick,
                scanEnabled = !uiState.isOcrRunning,
            )

            if (uiState.isOcrRunning) {
                Text(
                    text = "OCR laeuft... Bitte kurz warten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Offen: ${uiState.openCount} / Gesamt: ${uiState.totalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Archiv")
                    Switch(
                        checked = uiState.showArchived,
                        onCheckedChange = viewModel::setShowArchived,
                    )
                }
            }

            HorizontalDivider()

            if (uiState.items.isEmpty()) {
                Text(
                    text = "Noch keine Einträge. Über \"Neu\" kannst du den ersten Kauf anlegen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        PurchaseCard(
                            item = item,
                            onArchiveToggle = {
                                viewModel.toggleArchive(item.id, !item.archived)
                            },
                            onDelete = {
                                viewModel.deleteItem(item.id)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            initialDraft = uiState.pendingOcrDraft,
            onDismiss = {
                viewModel.consumePendingOcrDraft()
                showAddDialog = false
            },
            onSave = { draft ->
                viewModel.addItem(draft)
                viewModel.consumePendingOcrDraft()
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ActionRow(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onRunReminderNow: () -> Unit,
    onScanClick: () -> Unit,
    scanEnabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onScanClick, enabled = scanEnabled, modifier = Modifier.fillMaxWidth()) {
            Text("Beleg scannen (OCR)")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onExportClick, modifier = Modifier.weight(1f)) {
                Text("Export")
            }
            OutlinedButton(onClick = onImportClick, modifier = Modifier.weight(1f)) {
                Text("Import")
            }
            Button(onClick = onRunReminderNow, modifier = Modifier.weight(1f)) {
                Text("Reminder")
            }
        }
    }
}

@Composable
private fun PurchaseCard(
    item: PurchaseCardUi,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.merchant.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
            Text("Kaufdatum: ${item.purchaseDateLabel}")
            Text("Rückgabe bis: ${item.returnDueLabel} (${formatDays(item.daysLeft)})")
            Text("Garantie bis: ${item.warrantyDueLabel}")
            Text("Preis: ${item.priceLabel}")
            if (item.notes.isNotBlank()) {
                Text("Notiz: ${item.notes}", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    selected = item.archived,
                    onClick = onArchiveToggle,
                    label = { Text(if (item.archived) "Archiviert" else "Offen") },
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen")
                }
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    initialDraft: NewItemDraft?,
    onDismiss: () -> Unit,
    onSave: (NewItemDraft) -> Unit,
) {
    val draftKey = listOf(
        initialDraft?.productName.orEmpty(),
        initialDraft?.merchant.orEmpty(),
        initialDraft?.purchaseDateIso.orEmpty(),
        initialDraft?.priceInput.orEmpty(),
        initialDraft?.notes.orEmpty(),
    ).joinToString("|")

    var productName by rememberSaveable(draftKey) { mutableStateOf(initialDraft?.productName.orEmpty()) }
    var merchant by rememberSaveable(draftKey) { mutableStateOf(initialDraft?.merchant.orEmpty()) }
    var purchaseDate by rememberSaveable(draftKey) {
        mutableStateOf(initialDraft?.purchaseDateIso ?: LocalDate.now().toString())
    }
    var returnDays by rememberSaveable(draftKey) { mutableStateOf(initialDraft?.returnDays ?: "14") }
    var warrantyMonths by rememberSaveable(draftKey) { mutableStateOf(initialDraft?.warrantyMonths ?: "24") }
    var price by rememberSaveable(draftKey) { mutableStateOf(initialDraft?.priceInput.orEmpty()) }
    var notes by rememberSaveable(draftKey) { mutableStateOf(initialDraft?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Einkauf anlegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Produkt*") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Händler") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = purchaseDate,
                    onValueChange = { purchaseDate = it },
                    label = { Text("Kaufdatum (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = returnDays,
                        onValueChange = { returnDays = it },
                        label = { Text("Rückgabe (Tage)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = warrantyMonths,
                        onValueChange = { warrantyMonths = it },
                        label = { Text("Garantie (Monate)") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Preis in EUR") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("Hinweis: Datum als YYYY-MM-DD eingeben.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        NewItemDraft(
                            productName = productName,
                            merchant = merchant,
                            purchaseDateIso = purchaseDate,
                            returnDays = returnDays,
                            warrantyMonths = warrantyMonths,
                            priceInput = price,
                            notes = notes,
                        ),
                    )
                },
                enabled = productName.isNotBlank(),
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
    )
}

private fun formatDays(daysLeft: Int): String {
    return when {
        daysLeft < 0 -> "verpasst"
        daysLeft == 0 -> "heute"
        daysLeft == 1 -> "morgen"
        else -> "in $daysLeft Tagen"
    }
}
