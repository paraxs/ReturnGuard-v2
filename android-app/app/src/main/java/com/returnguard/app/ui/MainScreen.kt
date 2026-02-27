package com.returnguard.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onRunReminderNow: () -> Unit,
    onScanClick: () -> Unit,
    onShareDebugReport: (String, String) -> Unit,
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
            onShareDebugReport = onShareDebugReport,
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
    onShareDebugReport: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (NewItemDraft) -> Unit,
) {
    val requiresSaveGuard = (initialDraft?.ocrConfidence?.overallPercent ?: 100) < OCR_SAVE_GUARD_THRESHOLD
    val draftKey = listOf(
        initialDraft?.productName.orEmpty(),
        initialDraft?.merchant.orEmpty(),
        initialDraft?.purchaseDateIso.orEmpty(),
        initialDraft?.priceInput.orEmpty(),
        initialDraft?.notes.orEmpty(),
        initialDraft?.ocrConfidence?.overallPercent?.toString().orEmpty(),
        initialDraft?.ocrDebug?.rawText?.hashCode()?.toString().orEmpty(),
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
    var productConfirmed by rememberSaveable(draftKey) { mutableStateOf(!requiresSaveGuard) }
    var priceConfirmed by rememberSaveable(draftKey) { mutableStateOf(!requiresSaveGuard) }
    var showDebugPanel by rememberSaveable(draftKey) { mutableStateOf(false) }
    val canSave = productName.isNotBlank() && (!requiresSaveGuard || (productConfirmed && priceConfirmed))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Einkauf anlegen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                initialDraft?.ocrConfidence?.let { confidence ->
                    OcrConfidenceHint(confidence)
                }
                initialDraft?.ocrDebug?.let { debug ->
                    val debugDraft = initialDraft
                    FilterChip(
                        selected = showDebugPanel,
                        onClick = { showDebugPanel = !showDebugPanel },
                        label = {
                            Text(if (showDebugPanel) "OCR-Debug ausblenden" else "OCR-Debug anzeigen")
                        },
                    )
                    if (showDebugPanel) {
                        OcrDebugPanel(debug)
                    }
                    OutlinedButton(
                        onClick = {
                            val report = buildDebugShareReport(
                                draft = debugDraft,
                                currentProduct = productName,
                                currentMerchant = merchant,
                                currentPurchaseDate = purchaseDate,
                                currentPrice = price,
                                currentNotes = notes,
                            )
                            onShareDebugReport(
                                "ReturnGuard OCR Debug Report",
                                report,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Debug-Report teilen")
                    }
                }
                if (requiresSaveGuard) {
                    Card {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Unsicherer OCR-Entwurf (< ${OCR_SAVE_GUARD_THRESHOLD}%).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Bitte Produkt und Preis aktiv bestaetigen, bevor gespeichert wird.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = productConfirmed,
                                    onClick = { productConfirmed = !productConfirmed },
                                    label = { Text("Produkt geprueft") },
                                )
                                FilterChip(
                                    selected = priceConfirmed,
                                    onClick = { priceConfirmed = !priceConfirmed },
                                    label = { Text("Preis geprueft") },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = productName,
                    onValueChange = {
                        productName = it
                        if (requiresSaveGuard) {
                            productConfirmed = false
                        }
                    },
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
                    onValueChange = {
                        price = it
                        if (requiresSaveGuard) {
                            priceConfirmed = false
                        }
                    },
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
                            ocrConfidence = initialDraft?.ocrConfidence,
                            ocrDebug = initialDraft?.ocrDebug,
                        ),
                    )
                },
                enabled = canSave,
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

@Composable
private fun OcrConfidenceHint(confidence: OcrConfidence) {
    val label = when (confidence.level) {
        OcrConfidenceLevel.HIGH -> "hoch"
        OcrConfidenceLevel.MEDIUM -> "mittel"
        OcrConfidenceLevel.LOW -> "niedrig"
    }
    val toneColor = when (confidence.level) {
        OcrConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primary
        OcrConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
        OcrConfidenceLevel.LOW -> MaterialTheme.colorScheme.error
    }
    val product = confidence.fields["Produkt"]?.percent ?: 0
    val merchant = confidence.fields["Haendler"]?.percent ?: 0
    val date = confidence.fields["Kaufdatum"]?.percent ?: 0
    val price = confidence.fields["Preis"]?.percent ?: 0
    val weakFields = confidence.fields
        .filterValues { it.percent < 60 }
        .keys
        .joinToString(", ")

    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "OCR-Qualitaet: ${confidence.overallPercent}% ($label)",
                style = MaterialTheme.typography.bodyMedium,
                color = toneColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Produkt $product% | Haendler $merchant% | Kaufdatum $date% | Preis $price%",
                style = MaterialTheme.typography.bodySmall,
            )
            if (weakFields.isNotBlank()) {
                Text(
                    "Unsicher: $weakFields",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OcrDebugPanel(debug: OcrDebugInfo) {
    Card {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "OCR-Debug (Rohtext + Kandidaten)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OcrCandidateList("Produkt", debug.productCandidates)
            OcrCandidateList("Haendler", debug.merchantCandidates)
            OcrCandidateList("Kaufdatum", debug.dateCandidates)
            OcrCandidateList("Preis", debug.priceCandidates)

            Text("Rohtext", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = debug.rawText.take(OCR_DEBUG_RAW_TEXT_MAX_CHARS),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (debug.rawText.length > OCR_DEBUG_RAW_TEXT_MAX_CHARS) {
                Text(
                    "Rohtext gekuerzt auf ${OCR_DEBUG_RAW_TEXT_MAX_CHARS} Zeichen.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun OcrCandidateList(title: String, candidates: List<OcrDebugCandidate>) {
    Text("$title-Kandidaten", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    if (candidates.isEmpty()) {
        Text("-", style = MaterialTheme.typography.bodySmall)
        return
    }
    candidates.take(OCR_DEBUG_UI_CANDIDATE_LIMIT).forEachIndexed { index, candidate ->
        Text(
            text = "${index + 1}. ${candidate.value} [${candidate.score}] (${candidate.source})",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun buildDebugShareReport(
    draft: NewItemDraft,
    currentProduct: String,
    currentMerchant: String,
    currentPurchaseDate: String,
    currentPrice: String,
    currentNotes: String,
): String {
    val generatedAt = LocalDateTime.now().toString()
    val json = JSONObject().apply {
        put("generatedAt", generatedAt)
        put("app", "ReturnGuard Android")
        put("type", "ocr_debug_report")
        put(
            "ocrConfidence",
            draft.ocrConfidence?.let { confidence ->
                JSONObject().apply {
                    put("overallPercent", confidence.overallPercent)
                    put("level", confidence.level.label)
                    val fieldsJson = JSONObject()
                    confidence.fields.forEach { (name, field) ->
                        fieldsJson.put(
                            name,
                            JSONObject().apply {
                                put("percent", field.percent)
                                put("source", field.source)
                            },
                        )
                    }
                    put("fields", fieldsJson)
                }
            } ?: JSONObject.NULL,
        )
        put(
            "ocrDraft",
            JSONObject().apply {
                put("productName", draft.productName)
                put("merchant", draft.merchant)
                put("purchaseDateIso", draft.purchaseDateIso)
                put("returnDays", draft.returnDays)
                put("warrantyMonths", draft.warrantyMonths)
                put("priceInput", draft.priceInput)
                put("notes", draft.notes)
            },
        )
        put(
            "currentForm",
            JSONObject().apply {
                put("productName", currentProduct)
                put("merchant", currentMerchant)
                put("purchaseDateIso", currentPurchaseDate)
                put("priceInput", currentPrice)
                put("notes", currentNotes)
            },
        )
        put(
            "ocrDebug",
            draft.ocrDebug?.let { debug ->
                JSONObject().apply {
                    put("rawText", debug.rawText)
                    put("productCandidates", candidatesToJson(debug.productCandidates))
                    put("merchantCandidates", candidatesToJson(debug.merchantCandidates))
                    put("dateCandidates", candidatesToJson(debug.dateCandidates))
                    put("priceCandidates", candidatesToJson(debug.priceCandidates))
                }
            } ?: JSONObject.NULL,
        )
    }

    val jsonPretty = json.toString(2)
    return buildString {
        appendLine("ReturnGuard OCR Debug Report")
        appendLine("generatedAt: $generatedAt")
        appendLine("product(current): $currentProduct")
        appendLine("merchant(current): $currentMerchant")
        appendLine("purchaseDate(current): $currentPurchaseDate")
        appendLine("price(current): $currentPrice")
        appendLine()
        appendLine("JSON")
        appendLine(jsonPretty)
    }
}

private fun candidatesToJson(candidates: List<OcrDebugCandidate>): JSONArray {
    val arr = JSONArray()
    candidates.forEach { candidate ->
        arr.put(
            JSONObject().apply {
                put("value", candidate.value)
                put("score", candidate.score)
                put("source", candidate.source)
            },
        )
    }
    return arr
}

private fun formatDays(daysLeft: Int): String {
    return when {
        daysLeft < 0 -> "verpasst"
        daysLeft == 0 -> "heute"
        daysLeft == 1 -> "morgen"
        else -> "in $daysLeft Tagen"
    }
}

private const val OCR_SAVE_GUARD_THRESHOLD = 55
private const val OCR_DEBUG_UI_CANDIDATE_LIMIT = 6
private const val OCR_DEBUG_RAW_TEXT_MAX_CHARS = 4000
