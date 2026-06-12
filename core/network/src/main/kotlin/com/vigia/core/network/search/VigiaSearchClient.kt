package com.vigia.core.network.search

import com.vigia.core.model.VigiaSearchContext
import kotlinx.coroutines.flow.Flow

/**
 * Transport-agnostic contract for the VIGIASearch Fargate backend.
 *
 * Current binding: [OkHttpSseSearchClient] → POST /v1/search (text/event-stream).
 * To swap backend: change the @Binds in NetworkModule. All consumers are unaffected.
 */
interface VigiaSearchClient {
    /**
     * Submits a context-enriched query and returns the server-sent event stream.
     *
     * The [Flow] emits [SearchEvent.Step] and [SearchEvent.TextDelta] items progressively,
     * a single [SearchEvent.Metadata] after the last token, then [SearchEvent.Done] and completes.
     * Cancelling the collecting coroutine cancels the underlying HTTP call immediately.
     */
    fun search(context: VigiaSearchContext): Flow<SearchEvent>
}
