/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autofill.impl.importing.CredentialImporter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult
import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordResult.Companion.RESULT_KEY_DETAILS
import com.duckduckgo.autofill.impl.importing.gpm.webflow.ImportGooglePasswordsWebFlowViewModel.ViewState.Initializing
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class ImportGooglePasswordsWebFlowViewModel @Inject constructor() : ViewModel() {

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var credentialImporter: CredentialImporter

    @Inject
    lateinit var csvCredentialConverter: CsvCredentialConverter

    @Inject
    lateinit var autofillImportConfigStore: AutofillImportPasswordConfigStore

    private val _viewState = MutableStateFlow<ViewState>(Initializing)
    val viewState: StateFlow<ViewState> = _viewState

    fun onViewCreated() {
        viewModelScope.launch(dispatchers.io()) {
            _viewState.value = ViewState.LoadStartPage(autofillImportConfigStore.getConfig().launchUrlGooglePasswords)
        }
    }

    fun onPageStarted(url: String?) {
        Timber.i("onPageStarted: $url")
    }

    fun onPageFinished(url: String?) {
        Timber.i("onPageFinished: $url")
    }

    suspend fun onCsvAvailable(csv: String) {
        when (val parseResult = csvCredentialConverter.readCsv(csv)) {
            is CsvCredentialImportResult.Success -> onCsvParsed(parseResult)
            is CsvCredentialImportResult.Error -> onCsvError()
        }
    }

    private suspend fun onCsvParsed(parseResult: CsvCredentialImportResult.Success) {
        val jobId =
            credentialImporter.import(parseResult.loginCredentialsToImport, parseResult.numberCredentialsInSource)
        val resultBundle = Bundle().also {
            it.putParcelable(
                RESULT_KEY_DETAILS,
                ImportGooglePasswordResult.Success(
                    importedCount = parseResult.loginCredentialsToImport.size,
                    foundInImport = parseResult.loginCredentialsToImport.size,
                    importJobId = jobId,
                ),
            )
        }
        _viewState.value = ViewState.UserFinishedImportFlow(resultBundle)
    }

    fun onCsvError() {
        Timber.e("cdr Error decoding CSV")
        val resultBundle = Bundle().also {
            it.putParcelable(RESULT_KEY_DETAILS, ImportGooglePasswordResult.Error)
        }
        _viewState.value = ViewState.UserFinishedImportFlow(resultBundle)
    }

    fun onCloseButtonPressed(url: String?) {
        if (url?.startsWith(ENCRYPTED_PASSPHRASE_ERROR_URL) == true) {
            val resultBundle = Bundle().also {
                it.putParcelable(RESULT_KEY_DETAILS, ImportGooglePasswordResult.Error)
            }
            _viewState.value = ViewState.UserFinishedCannotImport(resultBundle)
        } else {
            terminateFlowAsCancellation(url ?: "unknown")
        }
    }

    fun onBackButtonPressed(
        url: String?,
        canGoBack: Boolean,
    ) {
        // if WebView can't go back, then we're at the first stage or something's gone wrong. Either way, time to cancel out of the screen.
        if (!canGoBack) {
            terminateFlowAsCancellation(url ?: "unknown")
            return
        }

        _viewState.value = ViewState.NavigatingBack
    }

    private fun terminateFlowAsCancellation(stage: String) {
        _viewState.value = ViewState.UserCancelledImportFlow(stage)
    }

    fun firstPageLoading() {
        _viewState.value = ViewState.WebContentShowing
    }

    sealed interface ViewState {
        data object Initializing : ViewState
        data object WebContentShowing : ViewState
        data class LoadStartPage(val initialLaunchUrl: String) : ViewState
        data class UserCancelledImportFlow(val stage: String) : ViewState
        data class UserFinishedImportFlow(val bundle: Bundle) : ViewState
        data class UserFinishedCannotImport(val bundle: Bundle) : ViewState
        data object NavigatingBack : ViewState
    }

    sealed interface BackButtonAction {
        data object NavigateBack : BackButtonAction
    }

    companion object {
        private const val ENCRYPTED_PASSPHRASE_ERROR_URL = "https://passwords.google.com/error/sync-passphrase"
    }
}
