package com.sezzle.sdk

import android.content.Intent
import com.sezzle.sdk.checkout.SezzleCheckoutContract
import com.sezzle.sdk.checkout.SezzleCheckoutWebViewActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The activity carries its checkout URL in a non-persistable Intent extra, and
 * `taskAffinity=""` makes it its own task root. When the system relaunches that task from
 * Recents after a process kill, the extras are gone — onCreate bails out before the WebView
 * is built, but onDestroy still runs.
 */
@RunWith(RobolectricTestRunner::class)
class SezzleCheckoutWebViewActivityLifecycleTest {

    private fun intentWithoutCheckoutUrl(): Intent =
        Intent(RuntimeEnvironment.getApplication(), SezzleCheckoutWebViewActivity::class.java)

    @Test
    fun `destroy after a launch with no checkout url does not crash`() {
        Robolectric.buildActivity(
            SezzleCheckoutWebViewActivity::class.java,
            intentWithoutCheckoutUrl(),
        ).create().destroy()
    }

    @Test
    fun `a launch with no checkout url reports an invalid-response error`() {
        val controller = Robolectric.buildActivity(
            SezzleCheckoutWebViewActivity::class.java,
            intentWithoutCheckoutUrl(),
        ).create()

        val shadow = shadowOf(controller.get())
        val result = shadow.resultIntent

        assertEquals(
            SezzleCheckoutContract.RESULT_TYPE_ERROR,
            result.getStringExtra(SezzleCheckoutContract.RESULT_TYPE_KEY),
        )
        assertEquals(
            SezzleCheckoutContract.ErrorCode.INVALID_RESPONSE,
            result.getStringExtra(SezzleCheckoutContract.RESULT_ERROR_CODE_KEY),
        )

        controller.destroy()
    }

    /**
     * The early-return path already delivered an error. onDestroy must not overwrite it with
     * a cancel, or the merchant sees a dismissal where a failure actually occurred.
     */
    @Test
    fun `destroy does not overwrite the error result with a cancel`() {
        val controller = Robolectric.buildActivity(
            SezzleCheckoutWebViewActivity::class.java,
            intentWithoutCheckoutUrl(),
        ).create()

        controller.destroy()

        val result = shadowOf(controller.get()).resultIntent
        assertEquals(
            SezzleCheckoutContract.RESULT_TYPE_ERROR,
            result.getStringExtra(SezzleCheckoutContract.RESULT_TYPE_KEY),
        )
    }
}
