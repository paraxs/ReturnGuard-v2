package com.returnguard.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.returnguard.app.ui.MainScreen
import com.returnguard.app.ui.MainViewModel
import com.returnguard.app.ui.theme.ReturnGuardTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scannerOptions = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setPageLimit(5)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        val documentScanner = GmsDocumentScanning.getClient(scannerOptions)

        val scanLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                    val pageUris = scanResult?.pages?.mapNotNull { it.imageUri }.orEmpty()
                    if (pageUris.isNotEmpty()) {
                        viewModel.processScannedPages(pageUris)
                    } else {
                        viewModel.onScanPagesMissing()
                    }
                }
                Activity.RESULT_CANCELED -> Unit
                else -> {
                    viewModel.onScanStartFailed("Scanner-Rueckgabe ungueltig.")
                }
            }
        }

        val createDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri != null) {
                viewModel.exportToUri(contentResolver, uri)
            }
        }

        val openDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                viewModel.importFromUri(contentResolver, uri)
            }
        }

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* no-op */ }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ReturnGuardTheme {
                MainScreen(
                    viewModel = viewModel,
                    onExportClick = {
                        val fileName = "returnguard-backup-${LocalDate.now()}.json"
                        createDocumentLauncher.launch(fileName)
                    },
                    onImportClick = {
                        openDocumentLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onRunReminderNow = {
                        viewModel.runReminderNow()
                    },
                    onScanClick = {
                        documentScanner.getStartScanIntent(this)
                            .addOnSuccessListener { intentSender ->
                                val request = IntentSenderRequest.Builder(intentSender).build()
                                scanLauncher.launch(request)
                            }
                            .addOnFailureListener { error ->
                                viewModel.onScanStartFailed(
                                    error.message ?: "Scanner konnte nicht gestartet werden.",
                                )
                            }
                    },
                )
            }
        }
    }
}
