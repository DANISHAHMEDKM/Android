/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import android.os.Parcelable
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.InProgress
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.Parcelize

interface CredentialImporter {
    suspend fun import(importList: List<LoginCredentials>, originalImportListSize: Int): String
    fun getImportStatus(jobId: String): Flow<ImportResult>

    sealed interface ImportResult : Parcelable {
        val jobId: String

        @Parcelize
        data class InProgress(
            val savedCredentialIds: List<Long>,
            val numberSkipped: Int,
            val originalImportListSize: Int,
            override val jobId: String,
        ) : ImportResult

        @Parcelize
        data class Finished(
            val savedCredentialIds: List<Long>,
            val numberSkipped: Int,
            override val jobId: String,
        ) : ImportResult
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CredentialImporterImpl @Inject constructor(
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : CredentialImporter {

    private val _importStatus = MutableSharedFlow<ImportResult>(replay = 1)
    private val mutex = Mutex()

    override suspend fun import(importList: List<LoginCredentials>, originalImportListSize: Int): String {
        val jobId = UUID.randomUUID().toString()

        mutex.withLock {
            appCoroutineScope.launch(dispatchers.io()) {
                doImportCredentials(importList, originalImportListSize, jobId)
            }
        }

        return jobId
    }

    private suspend fun doImportCredentials(
        importList: List<LoginCredentials>,
        originalImportListSize: Int,
        jobId: String,
    ) {
        val savedCredentialIds = mutableListOf<Long>()
        var skippedCredentials = originalImportListSize - importList.size

        _importStatus.emit(InProgress(savedCredentialIds, skippedCredentials, originalImportListSize, jobId))

        importList.forEach {
            if (!existingCredentialMatchDetector.alreadyExists(it)) {
                val insertedId = autofillStore.saveCredentials(it.domain!!, it)?.id

                if (insertedId != null) {
                    savedCredentialIds.add(insertedId)
                }
            } else {
                skippedCredentials++
            }

            _importStatus.emit(InProgress(savedCredentialIds, skippedCredentials, originalImportListSize, jobId))
        }

        _importStatus.emit(Finished(savedCredentialIds, skippedCredentials, jobId))
    }

    override fun getImportStatus(jobId: String): Flow<ImportResult> {
        return _importStatus.filter { result ->
            result.jobId == jobId
        }
    }
}
