package com.duckduckgo.autofill.impl.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DefaultExistingCredentialMatchDetectorTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val autofillStore: InternalAutofillStore = mock()

    private val testee = DefaultExistingCredentialMatchDetector(
        autofillStore = autofillStore,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNoStoredPasswordsThenDoesNotAlreadyExist() = runTest {
        configureNoStoredPasswords()
        val creds = creds()
        assertFalse(testee.alreadyExists(creds))
    }

    @Test
    fun whenStoredPasswordsIsExactMatchThenAlreadyExists() = runTest {
        configureStoredPasswords(listOf(creds()))
        val creds = creds()
        assertTrue(testee.alreadyExists(creds))
    }

    private suspend fun configureNoStoredPasswords() {
        whenever(autofillStore.getAllCredentials()).thenReturn(flowOf(emptyList()))
    }

    private suspend fun configureStoredPasswords(credentials: List<LoginCredentials>) {
        whenever(autofillStore.getAllCredentials()).thenReturn(flowOf(credentials))
    }

    private fun creds(
        domain: String = "example.com",
        username: String = "username",
        password: String = "password",
        notes: String = "notes",
        domainTitle: String = "example title",
    ): LoginCredentials {
        return LoginCredentials(
            domainTitle = domainTitle,
            domain = domain,
            username = username,
            password = password,
            notes = notes,
        )
    }
}
