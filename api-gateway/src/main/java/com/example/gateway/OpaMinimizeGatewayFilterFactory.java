package com.example.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * OPTION A — the gateway plugin. Used in a route as:  filters: [ OpaMinimize ]
 *
 *   1. Before the request goes to the backend, ask OPA which fields this
 *      consumer may receive from this endpoint. If OPA can't answer, stop
 *      with 503: the backend is never called (fail closed).
 *   2. Forward the request to the existing API, unchanged.
 *   3. On the way back, keep only the allowed fields.
 *
 * This is the same logic you would write as a Kong plugin or an Apigee policy.
 */
@Component
public class OpaMinimizeGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Logger log = LoggerFactory.getLogger(OpaMinimizeGatewayFilterFactory.class);

    private final OpaClient opa;
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBody;
    private final ObjectMapper mapper;

    public OpaMinimizeGatewayFilterFactory(OpaClient opa,
                                           ModifyResponseBodyGatewayFilterFactory modifyResponseBody,
                                           ObjectMapper mapper) {
        super(Object.class);
        this.opa = opa;
        this.modifyResponseBody = modifyResponseBody;
        this.mapper = mapper;
    }

    @Override
    public GatewayFilter apply(Object config) {
        // Response rewriting must be wired in before Netty writes the response,
        // so this filter runs just ahead of NettyWriteResponseFilter.
        return new OrderedGatewayFilter((exchange, chain) -> {
            String consumer = Exchanges.consumer(exchange);
            String method = Exchanges.method(exchange);
            String path = Exchanges.backendPath(exchange);

            return opa.allowedFields(consumer, method, path)
                    .map(Optional::of)
                    .onErrorResume(e -> {
                        log.warn("OPA unavailable: {}", e.toString());
                        return Mono.just(Optional.<List<String>>empty());
                    })
                    .flatMap(decision -> decision.isEmpty()
                            ? Exchanges.reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "policy decision unavailable")
                            : trimResponse(decision.get()).filter(exchange, chain));
        }, NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
    }

    /** A response-body rewrite that keeps only the allowed fields. */
    private GatewayFilter trimResponse(List<String> allowed) {
        FieldFilter.PathTree tree = FieldFilter.PathTree.of(allowed);
        return modifyResponseBody.apply(new ModifyResponseBodyGatewayFilterFactory.Config()
                .setRewriteFunction(byte[].class, byte[].class,
                        (exchange, body) -> body == null ? Mono.empty() : Mono.just(trim(exchange, body, tree))));
    }

    private byte[] trim(ServerWebExchange exchange, byte[] body, FieldFilter.PathTree tree) {
        ServerHttpResponse response = exchange.getResponse();
        if (!Exchanges.isOk(response)) {
            return body; // backend errors pass through untouched
        }
        if (!Exchanges.isJson(response)) {
            response.setStatusCode(HttpStatus.BAD_GATEWAY); // can't filter it, so don't send it
            return Exchanges.errorBody("unsupported content type");
        }
        try {
            JsonNode kept = FieldFilter.filter(mapper.readTree(body), tree);
            return mapper.writeValueAsBytes(kept == null ? mapper.createObjectNode() : kept);
        } catch (IOException e) {
            log.warn("Backend returned invalid JSON: {}", e.getMessage());
            response.setStatusCode(HttpStatus.BAD_GATEWAY);
            return Exchanges.errorBody("upstream error");
        }
    }
}
