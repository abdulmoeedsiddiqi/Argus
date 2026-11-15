package dev.argus.admin;

import java.util.LinkedHashMap;
import java.util.Map;

/** Test helper: valid request bodies for the Admin API, as maps that tests can modify. */
public final class TestData {

    private TestData() { }

    public static Map<String, Object> plan(String id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", "Free");
        body.put("rateCapacity", 60);
        body.put("ratePeriodMs", 60000);
        body.put("monthlyQuota", 10000);
        return body;
    }

    public static Map<String, Object> consumer(String id, String planId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", "Shop App");
        body.put("planId", planId);
        return body;
    }

    public static Map<String, Object> route(String id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("pathPrefix", "/" + id);
        body.put("serviceId", "orders");
        body.put("loadBalancing", "ROUND_ROBIN");
        body.put("timeoutMs", 5000);
        body.put("retryAttempts", 1);
        return body;
    }

    public static Map<String, Object> upstream(String id) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("serviceId", "orders");
        body.put("baseUri", "http://orders-a:8081");
        body.put("weight", 1);
        body.put("version", "v1");
        return body;
    }

    /** A copy of the body with one field changed (or removed when the value is null). */
    public static Map<String, Object> with(Map<String, Object> body, String field, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        if (value == null) {
            copy.remove(field);
        } else {
            copy.put(field, value);
        }
        return copy;
    }
}
