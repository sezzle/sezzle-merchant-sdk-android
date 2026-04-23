package com.sezzle.sdk.models

/** The checkout intent — authorize only or authorize and capture. */
enum class SezzleIntent(val value: String) {
    /** Authorize the payment. Capture separately via your backend. */
    AUTH("AUTH"),
    /** Authorize and capture in one step. */
    CAPTURE("CAPTURE")
}
