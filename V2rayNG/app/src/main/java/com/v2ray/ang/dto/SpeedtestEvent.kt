package com.v2ray.ang.dto

sealed class SpeedtestEvent {

    /** Periodic progress update while the batch is still running. */
    data class Progress(val text: String) : SpeedtestEvent()

    /** A single server download speed result in Mbps, 0 when the test failed. */
    data class Result(val guid: String, val speedMbps: Float) : SpeedtestEvent()

    /** The entire batch has finished or been cancelled. */
    data class Finish(val status: String) : SpeedtestEvent()
}
