package accounts.minimize

import rego.v1

# ---------------------------------------------------------------------------
# Field-level data minimization for EXISTING APIs behind a gateway.
#
# The gateway already authenticates callers (API key / secret / mTLS) and knows
# the consumer ID. That is all this policy needs:
#
#   {
#     "consumer_id": "mobile-banking",      # set by the gateway, never by the caller
#     "method":      "GET",
#     "path":        "/accounts/acc-1",
#     "resource":    { ...full response... } # only for option C
#   }
#
# The rules live in data.consumers (data.json): for each consumer, which
# fields it may receive from which endpoint, plus the purpose and the
# data-sharing agreement it was registered under.
#
# Paths are dot-separated. A path under "transactions." applies to every
# element of the transactions array.
# ---------------------------------------------------------------------------

# Options A and B: the fields this consumer may receive from this endpoint.
# Unknown consumer, or endpoint not listed for it -> empty set -> nothing
# is returned (deny by default).
allowed_fields contains field if {
	some api, fields in data.consumers[input.consumer_id].apis
	endpoint_matches(api, input.method, input.path)
	some field in fields
}

# "GET /accounts/{id}" matches GET /accounts/acc-1 but not /accounts/acc-1/x
endpoint_matches(api, method, path) if {
	parts := split(api, " ")
	parts[0] == method
	template := split(parts[1], "/")
	actual := split(path, "/")
	count(template) == count(actual)
	every i, segment in template {
		segment_matches(segment, actual[i])
	}
}

segment_matches(segment, _) if {
	startswith(segment, "{")
	endswith(segment, "}")
}

segment_matches(segment, value) if segment == value

# Why this consumer gets data at all. Not used for the decision; kept so
# the purpose can be queried and shown next to each decision for audits.
purpose := data.consumers[input.consumer_id].purpose

# ---------------------------------------------------------------------------
# Option C: OPA trims the response body itself.
# ---------------------------------------------------------------------------

# The one array in these responses. A path under "transactions." applies to
# every element; the path "transactions" alone keeps the whole array.
array_field := "transactions"

array_prefix := $"{array_field}."

# Paths on the top-level object, in json.filter syntax ("a/b").
top_paths := [replace(f, ".", "/") |
	some f in allowed_fields
	f != array_field
	not startswith(f, array_prefix)
]

# Paths applied to each element of the array.
item_paths := [replace(trim_prefix(f, array_prefix), ".", "/") |
	some f in allowed_fields
	startswith(f, array_prefix)
]

base := json.filter(object.remove(input.resource, [array_field]), top_paths)

items := input.resource[array_field] if {
	array_field in allowed_fields
} else := [json.filter(t, item_paths) | some t in input.resource[array_field]]

any_item_access if array_field in allowed_fields

any_item_access if count(item_paths) > 0

filtered := object.union(base, {array_field: items}) if {
	input.resource[array_field]
	any_item_access
} else := base
