package com.example.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldFilterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final String account = """
            {"id":"acc-1","iban":"SA03",
             "balance":{"amount":10.5,"currency":"SAR"},
             "owner":{"name":"Sara","nationalId":"1000","address":{"city":"Riyadh"}},
             "transactions":[{"id":"t1","amount":-5,"counterparty":{"name":"Cafe","iban":"SA44"}}]}
            """;

    private JsonNode filter(List<String> paths) throws Exception {
        return FieldFilter.filter(mapper.readTree(account), FieldFilter.PathTree.of(paths));
    }

    @Test
    void keepsOnlyAllowedFields() throws Exception {
        JsonNode out = filter(List.of("id", "balance.amount", "transactions.amount"));
        assertThat(out.toString())
                .isEqualTo("{\"id\":\"acc-1\",\"balance\":{\"amount\":10.5},\"transactions\":[{\"amount\":-5}]}");
    }

    @Test
    void parentPathKeepsWholeObject() throws Exception {
        JsonNode out = filter(List.of("owner.address", "owner"));
        assertThat(out.get("owner").get("nationalId").asText()).isEqualTo("1000");
    }

    @Test
    void nestedPathInsideArray() throws Exception {
        JsonNode out = filter(List.of("transactions.counterparty.name"));
        assertThat(out.toString()).isEqualTo("{\"transactions\":[{\"counterparty\":{\"name\":\"Cafe\"}}]}");
    }

    @Test
    void nothingAllowedReturnsEmptyObject() throws Exception {
        assertThat(filter(List.of()).toString()).isEqualTo("{}");
    }
}
