package com.example.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * An "existing" Accounts API. The /accounts endpoints know nothing about
 * minimization: they return everything, as most existing APIs do today.
 *
 * The /filtered endpoints exist only for OPTION B, where the service asks OPA
 * itself. The gateway has already validated the API key and put the caller's
 * identity in the x-consumer-id header.
 */
@RestController
public class AccountController {

    private static final String CONSUMER_HEADER = "x-consumer-id";

    private final AccountRepository repository;
    private final OpaClient opa;
    private final ObjectMapper mapper;

    public AccountController(AccountRepository repository, OpaClient opa, ObjectMapper mapper) {
        this.repository = repository;
        this.opa = opa;
        this.mapper = mapper;
    }

    // ---- existing endpoints: full data (direct, pass-through, options A and C) --

    @GetMapping("/accounts/{id}")
    public Map<String, Object> account(@PathVariable String id) {
        return repository.account(id);
    }

    @GetMapping("/accounts/{id}/transactions")
    public Map<String, Object> transactions(@PathVariable String id,
                                            @RequestParam(defaultValue = "50") int txns) {
        return repository.transactions(id, txns);
    }

    // ---- OPTION B: the service asks OPA and filters itself -------------------

    @GetMapping("/filtered/accounts/{id}")
    public ResponseEntity<JsonNode> filteredAccount(
            @PathVariable String id,
            @RequestHeader(value = CONSUMER_HEADER, defaultValue = "") String consumerId) {
        return minimized(consumerId, "/accounts/" + id, repository.account(id));
    }

    @GetMapping("/filtered/accounts/{id}/transactions")
    public ResponseEntity<JsonNode> filteredTransactions(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int txns,
            @RequestHeader(value = CONSUMER_HEADER, defaultValue = "") String consumerId) {
        return minimized(consumerId, "/accounts/" + id + "/transactions", repository.transactions(id, txns));
    }

    private ResponseEntity<JsonNode> minimized(String consumerId, String path, Object body) {
        List<String> allowed;
        try {
            allowed = opa.allowedFields(consumerId, "GET", path);
        } catch (RestClientException e) {
            // Fail closed: no decision, no data.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(mapper.createObjectNode().put("error", "policy decision unavailable"));
        }

        JsonNode full = mapper.valueToTree(body);
        JsonNode filtered = FieldFilter.filter(full, FieldFilter.PathTree.of(allowed));
        return ResponseEntity.ok(filtered == null ? mapper.createObjectNode() : filtered);
    }
}