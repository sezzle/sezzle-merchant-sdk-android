package com.sezzle.sdk.networking

import com.sezzle.sdk.models.SezzleCheckout
import com.sezzle.sdk.models.SezzleError

/** Creates Sezzle checkout sessions via `POST /v2/session`. */
internal interface SessionServiceProtocol {
    fun createSession(
        checkout: SezzleCheckout,
        onSuccess: (SessionResponse) -> Unit,
        onError: (SezzleError) -> Unit
    )
}

/** Default implementation using [HttpClient]. Runs network calls on a background thread. */
internal class SessionService(private val httpClient: HttpClient) : SessionServiceProtocol {

    override fun createSession(
        checkout: SezzleCheckout,
        onSuccess: (SessionResponse) -> Unit,
        onError: (SezzleError) -> Unit
    ) {
        Thread {
            try {
                val body = SessionRequest.fromCheckout(checkout)
                val json = httpClient.post("/v2/session", body)
                val response = SessionResponse.fromJson(json)
                onSuccess(response)
            } catch (e: SezzleError) {
                onError(e)
            } catch (e: Exception) {
                onError(SezzleError.NetworkError(e))
            }
        }.start()
    }
}
