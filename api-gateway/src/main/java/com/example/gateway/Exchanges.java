package com.example.gateway;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Small helpers shared by the two OPA filters. */
final class Exchanges {

    /** Set by the gateway after it authenticates the caller. In this POC the caller sends it. */
    static final String CONSUMER_HEADER = "x-consumer-id";

    private Exchanges() {
    }

    static String consumer(ServerWebExchange exchange) {
        String value = exchange.getRequest().getHeaders().getFirst(CONSUMER_HEADER);
        return value == null ? "" : value;
    }

    static String method(ServerWebExchange exchange) {
        return exchange.getRequest().getMethod().name();
    }

    /**
     * The path as the backend sees it. The POC's routes use a prefix per option
     * (/gateway/..., /opa/...) that StripPrefix removes later, so drop it here:
     *   /gateway/accounts/acc-1  ->  /accounts/acc-1
     * In a real gateway without such prefixes this is just the request path.
     */
    static String backendPath(ServerWebExchange exchange) {
        return exchange.getRequest().getPath().value().replaceFirst("^/[^/]+", "");
    }

    static boolean isOk(ServerHttpResponse response) {
        HttpStatusCode status = response.getStatusCode();
        return status != null && status.value() == 200;
    }

    static boolean isJson(ServerHttpResponse response) {
        MediaType type = response.getHeaders().getContentType();
        return type != null
                && (type.isCompatibleWith(MediaType.APPLICATION_JSON) || type.getSubtype().endsWith("+json"));
    }

    static byte[] errorBody(String message) {
        return ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    /** Ends the exchange with a JSON error, without calling the backend. */
    static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(errorBody(message));
        return response.writeWith(Mono.just(buffer));
    }
}
