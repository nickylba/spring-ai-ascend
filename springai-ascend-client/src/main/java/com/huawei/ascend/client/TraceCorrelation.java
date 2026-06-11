package com.huawei.ascend.client;

/**
 * Trace correlation for one A2A call: what the client sent and what the
 * server answered. The platform edge emits {@code traceresponse:
 * 00-&lt;trace_id&gt;-&lt;server_span_id&gt;-01} on every response, which is
 * the handle support teams use to join client-side failures to server logs.
 *
 * @param traceparent   the {@code traceparent} header sent with the call
 * @param traceresponse the server's {@code traceresponse} header; null when
 *                      the server did not answer one (e.g. a non-platform
 *                      A2A endpoint)
 */
public record TraceCorrelation(String traceparent, String traceresponse) {
}
