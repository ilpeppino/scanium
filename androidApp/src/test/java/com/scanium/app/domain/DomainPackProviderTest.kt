package com.scanium.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for DomainPackProvider singleton.
 */
@RunWith(RobolectricTestRunner::class)
class DomainPackProviderTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Reset provider state before each test
        DomainPackProvider.reset()
    }

    @After
    fun teardown() {
        // Reset provider state after each test
        DomainPackProvider.reset()
    }

    @Test
    fun `initialize creates repository and engine`() {
        DomainPackProvider.initialize(context)

        assertThat(DomainPackProvider.isInitialized).isTrue()
        assertThat(DomainPackProvider.repository).isNotNull()
        assertThat(DomainPackProvider.categoryEngine).isNotNull()
    }

    @Test
    fun `initialize is idempotent`() {
        DomainPackProvider.initialize(context)
        val repository1 = DomainPackProvider.repository
        val engine1 = DomainPackProvider.categoryEngine

        // Initialize again
        DomainPackProvider.initialize(context)
        val repository2 = DomainPackProvider.repository
        val engine2 = DomainPackProvider.categoryEngine

        // Should return same instances
        assertThat(repository2).isSameInstanceAs(repository1)
        assertThat(engine2).isSameInstanceAs(engine1)
    }

    @Test
    fun `repository throws exception when not initialized`() {
        try {
            DomainPackProvider.repository
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("not initialized")
        }
    }

    @Test
    fun `categoryEngine throws exception when not initialized`() {
        try {
            DomainPackProvider.categoryEngine
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("not initialized")
        }
    }

    @Test
    fun `isInitialized returns false before initialization`() {
        assertThat(DomainPackProvider.isInitialized).isFalse()
    }

    @Test
    fun `isInitialized returns true after initialization`() {
        DomainPackProvider.initialize(context)

        assertThat(DomainPackProvider.isInitialized).isTrue()
    }

    @Test
    fun `reset clears provider state`() {
        DomainPackProvider.initialize(context)
        assertThat(DomainPackProvider.isInitialized).isTrue()

        DomainPackProvider.reset()

        assertThat(DomainPackProvider.isInitialized).isFalse()
    }

    @Test
    fun `provider can be reinitialized after reset`() {
        DomainPackProvider.initialize(context)
        val repository1 = DomainPackProvider.repository

        DomainPackProvider.reset()
        DomainPackProvider.initialize(context)
        val repository2 = DomainPackProvider.repository

        // Should be different instances after reset
        assertThat(repository2).isNotSameInstanceAs(repository1)
    }

    @Test
    fun `initialized provider has working components`() {
        DomainPackProvider.initialize(context)

        // Verify repository works (doesn't throw)
        val repository = DomainPackProvider.repository
        assertThat(repository).isNotNull()

        // Verify engine works (doesn't throw)
        val engine = DomainPackProvider.categoryEngine
        assertThat(engine).isNotNull()
    }
}
