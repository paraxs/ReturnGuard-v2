package com.returnguard.app.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.returnguard.app.data.local.PurchaseEntity
import com.returnguard.app.data.local.ReturnGuardDatabase
import com.returnguard.app.data.repository.NewPurchaseInput
import com.returnguard.app.data.repository.PurchaseRepository
import com.returnguard.app.reminder.ReminderScheduler
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PurchaseRepository(
        ReturnGuardDatabase.getInstance(application).purchaseDao(),
    )

    private val allItems = MutableStateFlow<List<PurchaseEntity>>(emptyList())
    private val showArchived = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val pendingOcrDraft = MutableStateFlow<NewItemDraft?>(null)
    private val isOcrRunning = MutableStateFlow(false)

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

    val uiState: StateFlow<MainUiState> = combine(
        allItems,
        showArchived,
        statusMessage,
        pendingOcrDraft,
        isOcrRunning,
    ) { items, includeArchived, message, ocrDraft, ocrRunning ->
        val filtered = if (includeArchived) items else items.filter { !it.archived }
        MainUiState(
            items = filtered.map { it.toUi() },
            showArchived = includeArchived,
            totalCount = items.size,
            openCount = items.count { !it.archived },
            statusMessage = message,
            pendingOcrDraft = ocrDraft,
            isOcrRunning = ocrRunning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            repository.observeAll().collect { items ->
                allItems.value = items
            }
        }
    }

    fun setShowArchived(value: Boolean) {
        showArchived.value = value
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun addItem(draft: NewItemDraft) {
        if (draft.productName.isBlank()) {
            statusMessage.value = "Produktname fehlt."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.add(
                    NewPurchaseInput(
                        productName = draft.productName,
                        merchant = draft.merchant,
                        purchaseDate = parseDate(draft.purchaseDateIso),
                        returnDays = draft.returnDays.toIntOrNull() ?: 14,
                        warrantyMonths = draft.warrantyMonths.toIntOrNull() ?: 24,
                        priceCents = parsePriceCents(draft.priceInput),
                        notes = draft.notes,
                    ),
                )
            }.onSuccess {
                statusMessage.value = "Eintrag gespeichert."
            }.onFailure { error ->
                statusMessage.value = "Speichern fehlgeschlagen: ${error.message}"
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
            statusMessage.value = "Eintrag gelöscht."
        }
    }

    fun toggleArchive(id: String, archived: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setArchived(id, archived)
        }
    }

    fun exportToUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = repository.exportJson()
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                } ?: error("Datei kann nicht geschrieben werden")
            }.onSuccess {
                statusMessage.value = "Export gespeichert."
            }.onFailure { error ->
                statusMessage.value = "Export fehlgeschlagen: ${error.message}"
            }
        }
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val payload = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Datei kann nicht gelesen werden")
                repository.importJson(payload)
            }.onSuccess { count ->
                statusMessage.value = "Import erfolgreich: $count Einträge"
            }.onFailure { error ->
                statusMessage.value = "Import fehlgeschlagen: ${error.message}"
            }
        }
    }

    fun runReminderNow() {
        ReminderScheduler.runImmediate(getApplication())
        statusMessage.value = "Reminder-Check wurde gestartet."
    }

    fun onScanStartFailed(reason: String) {
        statusMessage.value = "Scanner fehlgeschlagen: $reason"
    }

    fun onScanPagesMissing() {
        statusMessage.value = "Scan abgeschlossen, aber keine Seiten gefunden."
    }

    fun consumePendingOcrDraft() {
        pendingOcrDraft.value = null
    }

    fun processScannedPages(pageUris: List<Uri>) {
        if (pageUris.isEmpty()) {
            onScanPagesMissing()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isOcrRunning.value = true
            runCatching {
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    buildString {
                        pageUris.forEachIndexed { index, uri ->
                            if (index > 0) append("\n\n")
                            val image = InputImage.fromFilePath(getApplication(), uri)
                            append(recognizer.process(image).await().text)
                        }
                    }
                } finally {
                    recognizer.close()
                }
            }.onSuccess { rawText ->
                if (rawText.isBlank()) {
                    statusMessage.value = "Kein Text im Beleg erkannt."
                    return@onSuccess
                }
                pendingOcrDraft.value = buildDraftFromOcr(rawText)
                statusMessage.value = "OCR fertig. Entwurf bitte prüfen und speichern."
            }.onFailure { error ->
                statusMessage.value = "OCR fehlgeschlagen: ${error.message}"
            }
            isOcrRunning.value = false
        }
    }

    private fun buildDraftFromOcr(rawText: String): NewItemDraft {
        val lines = rawText
            .lines()
            .map { normalizeLine(it) }
            .filter { it.isNotBlank() }

        val merchant = extractMerchant(lines)
        val productName = extractProductName(lines, merchant)
        val purchaseDateIso = extractPurchaseDateIso(lines)
        val priceInput = extractTotalPriceInput(lines)

        return NewItemDraft(
            productName = productName,
            merchant = merchant,
            purchaseDateIso = purchaseDateIso,
            returnDays = "14",
            warrantyMonths = "24",
            priceInput = priceInput,
            notes = "OCR-Entwurf automatisch erstellt. Bitte Werte pruefen.",
        )
    }

    private fun extractPurchaseDateIso(lines: List<String>): String {
        val today = LocalDate.now()
        val candidates = buildList {
            for ((index, line) in lines.withIndex()) {
                val lower = line.lowercase(Locale.ROOT)
                for (match in dateRegex.findAll(line)) {
                    val parsed = parseDateCandidate(match.groupValues[1], match.groupValues[2], match.groupValues[3]) ?: continue
                    var score = 1
                    if (index < 35) score += 1
                    if (dateHintKeywords.any { lower.contains(it) }) score += 10
                    if (nonPurchaseDateKeywords.any { lower.contains(it) }) score -= 4
                    if (parsed.isAfter(today.plusDays(7))) score -= 6
                    if (parsed.year < 2000) score -= 2
                    add(DateCandidate(parsed, score))
                }
            }
        }

        return candidates
            .maxWithOrNull(compareBy<DateCandidate> { it.score }.thenByDescending { it.date })
            ?.date
            ?.toString()
            ?: today.toString()
    }

    private fun extractTotalPriceInput(lines: List<String>): String {
        val candidates = buildList {
            for (line in lines) {
                val lower = line.lowercase(Locale.ROOT)
                for (match in amountRegex.findAll(line)) {
                    val cents = parsePriceCents(match.value) ?: continue
                    if (cents <= 0L) continue
                    var score = 1
                    if (totalAmountKeywords.any { lower.contains(it) }) score += 10
                    if (subtotalKeywords.any { lower.contains(it) }) score -= 4
                    if (line.contains("%")) score -= 2
                    add(AmountCandidate(cents, score))
                }
            }
        }

        val best = candidates.maxWithOrNull(
            compareBy<AmountCandidate> { it.score }.thenByDescending { it.cents },
        )?.cents ?: return ""

        return String.format(Locale.GERMANY, "%.2f", best / 100.0)
    }

    private fun extractMerchant(lines: List<String>): String {
        val candidates = lines.mapIndexedNotNull { index, rawLine ->
            val line = cleanupCompanyLine(rawLine)
            if (line.length < 3) return@mapIndexedNotNull null
            val lower = line.lowercase(Locale.ROOT)

            var score = 0
            if (index < 20) score += 3
            if (companyHintKeywords.any { lower.contains(it) }) score += 6
            if (line.contains("www.", ignoreCase = true)) score += 2
            if (blockedMerchantKeywords.any { lower.contains(it) }) score -= 6

            val digits = line.count { it.isDigit() }
            val letters = line.count { it.isLetter() }
            if (digits > 7) score -= 3
            if (letters < 4) score -= 3

            if (score <= 0) return@mapIndexedNotNull null
            MerchantCandidate(line, score)
        }

        return candidates
            .maxWithOrNull(compareBy<MerchantCandidate> { it.score }.thenByDescending { it.value.length })
            ?.value
            ?: "Unbekannter Haendler"
    }

    private fun extractProductName(lines: List<String>, merchant: String): String {
        val headerIndex = lines.indexOfFirst { line ->
            val lower = line.lowercase(Locale.ROOT)
            productHeaderKeywords.any { lower.contains(it) }
        }

        val scanLines = if (headerIndex >= 0) {
            lines.drop(headerIndex + 1).take(30)
        } else {
            lines.take(70)
        }

        val merchantLower = merchant.lowercase(Locale.ROOT)
        val candidates = scanLines.mapNotNull { rawLine ->
            val line = normalizeLine(rawLine)
            if (line.length < 8) return@mapNotNull null
            val lower = line.lowercase(Locale.ROOT)
            if (blockedProductKeywords.any { lower.contains(it) }) return@mapNotNull null

            var score = 0
            if (line.any { it.isDigit() } && line.any { it.isLetter() }) score += 5
            if (lower.contains("aktion")) score += 1
            if (line.contains(",")) score -= 1
            if (lower.contains(merchantLower)) score -= 4
            if (amountRegex.containsMatchIn(line)) score -= 1

            val cleaned = line
                .replace(Regex("^\\d+\\s+"), "")
                .replace(Regex("^[A-Z0-9-]{4,}\\s+"), "")
                .replace(Regex("\\s+\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})$"), "")
                .trim()

            if (cleaned.length < 6) return@mapNotNull null
            if (score <= 0) return@mapNotNull null

            ProductCandidate(cleaned, score)
        }

        return candidates
            .maxWithOrNull(compareBy<ProductCandidate> { it.score }.thenByDescending { it.value.length })
            ?.value
            ?: "Belegposition"
    }

    private fun parseDate(raw: String): LocalDate {
        return runCatching { LocalDate.parse(raw) }.getOrElse { LocalDate.now() }
    }

    private fun parsePriceCents(raw: String): Long? {
        val clean = raw.trim().replace("€", "")
        if (clean.isBlank()) return null
        val compact = clean
            .replace(" ", "")
            .replace("[^\\d,.-]".toRegex(), "")
        if (compact.isBlank()) return null
        val normalized = when {
            compact.contains(',') && compact.contains('.') -> compact.replace(".", "").replace(',', '.')
            compact.contains(',') -> compact.replace(',', '.')
            Regex("^\\d{1,3}(?:\\.\\d{3})+$").matches(compact) -> compact.replace(".", "")
            else -> compact
        }
        val value = normalized.toDoubleOrNull() ?: return null
        return (value * 100).toLong().coerceAtLeast(0)
    }

    private fun parseDateCandidate(dayRaw: String, monthRaw: String, yearRaw: String): LocalDate? {
        val day = dayRaw.toIntOrNull() ?: return null
        val month = monthRaw.toIntOrNull() ?: return null
        val yearValue = yearRaw.toIntOrNull() ?: return null
        val year = if (yearRaw.length == 2) {
            if (yearValue <= 69) 2000 + yearValue else 1900 + yearValue
        } else {
            yearValue
        }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun normalizeLine(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cleanupCompanyLine(value: String): String {
        return normalizeLine(value)
            .replace("Tel.", "", ignoreCase = true)
            .replace("Fax", "", ignoreCase = true)
            .split("|")
            .firstOrNull()
            .orEmpty()
            .trim()
    }

    private fun PurchaseEntity.toUi(): PurchaseCardUi {
        val purchaseDate = LocalDate.ofEpochDay(purchaseDateEpochDay)
        val returnDue = purchaseDate.plusDays(returnDays.toLong())
        val warrantyDue = purchaseDate.plusMonths(warrantyMonths.toLong())
        val daysLeft = returnDue.toEpochDay() - LocalDate.now().toEpochDay()

        return PurchaseCardUi(
            id = id,
            productName = productName,
            merchant = merchant,
            purchaseDateLabel = purchaseDate.format(dateFormatter),
            returnDueLabel = returnDue.format(dateFormatter),
            warrantyDueLabel = warrantyDue.format(dateFormatter),
            priceLabel = priceCents?.let { cents ->
                currencyFormatter.format(cents / 100.0)
            } ?: "-",
            archived = archived,
            daysLeft = daysLeft.toInt(),
            notes = notes,
        )
    }

    private data class DateCandidate(
        val date: LocalDate,
        val score: Int,
    )

    private data class AmountCandidate(
        val cents: Long,
        val score: Int,
    )

    private data class MerchantCandidate(
        val value: String,
        val score: Int,
    )

    private data class ProductCandidate(
        val value: String,
        val score: Int,
    )

    companion object {
        private val dateRegex = Regex("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})\\b")
        private val amountRegex = Regex("(?<!\\d)(\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})|\\d+(?:[.,]\\d{2}))(?!\\d)")

        private val dateHintKeywords = listOf(
            "datum",
            "rechnungsdatum",
            "invoice date",
            "belegdatum",
            "kaufdatum",
        )

        private val nonPurchaseDateKeywords = listOf(
            "versand",
            "liefer",
            "faellig",
            "zahlung",
        )

        private val totalAmountKeywords = listOf(
            "gesamtsumme",
            "gesamtbetrag",
            "summe (eur)",
            "zu zahlen",
            "total",
            "endbetrag",
        )

        private val subtotalKeywords = listOf(
            "zwischensumme",
            "nettobetrag",
            "nettosumme",
            "mwst",
            "mehrwertsteuer",
            "ust",
            "rabatt",
            "versand",
            "zustellung",
        )

        private val companyHintKeywords = listOf(
            "gmbh",
            "ag",
            "kg",
            "e.u.",
            "e.u",
            "inc",
            "ltd",
            "llc",
            "maschinen",
            "shop",
        )

        private val blockedMerchantKeywords = listOf(
            "rechnung",
            "datum",
            "seite",
            "uid",
            "iban",
            "artikel",
            "anzahl",
            "summe",
            "kunden",
        )

        private val productHeaderKeywords = listOf(
            "artikelbeschreibung",
            "bezeichnung",
            "position",
            "produkt",
            "item",
        )

        private val blockedProductKeywords = listOf(
            "gesamtsumme",
            "zwischensumme",
            "nettobetrag",
            "nettosumme",
            "mehrwertsteuer",
            "uid",
            "iban",
            "rechnung",
            "adresse",
            "versand",
            "zahlung",
            "www.",
        )
    }
}

data class MainUiState(
    val items: List<PurchaseCardUi> = emptyList(),
    val showArchived: Boolean = false,
    val totalCount: Int = 0,
    val openCount: Int = 0,
    val statusMessage: String? = null,
    val pendingOcrDraft: NewItemDraft? = null,
    val isOcrRunning: Boolean = false,
)

data class PurchaseCardUi(
    val id: String,
    val productName: String,
    val merchant: String,
    val purchaseDateLabel: String,
    val returnDueLabel: String,
    val warrantyDueLabel: String,
    val priceLabel: String,
    val archived: Boolean,
    val daysLeft: Int,
    val notes: String,
)

data class NewItemDraft(
    val productName: String,
    val merchant: String,
    val purchaseDateIso: String,
    val returnDays: String,
    val warrantyMonths: String,
    val priceInput: String,
    val notes: String,
)
