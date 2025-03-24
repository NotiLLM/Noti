package org.muilab.notigpt.database.server.queue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

object N8NWebhookQueue {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Channel to queue API requests sequentially
    private val requestChannel = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        processQueue()
    }

    private fun processQueue() {
        scope.launch {
            requestChannel.consumeAsFlow().collect { apiCall ->
                apiCall() // execute request

                // Delay to enforce 4 requests per minute (15s per request)
                delay(15_000L)
            }
        }
    }

    fun enqueue(request: suspend () -> Unit) {
        scope.launch {
            requestChannel.send(request)
        }
    }
}