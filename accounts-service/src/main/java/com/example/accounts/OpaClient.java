package com.example.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Asks OPA which fields a consumer may receive from an endpoint (OPTION B).
 * Same policy, same input shape as the gateway uses in OPTION A.
 */
@Component
public class OpaClient {

    private final RestClient rest;

    public OpaClient(@Value("${opa.url}") String opaUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(1000);
        this.rest = RestClient.builder().baseUrl(opaUrl).requestFactory(factory).build();
    }

    record OpaResponse(List<String> result) {
    }

    public List<String> allowedFields(String consumerId, String method, String path) {
        Map<String, Object> input = Map.of(
                "consumer_id", consumerId,
                "method", method,
                "path", path);

        OpaResponse response = rest.post()
                .uri("/v1/data/accounts/minimize/allowed_fields")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("input", input))
                .retrieve()
                .body(OpaResponse.class);

        // Undefined result = no decision = nothing allowed.
        return response == null || response.result() == null ? List.of() : response.result();
    }
}
