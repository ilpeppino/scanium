package com.scanium.app.items

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItemListingStatusTest {

    @Test
    fun `all statuses have display names`() {
        ItemListingStatus.values().forEach { status ->
            assertThat(status.displayName).isNotEmpty()
            assertThat(status.description).isNotEmpty()
        }
    }

    @Test
    fun `NOT_LISTED is default status`() {
        assertThat(ItemListingStatus.NOT_LISTED.displayName).isEqualTo("Not Listed")
    }

    @Test
    fun `LISTED_ACTIVE represents successful listing`() {
        assertThat(ItemListingStatus.LISTED_ACTIVE.displayName).isEqualTo("Listed")
    }

    @Test
    fun `LISTING_IN_PROGRESS shows ongoing operation`() {
        assertThat(ItemListingStatus.LISTING_IN_PROGRESS.displayName).contains("Posting")
    }

    @Test
    fun `LISTING_FAILED indicates error`() {
        assertThat(ItemListingStatus.LISTING_FAILED.displayName).isEqualTo("Failed")
    }
}
