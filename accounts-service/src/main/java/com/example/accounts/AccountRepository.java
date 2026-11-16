package com.example.accounts;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountRepository {

    private static final String[] NAMES = {"Motaz-1", "Motaz-2", "Motaz-3", "Motaz-4"};
    private static final String[][] MERCHANTS = {
            {"Supermarket", "groceries"},
            {"Fuel", "fuel"},
            {"Pharmacy", "health"},
            {"Bookstore", "shopping"},
            {"Cafe", "restaurants"},
            {"Electricity", "bills"},
    };

    private final Map<String, Map<String, Object>> accounts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> transactions = new ConcurrentHashMap<>();

    /** Response of GET /accounts/{id}: the account overview. */
    public Map<String, Object> account(String id) {
        return accounts.computeIfAbsent(id, this::buildAccount);
    }

    /** Response of GET /accounts/{id}/transactions. */
    public Map<String, Object> transactions(String id, int txns) {
        return transactions.computeIfAbsent(id + ":" + txns, key -> buildTransactions(id, txns));
    }

    private Map<String, Object> buildAccount(String id) {
        Random rnd = new Random(id.hashCode());
        String owner = NAMES[rnd.nextInt(NAMES.length)];

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Riyadh");
        address.put("district", "Test");
        address.put("street", "Test");
        address.put("postalCode", "12211");

        Map<String, Object> ownerMap = new LinkedHashMap<>();
        ownerMap.put("name", owner);
        ownerMap.put("nationalId", String.valueOf(1_000_000_000L + rnd.nextInt(999_999_999)));
        ownerMap.put("dateOfBirth", "1990-0" + (1 + rnd.nextInt(9)) + "-15");
        ownerMap.put("phone", "+9665" + (10_000_000 + rnd.nextInt(89_999_999)));
        ownerMap.put("email", owner.toLowerCase().replace(' ', '.') + "@example.com");
        ownerMap.put("address", address);

        double current = Math.round(rnd.nextDouble() * 10_000_000) / 100.0;
        Map<String, Object> balance = new LinkedHashMap<>();
        balance.put("current", current);
        balance.put("available", Math.round((current - rnd.nextDouble() * 500) * 100) / 100.0);
        balance.put("currency", "SAR");

        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", id);
        account.put("nickname", "Current account");
        account.put("currency", "SAR");
        account.put("iban", iban(rnd));
        account.put("balance", balance);
        account.put("owner", ownerMap);
        return account;
    }

    private Map<String, Object> buildTransactions(String id, int txns) {
        Random rnd = new Random(id.hashCode() * 31L + txns);
        List<Map<String, Object>> list = new ArrayList<>(txns);
        LocalDate day = LocalDate.of(2026, 11, 1);
        for (int i = 0; i < txns; i++) {
            String[] merchant = MERCHANTS[rnd.nextInt(MERCHANTS.length)];

            Map<String, Object> location = new LinkedHashMap<>();
            location.put("lat", Math.round((24.7 + rnd.nextDouble() / 10) * 1e6) / 1e6);
            location.put("lon", Math.round((46.6 + rnd.nextDouble() / 10) * 1e6) / 1e6);

            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", "txn-" + id + "-" + i);
            t.put("date", day.minusDays(i / 3).toString());
            t.put("amount", -Math.round(rnd.nextDouble() * 50_000) / 100.0);
            t.put("currency", "SAR");
            t.put("description", "Card payment - " + merchant[0]);
            t.put("category", merchant[1]);
            t.put("location", location);
            list.add(t);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", id);
        out.put("transactions", list);
        return out;
    }

    private static String iban(Random rnd) {
        StringBuilder sb = new StringBuilder("SA");
        for (int i = 0; i < 22; i++) sb.append(rnd.nextInt(10));
        return sb.toString();
    }
}
