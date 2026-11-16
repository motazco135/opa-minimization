package com.example.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The two questions the gateway filters can ask OPA. Non-blocking (WebClient). */
@Component
public class OpaClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private final WebClient web;
    private final ObjectMapper mapper;

    public OpaClient(WebClient.Builder builder, @Value("${opa.url}") String opaUrl, ObjectMapper mapper) {
        this.web = builder.baseUrl(opaUrl).build();
        this.mapper = mapper;
    }

    public record OpaResponse(List<String> result) {
    }

    /**
     * OPTION A: which fields may this consumer receive from this endpoint?
     * Small input, small answer, independent of the response size.
     */
    public Mono<List<String>> allowedFields(String consumerId, String method, String path) {
        return web.post()
                .uri("/v1/data/accounts/minimize/allowed_fields")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("input", input(consumerId, method, path)))
                .retrieve()
                .bodyToMono(OpaResponse.class)
                // Undefined result = no decision = nothing allowed.
                .map(r -> r.result() == null ? List.<String>of() : r.result())
                .defaultIfEmpty(List.of())
                .timeout(TIMEOUT);
    }

    /**
     * OPTION C: send the whole backend response to OPA and get it back trimmed.
     *
     * Uses OPA's v0 data API, where the request body IS the input and the
     * response body IS the result. The backend JSON is spliced in as raw bytes,
     * so the gateway does no JSON parsing at all: the full cost lands on OPA.
     */
    public Mono<byte[]> filter(String consumerId, String method, String path, byte[] responseBody) {
        return Mono.fromCallable(() -> inputWithResource(consumerId, method, path, responseBody))
                .flatMap(body -> web.post()
                        .uri("/v0/data/accounts/minimize/filtered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(byte[].class))
                .timeout(TIMEOUT);
    }

    private byte[] inputWithResource(String consumerId, String method, String path, byte[] resource)
            throws Exception {
        String head = mapper.writeValueAsString(input(consumerId, method, path));
        ByteArrayOutputStream out = new ByteArrayOutputStream(resource.length + head.length() + 16);
        out.writeBytes(head.substring(0, head.length() - 1).getBytes(StandardCharsets.UTF_8)); // drop final '}'
        out.writeBytes(",\"resource\":".getBytes(StandardCharsets.UTF_8));
        out.writeBytes(resource);
        out.writeBytes("}".getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static Map<String, Object> input(String consumerId, String method, String path) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("consumer_id", consumerId);
        input.put("method", method);
        input.put("path", path);
        return input;
    }
}
