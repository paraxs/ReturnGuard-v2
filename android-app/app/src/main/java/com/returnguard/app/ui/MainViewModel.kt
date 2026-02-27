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
                val draft = buildDraftFromOcr(rawText)
                pendingOcrDraft.value = draft
                val confidence = draft.ocrConfidence?.overallPercent ?: 100
                statusMessage.value = if (confidence < 65) {
                    "OCR fertig, aber unsicher (${confidence}%). Entwurf genau pruefen."
                } else {
                    "OCR fertig (${confidence}%). Entwurf pruefen und speichern."
                }
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
        val product = extractProductName(lines, merchant.value)
        val purchaseDate = extractPurchaseDateIso(lines)
        val price = extractTotalPriceInput(lines)
        val confidence = buildOcrConfidence(
            product = product,
            merchant = merchant,
            purchaseDate = purchaseDate,
            price = price,
        )
        val debugInfo = OcrDebugInfo(
            rawText = rawText.trim(),
            productCandidates = product.candidates.take(OCR_DEBUG_CANDIDATE_LIMIT),
            merchantCandidates = merchant.candidates.take(OCR_DEBUG_CANDIDATE_LIMIT),
            dateCandidates = purchaseDate.candidates.take(OCR_DEBUG_CANDIDATE_LIMIT),
            priceCandidates = price.candidates.take(OCR_DEBUG_CANDIDATE_LIMIT),
        )

        return NewItemDraft(
            productName = product.value,
            merchant = merchant.value,
            purchaseDateIso = purchaseDate.value,
            returnDays = "14",
            warrantyMonths = "24",
            priceInput = price.value,
            notes = buildOcrNotes(confidence),
            ocrConfidence = confidence,
            ocrDebug = debugInfo,
        )
    }

    private fun buildOcrNotes(confidence: OcrConfidence): String {
        val flagged = confidence.fields
            .filter { it.value.percent < 60 }
            .keys
            .joinToString(", ")
        return if (flagged.isBlank()) {
            "OCR-Entwurf automatisch erstellt. Bitte Werte pruefen."
        } else {
            "OCR-Entwurf automatisch erstellt. Unsicher: $flagged. Bitte Werte pruefen."
        }
    }

    private fun buildOcrConfidence(
        product: ProductExtraction,
        merchant: MerchantExtraction,
        purchaseDate: DateExtraction,
        price: AmountExtraction,
    ): OcrConfidence {
        val productConfidence = when (product.source) {
            ProductSource.ITEM_LINE -> normalizePercent(product.score, low = 14, high = 30)
            ProductSource.TABLE_LINE -> normalizePercent(product.score, low = 10, high = 26)
            ProductSource.FREE_TEXT -> normalizePercent(product.score, low = 5, high = 16)
            ProductSource.FALLBACK -> 18
        }
        val merchantConfidence = when (merchant.source) {
            MerchantSource.CANONICAL_MATCH -> 92
            MerchantSource.CANDIDATE -> normalizePercent(merchant.score, low = 4, high = 16)
            MerchantSource.FALLBACK -> 20
        }
        var dateConfidence = when (purchaseDate.source) {
            DateSource.HINTED_CANDIDATE -> normalizePercent(purchaseDate.score, low = 6, high = 12)
            DateSource.GENERIC_CANDIDATE -> normalizePercent(purchaseDate.score, low = 2, high = 8)
            DateSource.FALLBACK_TODAY -> 15
        }
        val today = LocalDate.now()
        if (purchaseDate.source != DateSource.FALLBACK_TODAY) {
            val parsedDate = runCatching { LocalDate.parse(purchaseDate.value) }.getOrNull()
            if (parsedDate != null) {
                if (!parsedDate.isAfter(today.plusDays(7)) && parsedDate.year in 2000..today.year) {
                    dateConfidence = dateConfidence.coerceAtLeast(60)
                }
                if (parsedDate.isBefore(today.minusYears(7))) {
                    dateConfidence -= 18
                }
            }
            if (purchaseDate.sameDateCount >= 2) dateConfidence += 12
            if (purchaseDate.candidates.size <= 3) dateConfidence += 8
            if (purchaseDate.source == DateSource.HINTED_CANDIDATE) {
                dateConfidence = dateConfidence.coerceAtLeast(78)
            }
        }
        dateConfidence = dateConfidence.coerceIn(12, 98)

        var priceConfidence = when (price.source) {
            PriceSource.STRONG_TOTAL -> normalizePercent(price.score, low = 8, high = 14)
            PriceSource.WEAK_TOTAL -> normalizePercent(price.score, low = 4, high = 10)
            PriceSource.LARGE_FALLBACK -> 50
            PriceSource.FALLBACK_EMPTY -> 10
        }
        if (price.source == PriceSource.LARGE_FALLBACK) {
            if (price.sameAmountCount >= 2) priceConfidence = priceConfidence.coerceAtLeast(70)
            if (price.hasKeywordSupport) priceConfidence = priceConfidence.coerceAtLeast(78)
            if (price.hasAnyKeywordCandidate) priceConfidence += 5
        }
        if (price.source != PriceSource.FALLBACK_EMPTY && price.value.isNotBlank()) {
            priceConfidence += 3
        }
        priceConfidence = priceConfidence.coerceIn(10, 98)

        val weighted = (
            productConfidence * 0.35 +
                merchantConfidence * 0.20 +
                dateConfidence * 0.20 +
                priceConfidence * 0.25
            ).toInt().coerceIn(0, 100)
        val level = when {
            weighted >= 80 -> OcrConfidenceLevel.HIGH
            weighted >= 60 -> OcrConfidenceLevel.MEDIUM
            else -> OcrConfidenceLevel.LOW
        }
        return OcrConfidence(
            overallPercent = weighted,
            level = level,
            fields = mapOf(
                "Produkt" to OcrFieldConfidence(productConfidence, product.source.label),
                "Haendler" to OcrFieldConfidence(merchantConfidence, merchant.source.label),
                "Kaufdatum" to OcrFieldConfidence(dateConfidence, purchaseDate.source.label),
                "Preis" to OcrFieldConfidence(priceConfidence, price.source.label),
            ),
        )
    }

    private fun normalizePercent(score: Int, low: Int, high: Int): Int {
        if (high <= low) return 50
        val ratio = (score - low).toDouble() / (high - low).toDouble()
        return (ratio * 100.0).toInt().coerceIn(15, 98)
    }

    private fun extractPurchaseDateIso(lines: List<String>): DateExtraction {
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
                    val hasHint = dateHintKeywords.any { lower.contains(it) }
                    add(DateCandidate(parsed, score, hasHint))
                }
            }
        }
        val sorted = candidates
            .sortedWith(compareByDescending<DateCandidate> { it.score }.thenByDescending { it.date })
        val debugCandidates = sorted.map {
            OcrDebugCandidate(
                value = it.date.toString(),
                score = it.score,
                source = if (it.hasHint) DateSource.HINTED_CANDIDATE.label else DateSource.GENERIC_CANDIDATE.label,
            )
        }

        val best = sorted.firstOrNull()
        return if (best == null) {
            DateExtraction(
                value = today.toString(),
                score = 0,
                source = DateSource.FALLBACK_TODAY,
                sameDateCount = 1,
                candidates = listOf(
                    OcrDebugCandidate(
                        value = today.toString(),
                        score = 0,
                        source = DateSource.FALLBACK_TODAY.label,
                    ),
                ),
            )
        } else {
            DateExtraction(
                value = best.date.toString(),
                score = best.score,
                source = if (best.hasHint) DateSource.HINTED_CANDIDATE else DateSource.GENERIC_CANDIDATE,
                sameDateCount = sorted.count { it.date == best.date },
                candidates = debugCandidates,
            )
        }
    }

    private fun extractTotalPriceInput(lines: List<String>): AmountExtraction {
        val candidates = buildList {
            for (line in lines) {
                val lower = line.lowercase(Locale.ROOT)
                val normalizedAmountLine = normalizeAmountOcrNoise(line)
                val strong = totalAmountKeywords.any { lower.contains(it) }
                val weak = weakTotalKeywords.any { lower.contains(it) }
                val hasPercent = line.contains("%")
                if (hasPercent && !strong && !weak) continue
                var baseScore = when {
                    strong -> 10
                    weak -> 4
                    else -> 1
                }
                if (subtotalKeywords.any { lower.contains(it) }) baseScore -= 6
                if (line.contains("%")) baseScore -= 3
                if (dateLikeRegex.containsMatchIn(line)) baseScore -= 3
                if (itemRowAmountRegex.containsMatchIn(line)) baseScore -= 6

                for (match in amountRegex.findAll(normalizedAmountLine)) {
                    val cents = parsePriceCents(match.value) ?: continue
                    if (cents <= 0L) continue
                    // Ignore tiny values unless line is clearly a total.
                    if (!strong && cents < 1_000L) continue
                    if (hasPercent && !strong && cents >= 5_000L) continue
                    var score = baseScore
                    if (cents >= 10_000L) score += 1
                    if (hasPercent) score -= 2
                    val source = when {
                        strong -> PriceSource.STRONG_TOTAL
                        weak -> PriceSource.WEAK_TOTAL
                        else -> PriceSource.LARGE_FALLBACK
                    }
                    add(AmountCandidate(cents, score, source, match.value))
                }
            }
        }

        val sorted = candidates.sortedWith(
            compareByDescending<AmountCandidate> { it.score }.thenByDescending { it.cents },
        )
        val debugCandidates = sorted.map {
            OcrDebugCandidate(
                value = "${formatCentsToInput(it.cents)} (raw: ${it.rawToken})",
                score = it.score,
                source = it.source.label,
            )
        }

        val best = sorted.firstOrNull() ?: return AmountExtraction(
            value = "",
            score = 0,
            source = PriceSource.FALLBACK_EMPTY,
            sameAmountCount = 0,
            hasKeywordSupport = false,
            hasAnyKeywordCandidate = false,
            candidates = listOf(
                OcrDebugCandidate(
                    value = "-",
                    score = 0,
                    source = PriceSource.FALLBACK_EMPTY.label,
                ),
            ),
        )

        val fallbackLarge = candidates
            .filter { it.cents >= 10_000L }
            .maxByOrNull { it.cents }

        val bestScore = candidates.maxOfOrNull { it.score } ?: Int.MIN_VALUE
        val lowConfidence = bestScore < 8
        val keywordPreferred = sorted.firstOrNull { it.source != PriceSource.LARGE_FALLBACK && it.score >= 2 }
        val outlierSafeFallback = sorted
            .sortedByDescending { it.cents }
            .let { byAmount ->
                if (byAmount.isEmpty()) {
                    null
                } else if (byAmount.size == 1) {
                    byAmount[0]
                } else {
                    val top = byAmount[0]
                    val second = byAmount[1]
                    if (top.cents >= second.cents * 2L && second.cents >= 1_000L) second else top
                }
            }
        val chosen = when {
            lowConfidence && keywordPreferred != null -> keywordPreferred
            lowConfidence && outlierSafeFallback != null -> outlierSafeFallback
            lowConfidence && fallbackLarge != null -> fallbackLarge
            else -> best
        }
        val sameAmountCount = candidates.count { it.cents == chosen.cents }
        val hasKeywordSupport = candidates.any {
            it.cents == chosen.cents && it.source != PriceSource.LARGE_FALLBACK
        }
        val hasAnyKeywordCandidate = candidates.any { it.source != PriceSource.LARGE_FALLBACK }
        return AmountExtraction(
            value = formatCentsToInput(chosen.cents),
            score = chosen.score,
            source = if (chosen === fallbackLarge) PriceSource.LARGE_FALLBACK else chosen.source,
            sameAmountCount = sameAmountCount,
            hasKeywordSupport = hasKeywordSupport,
            hasAnyKeywordCandidate = hasAnyKeywordCandidate,
            candidates = debugCandidates,
        )
    }

    private fun extractMerchant(lines: List<String>): MerchantExtraction {
        val candidates = lines.mapIndexedNotNull { index, rawLine ->
            val line = cleanupCompanyLine(rawLine)
            if (line.length < 3) return@mapIndexedNotNull null
            val lower = line.lowercase(Locale.ROOT)

            var score = 0
            if (index < 20) score += 3
            if (looksLikeNeureiter(lower)) score += 12
            if (companyHintKeywords.any { lower.contains(it) }) score += 6
            if (line.contains("www.", ignoreCase = true)) score += 2
            if (blockedMerchantKeywords.any { lower.contains(it) }) score -= 6
            if (isCustomerContext(lines, index)) score -= 8
            if (lower.contains(" e.u") || lower.contains(" e.u.")) score -= 3

            val digits = line.count { it.isDigit() }
            val letters = line.count { it.isLetter() }
            if (digits > 7) score -= 3
            if (letters < 4) score -= 3

            if (score <= 0) return@mapIndexedNotNull null
            MerchantCandidate(line, score)
        }

        val sorted = candidates
            .sortedWith(compareByDescending<MerchantCandidate> { it.score }.thenByDescending { it.value.length })
        val debugCandidates = sorted.map { candidate ->
            val normalized = normalizeMerchantName(candidate.value)
            val source = if (normalized == "Neureiter Maschinen GmbH") {
                MerchantSource.CANONICAL_MATCH.label
            } else {
                MerchantSource.CANDIDATE.label
            }
            OcrDebugCandidate(
                value = normalized,
                score = candidate.score,
                source = source,
            )
        }

        val rawCandidate = sorted.firstOrNull()
        val raw = rawCandidate?.value ?: "Unbekannter Haendler"
        val normalized = normalizeMerchantName(raw)
        val source = when {
            normalized == "Unbekannter Haendler" -> MerchantSource.FALLBACK
            normalized == "Neureiter Maschinen GmbH" -> MerchantSource.CANONICAL_MATCH
            else -> MerchantSource.CANDIDATE
        }
        return MerchantExtraction(
            value = normalized,
            score = rawCandidate?.score ?: 0,
            source = source,
            candidates = if (debugCandidates.isEmpty()) {
                listOf(OcrDebugCandidate(normalized, 0, MerchantSource.FALLBACK.label))
            } else {
                debugCandidates
            },
        )
    }

    private fun extractProductName(lines: List<String>, merchant: String): ProductExtraction {
        val allCandidates = mutableListOf<ProductCandidate>()

        // Candidate stream 1: structured invoice rows.
        allCandidates += lines.mapNotNull { rawLine ->
            val line = normalizeLine(rawLine)
            val match = itemLineRegex.matchEntire(line) ?: return@mapNotNull null
            val description = normalizeLine(match.groupValues[1])
            val letters = description.count { it.isLetter() }
            if (letters < 6 || description.length < 8) return@mapNotNull null
            var score = letters + 26
            if (productBoostKeywords.any { description.contains(it, ignoreCase = true) }) score += 6
            ProductCandidate(description, score, ProductSource.ITEM_LINE)
        }

        val headerIndex = lines.indexOfFirst { line ->
            val lower = line.lowercase(Locale.ROOT)
            productHeaderKeywords.any { lower.contains(it) }
        }

        // Candidate stream 2: lines near product table.
        if (headerIndex >= 0) {
            allCandidates += lines
                .drop(headerIndex + 1)
                .take(20)
                .mapNotNull { parseProductFromTableLine(it) }
        }

        val scanLines = if (headerIndex >= 0) {
            lines.drop(headerIndex + 1).take(30)
        } else {
            lines.drop(PRODUCT_FREETEXT_SKIP_TOP_LINES).take(80)
        }

        val merchantLower = merchant.lowercase(Locale.ROOT)
        val candidates = scanLines.mapNotNull { rawLine ->
            val line = normalizeLine(rawLine)
            if (line.length < 8) return@mapNotNull null
            val lower = line.lowercase(Locale.ROOT)
            val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size < 2) return@mapNotNull null
            if (headerLabelRegex.containsMatchIn(line)) return@mapNotNull null
            if (blockedProductKeywords.any { lower.contains(it) }) return@mapNotNull null
            if (codeLikeProductRegex.matches(line)) return@mapNotNull null
            if (codePairProductRegex.matches(line)) return@mapNotNull null
            if (isLikelyContactLine(line, lower)) return@mapNotNull null

            var score = 0
            if (line.any { it.isDigit() } && line.any { it.isLetter() }) score += 5
            if (lower.contains("aktion")) score += 1
            if (productBoostKeywords.any { lower.contains(it) }) score += 18
            if (line.contains(",")) score -= 1
            if (lower.contains(merchantLower)) score -= 4
            if (amountRegex.containsMatchIn(line)) score -= 1
            if (line.contains("/") && line.count { it.isDigit() } > 4) score -= 6
            if (line.contains("www.", ignoreCase = true) || line.contains("@")) score -= 8
            if (line.contains("|") && line.count { it.isDigit() } >= 6) score -= 12

            val cleaned = line
                .replace(Regex("^\\d+\\s+"), "")
                .replace(Regex("^[A-Z0-9-]{4,}\\s+"), "")
                .replace(Regex("\\s+\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})$"), "")
                .replace(Regex("^[^\\p{L}\\d]+"), "")
                .trim()

            if (cleaned.length < 6) return@mapNotNull null
            if (cleaned.split(Regex("\\s+")).size < 2) return@mapNotNull null
            if (isLikelyContactLine(cleaned)) return@mapNotNull null
            if (score < 8) return@mapNotNull null

            ProductCandidate(cleaned, score + 4, ProductSource.FREE_TEXT)
        }

        allCandidates += candidates

        val deduped = allCandidates
            .groupBy { productCandidateKey(it.value) }
            .mapNotNull { (_, grouped) ->
                grouped.maxWithOrNull(
                    compareBy<ProductCandidate> { it.score }.thenByDescending { it.value.length },
                )
            }
            .sortedWith(compareByDescending<ProductCandidate> { it.score }.thenByDescending { it.value.length })

        val best = deduped.firstOrNull()
        return if (best == null) {
            ProductExtraction(
                value = "Belegposition",
                score = 0,
                source = ProductSource.FALLBACK,
                candidates = listOf(
                    OcrDebugCandidate(
                        value = "Belegposition",
                        score = 0,
                        source = ProductSource.FALLBACK.label,
                    ),
                ),
            )
        } else {
            ProductExtraction(
                value = best.value,
                score = best.score,
                source = best.source,
                candidates = deduped.map {
                    OcrDebugCandidate(
                        value = it.value,
                        score = it.score,
                        source = it.source.label,
                    )
                },
            )
        }
    }

    private fun parseProductFromTableLine(rawLine: String): ProductCandidate? {
        val line = normalizeLine(rawLine)
        if (line.isBlank()) return null
        if (headerLabelRegex.containsMatchIn(line)) return null
        val lower = line.lowercase(Locale.ROOT)
        if (blockedProductKeywords.any { lower.contains(it) }) return null
        if (line.equals("AKTIONSPREIS", ignoreCase = true)) return null
        if (isLikelyContactLine(line, lower)) return null

        var cleaned = line
            .replace(Regex("^\\s*\\d+\\s*[A-Z]{0,4}\\s+[A-Z0-9*#_/\\-]{3,}\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*[A-Z0-9*#_/\\-]{4,}\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})(?:\\s+-?\\d{1,3}(?:[.,]\\d{2})?%?)?(?:\\s+\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2}))?\\s*$"), "")
            .replace(Regex("^[^\\p{L}\\d]+"), "")
            .trim()

        if (cleaned.split(Regex("\\s+")).size < 2) return null
        val letters = cleaned.count { it.isLetter() }
        if (letters < 6 || cleaned.length < 8) return null
        if (codeLikeProductRegex.matches(cleaned)) return null
        if (codePairProductRegex.matches(cleaned)) return null
        if (cleaned.contains("www.", ignoreCase = true) || cleaned.contains("@")) return null
        if (isLikelyContactLine(cleaned)) return null

        var score = letters
        if (productBoostKeywords.any { cleaned.contains(it, ignoreCase = true) }) score += 8
        if (cleaned.any { it.isDigit() }) score += 1
        return ProductCandidate(cleaned, score + 18, ProductSource.TABLE_LINE)
    }

    private fun isCustomerContext(lines: List<String>, index: Int): Boolean {
        val from = (index - 2).coerceAtLeast(0)
        val to = (index + 1).coerceAtMost(lines.lastIndex)
        for (i in from..to) {
            val lower = lines[i].lowercase(Locale.ROOT)
            if (lower.contains("firma") || lower.contains("kunde")) return true
        }
        return false
    }

    private fun looksLikeNeureiter(lower: String): Boolean {
        return lower.contains("neureiter") || neureiterLooseRegex.containsMatchIn(lower)
    }

    private fun normalizeMerchantName(value: String): String {
        var cleaned = normalizeLine(value)
            .replace(Regex("^[^\\p{L}\\d]+"), "")
            .replace(Regex("[^\\p{L}\\d]+$"), "")
            .trim()
        if (cleaned.lowercase(Locale.ROOT).startsWith("firma ")) {
            cleaned = cleaned.removePrefix("Firma ").removePrefix("firma ").trim()
        }
        if (looksLikeNeureiter(cleaned.lowercase(Locale.ROOT))) {
            return "Neureiter Maschinen GmbH"
        }
        return cleaned.ifBlank { "Unbekannter Haendler" }
    }

    private fun productCandidateKey(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\d]+"), " ")
            .trim()
    }

    private fun isLikelyContactLine(line: String, lowerInput: String? = null): Boolean {
        val lower = lowerInput ?: line.lowercase(Locale.ROOT)
        if (contactKeywordRegex.containsMatchIn(lower)) return true
        if (phonePatternRegex.containsMatchIn(line)) return true
        if (line.contains("|") && line.count { it.isDigit() } >= 6) return true
        return false
    }

    private fun parseDate(raw: String): LocalDate {
        return runCatching { LocalDate.parse(raw) }.getOrElse { LocalDate.now() }
    }

    private fun parsePriceCents(raw: String): Long? {
        val clean = normalizeAmountOcrNoise(raw)
            .replace("€", "", ignoreCase = true)
            .replace("eur", "", ignoreCase = true)
            .trim()
        if (clean.isBlank()) return null

        val numericEnvelope = clean
            .replace('’', '\'')
            .replace("[^\\d,.'\\-\\s]".toRegex(), "")
            .trim()
        if (numericEnvelope.none { it.isDigit() }) return null

        val compact = numericEnvelope
            .replace(" ", "")
            .replace("'", "")
            .removePrefix("+")
        if (compact.isBlank() || compact == "-") return null

        val negative = compact.startsWith("-")
        val unsigned = compact.removePrefix("-")
        if (unsigned.none { it.isDigit() }) return null

        val decimalSeparator = detectDecimalSeparator(unsigned)
        val normalized = if (decimalSeparator == null) {
            unsigned.replace(",", "").replace(".", "")
        } else {
            val sepIndex = unsigned.lastIndexOf(decimalSeparator)
            if (sepIndex <= 0 || sepIndex >= unsigned.lastIndex) return null
            val integerPart = unsigned.substring(0, sepIndex).replace(",", "").replace(".", "")
            val fractionRaw = unsigned.substring(sepIndex + 1).filter { it.isDigit() }
            if (integerPart.isBlank() || fractionRaw.isEmpty()) return null
            val fraction = when {
                fractionRaw.length >= 2 -> fractionRaw.substring(0, 2)
                else -> fractionRaw.padEnd(2, '0')
            }
            "$integerPart.$fraction"
        }

        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        val cents = kotlin.math.round(value * 100.0).toLong()
        if (negative) return null
        return cents.coerceAtLeast(0)
    }

    private fun detectDecimalSeparator(value: String): Char? {
        val lastComma = value.lastIndexOf(',')
        val lastDot = value.lastIndexOf('.')
        if (lastComma < 0 && lastDot < 0) return null

        fun hasDecimalTail(index: Int): Boolean {
            if (index <= 0 || index >= value.lastIndex) return false
            val tailDigits = value.substring(index + 1).filter { it.isDigit() }
            return tailDigits.length in 1..2
        }

        val commaDecimal = hasDecimalTail(lastComma)
        val dotDecimal = hasDecimalTail(lastDot)
        return when {
            commaDecimal && dotDecimal -> if (lastComma > lastDot) ',' else '.'
            commaDecimal -> ','
            dotDecimal -> '.'
            else -> null
        }
    }

    private fun normalizeAmountOcrNoise(value: String): String {
        val replacements = mapOf(
            'O' to '0',
            'o' to '0',
            'D' to '0',
            'Q' to '0',
            'I' to '1',
            'l' to '1',
            '|' to '1',
        )
        val chars = value.toCharArray()
        for (index in chars.indices) {
            val replacement = replacements[chars[index]] ?: continue
            val prev = chars.getOrNull(index - 1)
            val next = chars.getOrNull(index + 1)
            if (prev.isAmountNeighbor() || next.isAmountNeighbor()) {
                chars[index] = replacement
            }
        }
        return String(chars)
    }

    private fun Char?.isAmountNeighbor(): Boolean {
        return this != null && (
            this.isDigit() ||
                this == '.' ||
                this == ',' ||
                this == '\'' ||
                this == '’' ||
                this == ' '
            )
    }

    private fun formatCentsToInput(cents: Long): String {
        return String.format(Locale.GERMANY, "%.2f", cents / 100.0)
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
        val cleaned = normalizeLine(value)
            .replace("Tel.", "", ignoreCase = true)
            .replace("Fax", "", ignoreCase = true)
            .split("|")
            .firstOrNull()
            .orEmpty()
            .trim()
        return cleaned.replace(Regex("^[^\\p{L}\\d]+"), "")
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
        val hasHint: Boolean,
    )

    private data class AmountCandidate(
        val cents: Long,
        val score: Int,
        val source: PriceSource,
        val rawToken: String,
    )

    private data class MerchantCandidate(
        val value: String,
        val score: Int,
    )

    private data class ProductCandidate(
        val value: String,
        val score: Int,
        val source: ProductSource,
    )

    companion object {
        private val dateRegex = Regex("\\b(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})\\b")
        private val amountRegex = Regex(
            "(?<!\\d)(\\d{1,3}(?:[.\\s'’]\\d{3})+(?:,\\d{2})?|\\d{1,3}(?:,\\d{3})+(?:\\.\\d{2})?|\\d+(?:[.,]\\d{2}))(?!\\d)",
        )
        private val dateLikeRegex = Regex("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b")
        private val neureiterLooseRegex = Regex("n\\s*e\\s*u\\s*r\\s*e\\s*i\\s*t\\s*e\\s*r", RegexOption.IGNORE_CASE)
        private val codeLikeProductRegex = Regex("^[A-Z0-9*#_/\\-]{8,}$", RegexOption.IGNORE_CASE)
        private val codePairProductRegex = Regex("^[A-Z]{1,3}\\s+[A-Z0-9*#_/\\-]{6,}$", RegexOption.IGNORE_CASE)
        private val contactKeywordRegex = Regex("\\b(tel\\.?|fax|telefon|mobil|kontakt|www\\.|@|iban|uid|fn\\b)\\b", RegexOption.IGNORE_CASE)
        private val phonePatternRegex = Regex("\\d{2,5}[-\\s/]\\d{2,}(?:[-\\s/]\\d+)*")
        private val headerLabelRegex = Regex(
            "^(rechn(ung|ungs?)\\b.*|rechnungs?\\s*nr\\b.*|kundennummer\\b.*|auftragsnummer\\b.*|bestellnummer\\b.*|unser\\s+zeichen\\b.*|datum\\b.*|seite\\b.*|uid\\b.*)$",
            RegexOption.IGNORE_CASE,
        )
        private val itemRowAmountRegex = Regex(
            "^\\s*\\d+\\s*[A-Z]{0,4}\\s+[A-Z0-9*#_/\\-]{3,}.*\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2}).*\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})\\s*$",
            RegexOption.IGNORE_CASE,
        )
        private val itemLineRegex = Regex(
            "^\\s*\\d+\\s*(?:[A-Z]{1,4})?\\s+[A-Z0-9-]{4,}\\s+(.+?)\\s+\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2})(?:\\s+-?\\d{1,3}(?:[.,]\\d{2})?%?)?(?:\\s+\\d{1,3}(?:[.\\s]\\d{3})*(?:,\\d{2}))?\\s*$",
            RegexOption.IGNORE_CASE,
        )

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
        private val weakTotalKeywords = listOf(
            "summe",
            "gesamt",
            "betrag",
            "zahlung",
            "bankeingang",
            "total",
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

        private val productBoostKeywords = listOf(
            "mafell",
            "bosch",
            "makita",
            "erika",
            "unterflur",
            "saege",
        )
        private const val OCR_DEBUG_CANDIDATE_LIMIT = 8
        private const val PRODUCT_FREETEXT_SKIP_TOP_LINES = 14
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
    val ocrConfidence: OcrConfidence? = null,
    val ocrDebug: OcrDebugInfo? = null,
)

data class OcrConfidence(
    val overallPercent: Int,
    val level: OcrConfidenceLevel,
    val fields: Map<String, OcrFieldConfidence>,
)

data class OcrFieldConfidence(
    val percent: Int,
    val source: String,
)

data class OcrDebugInfo(
    val rawText: String,
    val productCandidates: List<OcrDebugCandidate>,
    val merchantCandidates: List<OcrDebugCandidate>,
    val dateCandidates: List<OcrDebugCandidate>,
    val priceCandidates: List<OcrDebugCandidate>,
)

data class OcrDebugCandidate(
    val value: String,
    val score: Int,
    val source: String,
)

enum class OcrConfidenceLevel(val label: String) {
    HIGH("hoch"),
    MEDIUM("mittel"),
    LOW("niedrig"),
}

private data class DateExtraction(
    val value: String,
    val score: Int,
    val source: DateSource,
    val sameDateCount: Int,
    val candidates: List<OcrDebugCandidate>,
)

private data class AmountExtraction(
    val value: String,
    val score: Int,
    val source: PriceSource,
    val sameAmountCount: Int,
    val hasKeywordSupport: Boolean,
    val hasAnyKeywordCandidate: Boolean,
    val candidates: List<OcrDebugCandidate>,
)

private data class MerchantExtraction(
    val value: String,
    val score: Int,
    val source: MerchantSource,
    val candidates: List<OcrDebugCandidate>,
)

private data class ProductExtraction(
    val value: String,
    val score: Int,
    val source: ProductSource,
    val candidates: List<OcrDebugCandidate>,
)

private enum class DateSource(val label: String) {
    HINTED_CANDIDATE("mit Datums-Hinweis"),
    GENERIC_CANDIDATE("allgemeines Datum"),
    FALLBACK_TODAY("Fallback heute"),
}

private enum class PriceSource(val label: String) {
    STRONG_TOTAL("starkes Total-Match"),
    WEAK_TOTAL("schwaches Total-Match"),
    LARGE_FALLBACK("groesster Betrag als Fallback"),
    FALLBACK_EMPTY("kein Betrag gefunden"),
}

private enum class MerchantSource(val label: String) {
    CANONICAL_MATCH("bekannter Haendler"),
    CANDIDATE("OCR-Kandidat"),
    FALLBACK("kein Haendler erkannt"),
}

private enum class ProductSource(val label: String) {
    ITEM_LINE("Tabellenposition"),
    TABLE_LINE("nahe Artikel-Tabelle"),
    FREE_TEXT("Freitext-Heuristik"),
    FALLBACK("Fallback-Wert"),
}
