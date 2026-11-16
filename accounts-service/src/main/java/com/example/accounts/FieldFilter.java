package com.example.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Keeps only the allowed dot-separated paths in a JSON tree.
 * Same rules as the Rego policy (option C) and the other FieldFilter copy:
 *   - "owner.name" keeps owner.name only; "owner" keeps the whole owner object
 *   - arrays are transparent: "transactions.amount" applies to every element
 *   - anything not listed is dropped (deny by default)
 */
public final class FieldFilter {

    /** A node in the path tree. leaf = keep everything below this point. */
    public static final class PathTree {
        private final Map<String, PathTree> children = new HashMap<>();
        private boolean leaf;

        public static PathTree of(Collection<String> paths) {
            PathTree root = new PathTree();
            for (String path : paths) {
                PathTree node = root;
                for (String part : path.split("\\.")) {
                    if (node.leaf) break; // a parent path already keeps everything
                    node = node.children.computeIfAbsent(part, k -> new PathTree());
                }
                node.leaf = true;
                node.children.clear();
            }
            return root;
        }
    }

    private FieldFilter() {
    }

    /** Returns the filtered copy, or null when nothing under {@code value} is allowed. */
    public static JsonNode filter(JsonNode value, PathTree node) {
        if (node.leaf) {
            return value;
        }
        if (value.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                PathTree child = node.children.get(field.getKey());
                if (child != null) {
                    JsonNode kept = filter(field.getValue(), child);
                    if (kept != null) {
                        out.set(field.getKey(), kept);
                    }
                }
            }
            return out;
        }
        if (value.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : value) {
                JsonNode kept = filter(item, node);
                if (kept != null) {
                    out.add(kept);
                }
            }
            return out;
        }
        return null; // a scalar where the path expects more structure
    }
}