package com.duckduckgo.autofill.impl.importing

import app.cash.turbine.test
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CredentialImporterImplTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector = mock()
    private val autofillStore: InternalAutofillStore = mock()
    private val dispatchers = coroutineTestRule.testDispatcherProvider
    private val appCoroutineScope: CoroutineScope = coroutineTestRule.testScope

    private val testee = CredentialImporterImpl(
        existingCredentialMatchDetector = existingCredentialMatchDetector,
        autofillStore = autofillStore,
        dispatchers = dispatchers,
        appCoroutineScope = appCoroutineScope,
    )

    private var nextId = 0L

    @Before
    fun before() = runTest {
        whenever(existingCredentialMatchDetector.alreadyExists(any())).thenReturn(false)
        whenever(autofillStore.saveCredentials(any(), any())).then {
            creds(id = nextId++)
        }
    }

    @Test
    fun whenImportingEmptyListThenResultIsCorrect() = runTest {
        val jobId = listOf<LoginCredentials>().import()
        jobId.assertResult(numberSkippedExpected = 0, importListSizeExpected = 0)
    }

    @Test
    fun whenImportingSingleItemNotADuplicateThenResultIsCorrect() = runTest {
        val jobId = listOf(creds()).import()
        jobId.assertResult(numberSkippedExpected = 0, importListSizeExpected = 1)
    }

    @Test
    fun whenImportingMultipleItemsNoDuplicatesThenResultIsCorrect() = runTest {
        val jobId = listOf(
            creds(username = "username1"),
            creds(username = "username2"),
        ).import()
        jobId.assertResult(numberSkippedExpected = 0, importListSizeExpected = 2)
    }

    @Test
    fun whenImportingSingleItemWhichIsADuplicateThenResultIsCorrect() = runTest {
        val duplicatedLogin = creds(username = "username")
        whenever(existingCredentialMatchDetector.alreadyExists(duplicatedLogin)).thenReturn(true)
        val jobId = listOf(duplicatedLogin).import()
        jobId.assertResult(numberSkippedExpected = 1, importListSizeExpected = 0)
    }

    @Test
    fun whenImportingMultipleItemsAllDuplicatesThenResultIsCorrect() = runTest {
        val duplicatedLogin1 = creds(username = "username1")
        val duplicatedLogin2 = creds(username = "username2")
        whenever(existingCredentialMatchDetector.alreadyExists(duplicatedLogin1)).thenReturn(true)
        whenever(existingCredentialMatchDetector.alreadyExists(duplicatedLogin2)).thenReturn(true)

        val jobId = listOf(duplicatedLogin1, duplicatedLogin2).import()
        jobId.assertResult(numberSkippedExpected = 2, importListSizeExpected = 0)
    }

    @Test
    fun whenImportingMultipleItemsSomeDuplicatesThenResultIsCorrect() = runTest {
        val duplicatedLogin1 = creds(username = "username1")
        val duplicatedLogin2 = creds(username = "username2")
        val notADuplicate = creds(username = "username3")
        whenever(existingCredentialMatchDetector.alreadyExists(duplicatedLogin1)).thenReturn(true)
        whenever(existingCredentialMatchDetector.alreadyExists(duplicatedLogin2)).thenReturn(true)
        whenever(existingCredentialMatchDetector.alreadyExists(notADuplicate)).thenReturn(false)

        val jobId = listOf(duplicatedLogin1, duplicatedLogin2, notADuplicate).import()
        jobId.assertResult(numberSkippedExpected = 2, importListSizeExpected = 1)
    }

    @Test
    fun whenAllPasswordsSkippedAlreadyBeforeImportThenResultIsCorrect() = runTest {
        val jobId = listOf<LoginCredentials>().import(originalListSize = 3)
        jobId.assertResult(numberSkippedExpected = 3, importListSizeExpected = 0)
    }

    @Test
    fun whenSomePasswordsSkippedAlreadyBeforeImportThenResultIsCorrect() = runTest {
        val jobId = listOf(creds()).import(originalListSize = 3)
        jobId.assertResult(numberSkippedExpected = 2, importListSizeExpected = 1)
    }

    private suspend fun List<LoginCredentials>.import(originalListSize: Int = this.size): String {
        return testee.import(this, originalListSize)
    }

    private suspend fun String.assertResult(
        numberSkippedExpected: Int,
        importListSizeExpected: Int,
    ) {
        testee.getImportStatus(this).test {
            with(awaitItem() as ImportResult.Finished) {
                assertEquals("Wrong number of duplicates in result", numberSkippedExpected, numberSkipped)
                assertEquals("Wrong import size in result", importListSizeExpected, savedCredentialIds.size)
            }
        }
    }

    private fun creds(
        id: Long? = null,
        domain: String? = "example.com",
        username: String? = "username",
        password: String? = "password",
        notes: String? = "notes",
        domainTitle: String? = "example title",
    ): LoginCredentials {
        return LoginCredentials(
            id = id,
            domainTitle = domainTitle,
            domain = domain,
            username = username,
            password = password,
            notes = notes,
        )
    }
}
