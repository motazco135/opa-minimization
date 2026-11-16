package com.example.gateway;

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
import reactor.core.publisher.Mono;

/**
 * OPTION C — OPA trims the whole body. Used in a route as:  filters: [ OpaFilterBody ]
 *
 * On the way back, the gateway sends the full backend response to OPA and
 * returns whatever OPA's `filtered` rule produces. The gateway itself does no
 * JSON parsing; OPA does the parsing, filtering and serializing.
 */
@Component
public class OpaFilterBodyGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Logger log = LoggerFactory.getLogger(OpaFilterBodyGatewayFilterFactory.class);

    private final OpaClient opa;
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBody;

    public OpaFilterBodyGatewayFilterFactory(OpaClient opa,
                                             ModifyResponseBodyGatewayFilterFactory modifyResponseBody) {
        super(Object.class);
        this.opa = opa;
        this.modifyResponseBody = modifyResponseBody;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return new OrderedGatewayFilter((exchange, chain) -> {
            String consumer = Exchanges.consumer(exchange);
            String method = Exchanges.method(exchange);
            String path = Exchanges.backendPath(exchange);

            GatewayFilter rewrite = modifyResponseBody.apply(new ModifyResponseBodyGatewayFilterFactory.Config()
                    .setRewriteFunction(byte[].class, byte[].class, (ex, body) -> {
                        ServerHttpResponse response = ex.getResponse();
                        if (body == null) {
                            return Mono.empty();
                        }
                        if (!Exchanges.isOk(response)) {
                            return Mono.just(body); // backend errors pass through untouched
                        }
                        if (!Exchanges.isJson(response)) {
                            response.setStatusCode(HttpStatus.BAD_GATEWAY);
                            return Mono.just(Exchanges.errorBody("unsupported content type"));
                        }
                        return opa.filter(consumer, method, path, body)
                                .onErrorResume(e -> {
                                    // Fail closed: no decision, no data.
                                    log.warn("OPA unavailable: {}", e.toString());
                                    response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                                    return Mono.just(Exchanges.errorBody("policy decision unavailable"));
                                });
                    }));
            return rewrite.filter(exchange, chain);
        }, NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1);
    }
}
