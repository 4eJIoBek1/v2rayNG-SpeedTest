package com.v2ray.ang.service

/** Common handle for cancellable per-profile test batches owned by [CoreTestService]. */
internal interface TestBatchWorker {
    fun cancel()
}
