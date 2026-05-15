package com.kryptos.vault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kryptos.vault.data.Template
import com.kryptos.vault.ui.detail.EntryDetailScreen
import com.kryptos.vault.ui.edit.EntryEditScreen
import com.kryptos.vault.ui.edit.ScanResultKeys
import com.kryptos.vault.ui.list.VaultListScreen
import com.kryptos.vault.ui.lock.LockScreen
import com.kryptos.vault.ui.scan.ScanScreen
import com.kryptos.vault.ui.nfc.NfcPassportScanScreen
import com.kryptos.vault.ui.nfc.NfcCardScanScreen
import com.kryptos.vault.ui.scan.QrScanScreen
import com.kryptos.vault.ui.VaultViewModel

private object Routes {
    const val LOCK = "lock"
    const val LIST = "list"
    const val DETAIL = "detail/{id}"
    const val EDIT = "edit/{id}"
    const val SCAN = "scan/{template}"
    const val QR_SCAN = "qr_scan"
    const val NFC = "nfc/{template}"
    const val CARD_NFC = "card_nfc"
    fun detail(id: Long) = "detail/$id"
    fun edit(id: Long) = "edit/$id"
    fun scan(template: Template) = "scan/${template.name}"
    fun nfc(template: Template) = "nfc/${template.name}"
}

@Composable
fun KryptosNavGraph(unlocked: MutableState<Boolean>) {
    val nav = rememberNavController()
    val vm: VaultViewModel = viewModel(factory = remember { VaultViewModel.Factory })

    NavHost(navController = nav, startDestination = if (unlocked.value) Routes.LIST else Routes.LOCK) {
        composable(Routes.LOCK) {
            LockScreen(onUnlocked = {
                unlocked.value = true
                nav.navigate(Routes.LIST) {
                    popUpTo(Routes.LOCK) { inclusive = true }
                }
            })
        }
        composable(Routes.LIST) {
            VaultListScreen(
                viewModel = vm,
                onOpen = { nav.navigate(Routes.detail(it)) },
                onAdd = { nav.navigate(Routes.edit(0)) },
                onSignOut = {
                    unlocked.value = false
                    nav.navigate(Routes.LOCK) {
                        popUpTo(Routes.LIST) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            EntryDetailScreen(
                id = id,
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.edit(id)) },
            )
        }
        composable(
            Routes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            EntryEditScreen(
                id = id,
                viewModel = vm,
                savedStateHandle = entry.savedStateHandle,
                onDone = { nav.popBackStack() },
                onScan = { template -> nav.navigate(Routes.scan(template)) },
                onQrScan = { nav.navigate(Routes.QR_SCAN) },
                onNfcScan = { template, prefillJson ->
                    if (template == Template.PAYMENT_CARD) {
                        nav.navigate(Routes.CARD_NFC)
                    } else {
                        nav.navigate(Routes.nfc(template))
                        nav.currentBackStackEntry?.savedStateHandle
                            ?.set(ScanResultKeys.NFC_PREFILL_FIELDS_JSON, prefillJson)
                    }
                },
            )
        }
        composable(
            Routes.SCAN,
            arguments = listOf(navArgument("template") { type = NavType.StringType }),
        ) { entry ->
            val template = entry.arguments?.getString("template")
                ?.let { runCatching { Template.valueOf(it) }.getOrNull() }
                ?: return@composable
            ScanScreen(
                template = template,
                onCancel = { nav.popBackStack() },
                onApply = { parsedJson, rawText, attachment ->
                    val prev = nav.previousBackStackEntry?.savedStateHandle
                    prev?.set(ScanResultKeys.PARSED_FIELDS_JSON, parsedJson)
                    prev?.set(ScanResultKeys.RAW_TEXT, rawText)
                    prev?.set(ScanResultKeys.ATTACHMENT, attachment)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.QR_SCAN) {
            QrScanScreen(
                onCancel = { nav.popBackStack() },
                onResult = { value ->
                    val prev = nav.previousBackStackEntry?.savedStateHandle
                    prev?.set(ScanResultKeys.RAW_TEXT, value)
                    nav.popBackStack()
                }
            )
        }
        composable(
            Routes.NFC,
            arguments = listOf(navArgument("template") { type = NavType.StringType }),
        ) { entry ->
            val prefill: String? = entry.savedStateHandle[ScanResultKeys.NFC_PREFILL_FIELDS_JSON]
            NfcPassportScanScreen(
                prefillFieldsJson = prefill,
                onCancel = { nav.popBackStack() },
                onApply = { parsedJson, attachment ->
                    val prev = nav.previousBackStackEntry?.savedStateHandle
                    prev?.set(ScanResultKeys.PARSED_FIELDS_JSON, parsedJson)
                    prev?.set(ScanResultKeys.RAW_TEXT, "")
                    if (attachment != null) prev?.set(ScanResultKeys.ATTACHMENT, attachment)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.CARD_NFC) {
            NfcCardScanScreen(
                onCancel = { nav.popBackStack() },
                onApply = { parsedJson ->
                    val prev = nav.previousBackStackEntry?.savedStateHandle
                    prev?.set(ScanResultKeys.PARSED_FIELDS_JSON, parsedJson)
                    nav.popBackStack()
                }
            )
        }
    }
}
