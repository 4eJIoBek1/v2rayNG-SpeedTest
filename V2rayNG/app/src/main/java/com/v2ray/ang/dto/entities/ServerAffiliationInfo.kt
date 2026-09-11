package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    /** Measured download speed in Mbps. 0 means untested (or failed). */
    var testSpeedMbps: Float = 0f
)
