package com.mikepenz.agentbelay.harness

/**
 * Wrapper around an outgoing harness response.
 *
 * Today this carries only the serialized [body] string and a content
 * type — operationally identical to the previous bare-`String` return —
 * but introduces a stable extension point so future harnesses can layer
 * on additional metadata without changing every adapter signature:
 *
 *  - structured deny codes (some agents accept programmatic error
 *    classifications alongside the user-facing message)
 *  - extra response headers (e.g. `X-Agent-Belay-Decision: defer` for
 *    telemetry consumers)
 *  - a non-JSON content type (Gemini's `command`-type hooks parse
 *    plain-text envelopes)
 *  - retry-after / ratelimit hints
 *
 * Adding a field here is a single-call-site change at the route layer.
 * Keep this data class additive (default values for any new field) so
 * existing adapters continue to compile.
 */
data class HarnessResponse(
    /** Already-serialized body — JSON for the harnesses we ship today. */
    val body: String,
    /**
     * MIME type written into the HTTP `Content-Type` header. Defaults to
     * `application/json` because every harness we ship today returns
     * JSON; switch to `text/plain` (or anything else) for harnesses that
     * use a different envelope.
     */
    val contentType: String = "application/json",
)
