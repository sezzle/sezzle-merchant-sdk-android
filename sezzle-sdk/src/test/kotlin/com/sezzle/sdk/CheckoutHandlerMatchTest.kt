package com.sezzle.sdk

import android.net.Uri
import com.sezzle.sdk.checkout.CheckoutHandler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckoutHandlerMatchTest {

    @Test
    fun `exact match on custom scheme`() {
        val target = Uri.parse("sezzle-sdk://checkout/confirmed")
        val uri = Uri.parse("sezzle-sdk://checkout/confirmed")
        assertTrue(CheckoutHandler.matches(uri, target))
    }

    @Test
    fun `extra query params still match`() {
        val target = Uri.parse("poshmark-sezzle://checkout/done")
        val uri = Uri.parse("poshmark-sezzle://checkout/done?orderRef=12345&extra=true")
        assertTrue(CheckoutHandler.matches(uri, target))
    }

    @Test
    fun `case-insensitive scheme and host`() {
        val target = Uri.parse("Poshmark-Sezzle://Checkout/done")
        val uri = Uri.parse("poshmark-sezzle://checkout/done")
        assertTrue(CheckoutHandler.matches(uri, target))
    }

    @Test
    fun `different path does not match`() {
        val target = Uri.parse("sezzle-sdk://checkout/confirmed")
        val uri = Uri.parse("sezzle-sdk://checkout/cancelled")
        assertFalse(CheckoutHandler.matches(uri, target))
    }

    @Test
    fun `different scheme does not match`() {
        val target = Uri.parse("sezzle-sdk://checkout/confirmed")
        val uri = Uri.parse("other-sdk://checkout/confirmed")
        assertFalse(CheckoutHandler.matches(uri, target))
    }

    @Test
    fun `https URLs work`() {
        val target = Uri.parse("https://merchant.com/checkout/done")
        val uri = Uri.parse("https://merchant.com/checkout/done?orderRef=42")
        assertTrue(CheckoutHandler.matches(uri, target))
    }

    @Test
    fun `https different domain does not match`() {
        val target = Uri.parse("https://merchant.com/checkout/done")
        val uri = Uri.parse("https://attacker.com/checkout/done")
        assertFalse(CheckoutHandler.matches(uri, target))
    }
}
