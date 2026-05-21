package com.sezzle.sdk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.sezzle.sdk.checkout.SezzleCheckoutContract
import com.sezzle.sdk.checkout.SezzleCheckoutWebViewActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SezzleCheckoutContractTest {

    private val contract = SezzleCheckoutContract()
    private val context: android.content.Context get() = RuntimeEnvironment.getApplication()

    private fun input(
        checkoutUrl: String = "https://checkout.sezzle.com/?id=abc",
        orderUuid: String? = "order-1",
        completeUrl: Uri = Uri.parse("sezzle-sdk://checkout/confirmed"),
        cancelUrl: Uri = Uri.parse("sezzle-sdk://checkout/cancelled"),
    ): SezzleCheckoutContract.Input = SezzleCheckoutContract.Input(
        checkoutUrl = checkoutUrl,
        orderUuid = orderUuid,
        completeUrl = completeUrl,
        cancelUrl = cancelUrl,
    )

    // MARK: createIntent

    @Test
    fun `createIntent encodes all input fields as extras`() {
        val input = input(
            checkoutUrl = "https://checkout.sezzle.com/?id=xyz",
            orderUuid = "order-abc",
            completeUrl = Uri.parse("https://merchant.example/complete?ref=42"),
            cancelUrl = Uri.parse("https://merchant.example/cancel"),
        )
        val intent = contract.createIntent(context, input)

        assertEquals(
            "https://checkout.sezzle.com/?id=xyz",
            intent.getStringExtra(SezzleCheckoutWebViewActivity.EXTRA_CHECKOUT_URL),
        )
        assertEquals(
            "order-abc",
            intent.getStringExtra(SezzleCheckoutWebViewActivity.EXTRA_ORDER_UUID),
        )
        assertEquals(
            "https://merchant.example/complete?ref=42",
            intent.getStringExtra(SezzleCheckoutWebViewActivity.EXTRA_COMPLETE_URL),
        )
        assertEquals(
            "https://merchant.example/cancel",
            intent.getStringExtra(SezzleCheckoutWebViewActivity.EXTRA_CANCEL_URL),
        )
    }

    @Test
    fun `createIntent does not set EXTRA_USE_LEGACY_LISTENER`() {
        val intent = contract.createIntent(context, input())
        // Default must be false — i.e. the static legacy listener won't fire for this launch.
        assertEquals(
            false,
            intent.getBooleanExtra(SezzleCheckoutWebViewActivity.EXTRA_USE_LEGACY_LISTENER, false),
        )
    }

    @Test
    fun `createIntent targets SezzleCheckoutWebViewActivity`() {
        val intent = contract.createIntent(context, input())
        assertEquals(
            SezzleCheckoutWebViewActivity::class.java.name,
            intent.component?.className,
        )
    }

    // MARK: parseResult — Complete

    @Test
    fun `parseResult returns Complete with orderUuid for SDK-creates-session flow`() {
        val intent = Intent().apply {
            putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_COMPLETE)
            putExtra(SezzleCheckoutContract.RESULT_ORDER_UUID_KEY, "order-99")
        }

        val output = contract.parseResult(Activity.RESULT_OK, intent)

        assertTrue(output is SezzleCheckoutContract.Output.Complete)
        output as SezzleCheckoutContract.Output.Complete
        assertEquals("order-99", output.orderUuid)
        assertNull(output.callbackUrl)
    }

    @Test
    fun `parseResult returns Complete with callbackUrl for server-driven flow`() {
        val intent = Intent().apply {
            putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_COMPLETE)
            putExtra(SezzleCheckoutContract.RESULT_CALLBACK_URL_KEY, "https://merchant.example/complete?ref=42")
        }

        val output = contract.parseResult(Activity.RESULT_OK, intent)

        assertTrue(output is SezzleCheckoutContract.Output.Complete)
        output as SezzleCheckoutContract.Output.Complete
        assertNull(output.orderUuid)
        assertEquals(Uri.parse("https://merchant.example/complete?ref=42"), output.callbackUrl)
    }

    // MARK: parseResult — Cancel

    @Test
    fun `parseResult returns Cancel for Cancel result type`() {
        val intent = Intent().apply {
            putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_CANCEL)
        }

        val output = contract.parseResult(Activity.RESULT_OK, intent)

        assertEquals(SezzleCheckoutContract.Output.Cancel, output)
    }

    @Test
    fun `parseResult returns Cancel for RESULT_CANCELED with null intent`() {
        // User swiped the WebView activity from Recents, or system destroyed without setResult.
        val output = contract.parseResult(Activity.RESULT_CANCELED, null)

        assertEquals(SezzleCheckoutContract.Output.Cancel, output)
    }

    // MARK: parseResult — Error

    @Test
    fun `parseResult returns Error with code and message`() {
        val intent = Intent().apply {
            putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_ERROR)
            putExtra(SezzleCheckoutContract.RESULT_ERROR_CODE_KEY, SezzleCheckoutContract.ErrorCode.NETWORK_ERROR)
            putExtra(SezzleCheckoutContract.RESULT_ERROR_MESSAGE_KEY, "DNS failure")
        }

        val output = contract.parseResult(Activity.RESULT_OK, intent)

        assertTrue(output is SezzleCheckoutContract.Output.Error)
        output as SezzleCheckoutContract.Output.Error
        assertEquals(SezzleCheckoutContract.ErrorCode.NETWORK_ERROR, output.code)
        assertEquals("DNS failure", output.message)
    }

    @Test
    fun `parseResult defaults Error code to INVALID_RESPONSE when missing`() {
        val intent = Intent().apply {
            putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_ERROR)
            putExtra(SezzleCheckoutContract.RESULT_ERROR_MESSAGE_KEY, "Boom")
        }

        val output = contract.parseResult(Activity.RESULT_OK, intent)

        assertTrue(output is SezzleCheckoutContract.Output.Error)
        output as SezzleCheckoutContract.Output.Error
        assertEquals(SezzleCheckoutContract.ErrorCode.INVALID_RESPONSE, output.code)
    }

    // MARK: parseResult — fallback

    @Test
    fun `parseResult returns NO_RESULT Error when intent has no type extra`() {
        val intent = Intent()    // RESULT_OK but missing extras — defensive case

        val output = contract.parseResult(Activity.RESULT_OK, intent)

        assertTrue(output is SezzleCheckoutContract.Output.Error)
        output as SezzleCheckoutContract.Output.Error
        assertEquals(SezzleCheckoutContract.ErrorCode.NO_RESULT, output.code)
    }

    // MARK: parseResult — SDK gate clearing

    @Test
    fun `parseResult clears SezzleSDK overlap gate`() {
        // Simulate the SDK starting a checkout via startCheckoutForResult, which sets the gate.
        // After parseResult runs (the contract's terminal hook), the gate must be cleared so the
        // next checkout call can proceed.
        val launcher = SezzleSDKTestFakeLauncher()
        SezzleSDK.startCheckoutForResult(
            launcher = launcher,
            checkoutUrl = "https://sandbox.checkout.sezzle.com/?id=abc",
            completeUrl = Uri.parse("sezzle-sdk://checkout/confirmed"),
            cancelUrl = Uri.parse("sezzle-sdk://checkout/cancelled"),
        )

        // Until parseResult fires, the gate should be held: a second call returns silently.
        SezzleSDK.startCheckoutForResult(
            launcher = launcher,
            checkoutUrl = "https://sandbox.checkout.sezzle.com/?id=second",
            completeUrl = Uri.parse("sezzle-sdk://checkout/confirmed"),
            cancelUrl = Uri.parse("sezzle-sdk://checkout/cancelled"),
        )
        assertEquals("only the first launch should fire while gate is held", 1, launcher.launches.size)

        // Terminal result arrives — parseResult clears the gate.
        val cancelIntent = Intent().apply {
            putExtra(SezzleCheckoutContract.RESULT_TYPE_KEY, SezzleCheckoutContract.RESULT_TYPE_CANCEL)
        }
        contract.parseResult(Activity.RESULT_OK, cancelIntent)

        // Next call now proceeds.
        SezzleSDK.startCheckoutForResult(
            launcher = launcher,
            checkoutUrl = "https://sandbox.checkout.sezzle.com/?id=third",
            completeUrl = Uri.parse("sezzle-sdk://checkout/confirmed"),
            cancelUrl = Uri.parse("sezzle-sdk://checkout/cancelled"),
        )
        assertEquals("gate cleared — next launch fires", 2, launcher.launches.size)
    }
}

/**
 * Minimal ActivityResultLauncher stub that records launches without actually starting
 * an activity. The SDK only calls [launch] / [unregister] on the launcher.
 */
private class SezzleSDKTestFakeLauncher : androidx.activity.result.ActivityResultLauncher<SezzleCheckoutContract.Input>() {
    val launches = mutableListOf<SezzleCheckoutContract.Input>()
    override fun launch(input: SezzleCheckoutContract.Input, options: androidx.core.app.ActivityOptionsCompat?) {
        launches += input
    }
    override fun unregister() {}
    override val contract: androidx.activity.result.contract.ActivityResultContract<SezzleCheckoutContract.Input, *> = SezzleCheckoutContract()
}
