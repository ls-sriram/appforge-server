#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────
# Local smoke tests — curl-based integration tests against running backend
# ──────────────────────────────────────────────────────────────────────────
# Prerequisites:
#   - AppForge Backend running on localhost:8080
#   - PostgreSQL running on localhost:5432 with DATABASE_PRIMARY=sql
#
# Usage:
#   ./scripts/test-local.sh              # Run all tests
#   ./scripts/test-local.sh --health-only # Run only health checks
#   ./scripts/test-local.sh --verbose    # Show full response bodies
# ──────────────────────────────────────────────────────────────────────────

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
PASS=0
FAIL=0
VERBOSE=false

if [ "${1:-}" = "--verbose" ] || [ "${2:-}" = "--verbose" ]; then
    VERBOSE=true
fi

# ─── Helpers ─────────────────────────────────────────────────────────────
log_pass() { echo -e "  ✅ PASS: $1"; ((PASS++)); }
log_fail() { echo -e "  ❌ FAIL: $1 — $2"; ((FAIL++)); }

assert_status() {
    local desc="$1" url="$2" method="${3:-GET}" data="$4" expected="${5:-200}"
    local cmd="curl -s -o /tmp/test_response.json -w '%{http_code}' -X $method"

    if [ -n "$data" ]; then
        cmd="$cmd -H 'Content-Type: application/json' -d '$data'"
    fi
    cmd="$cmd '$BASE_URL$url'"

    local status
    status=$(eval "$cmd" 2>/dev/null) || status="000"

    if [ "$status" = "$expected" ]; then
        log_pass "$desc ($status)"
        if [ "$VERBOSE" = true ]; then
            cat /tmp/test_response.json | python3 -m json.tool 2>/dev/null || cat /tmp/test_response.json
            echo ""
        fi
    else
        log_fail "$desc" "expected=$expected got=$status"
        if [ "$VERBOSE" = true ] && [ -s /tmp/test_response.json ]; then
            cat /tmp/test_response.json | python3 -m json.tool 2>/dev/null || cat /tmp/test_response.json
            echo ""
        fi
    fi
}

# ─── Tests ───────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  AppForge Backend — Local Smoke Tests"
echo "  Target: $BASE_URL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 1. Health endpoints
echo "── Health Checks ──"
assert_status "Main health" "/health"
assert_status "ML health" "/ml/health"

if [ "${1:-}" = "--health-only" ]; then
    echo ""
    echo "── Health-only mode, exiting ──"
    echo "  Results: $PASS passed, $FAIL failed"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    exit $FAIL
fi

# 2. System routes (internal)
echo ""
echo "── System Routes ──"
assert_status "Readiness probe" "/ready"
assert_status "Config dump (internal)" "/internal/config" "POST" '{"secret":"dev-secret"}'

# 3. Database CRUD via internal test endpoint
echo ""
echo "── Database CRUD (via /internal/test-db) ──"

# Create
CREATE_PAYLOAD='{"secret":"dev-secret","op":"create","collection":"test-users","id":"user-001","data":{"name":"Alice","role":"admin","active":true}}'
assert_status "Create document" "/internal/test-db" "POST" "$CREATE_PAYLOAD" "200"

# Get
GET_PAYLOAD='{"secret":"dev-secret","op":"get","collection":"test-users","id":"user-001"}'
assert_status "Get document" "/internal/test-db" "POST" "$GET_PAYLOAD" "200"

# Update
UPDATE_PAYLOAD='{"secret":"dev-secret","op":"update","collection":"test-users","id":"user-001","data":{"name":"Alice Updated","role":"superadmin"}}'
assert_status "Update document" "/internal/test-db" "POST" "$UPDATE_PAYLOAD" "200"

# Duplicate create should fail
assert_status "Duplicate create rejected" "/internal/test-db" "POST" "$CREATE_PAYLOAD" "400"

# Merge
MERGE_PAYLOAD='{"secret":"dev-secret","op":"merge","collection":"test-users","id":"user-001","data":{"age":31}}'
assert_status "Merge document" "/internal/test-db" "POST" "$MERGE_PAYLOAD" "200"

# Query
QUERY_PAYLOAD='{"secret":"dev-secret","op":"query","collection":"test-users"}'
assert_status "Query collection" "/internal/test-db" "POST" "$QUERY_PAYLOAD" "200"

# Delete
DELETE_PAYLOAD='{"secret":"dev-secret","op":"delete","collection":"test-users","id":"user-001"}'
assert_status "Delete document" "/internal/test-db" "POST" "$DELETE_PAYLOAD" "200"

# Get after delete should fail
assert_status "Get after delete returns 404" "/internal/test-db" "POST" "$GET_PAYLOAD" "404"

# 4. Set-if-absent
echo ""
echo "── Set If Absent ──"
SIA_CREATE='{"secret":"dev-secret","op":"setIfAbsent","collection":"test-users","id":"user-sia","data":{"name":"First"}}'
assert_status "SetIfAbsent creates" "/internal/test-db" "POST" "$SIA_CREATE" "200"
SIA_RETRY='{"secret":"dev-secret","op":"setIfAbsent","collection":"test-users","id":"user-sia","data":{"name":"Second"}}'
assert_status "SetIfAbsent returns false for existing" "/internal/test-db" "POST" "$SIA_RETRY" "200"

# 5. Find first by field
echo ""
echo "── Find First By Field ──"
FIND_PAYLOAD='{"secret":"dev-secret","op":"findFirst","collection":"test-users","field":"role","value":"admin"}'
assert_status "FindFirstByField" "/internal/test-db" "POST" "$FIND_PAYLOAD" "200"

# 6. Collection isolation
echo ""
echo "── Collection Isolation ──"
assert_status "Create in collection A" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"create","collection":"col-a","id":"item-1","data":{"val":"from-a"}}'
assert_status "Create in collection B (same id)" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"create","collection":"col-b","id":"item-1","data":{"val":"from-b"}}'
assert_status "Get from col-a" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"get","collection":"col-a","id":"item-1"}'
assert_status "Get from col-b" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"get","collection":"col-b","id":"item-1"}'

# Cleanup
assert_status "Cleanup col-a" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"delete","collection":"col-a","id":"item-1"}'
assert_status "Cleanup col-b" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"delete","collection":"col-b","id":"item-1"}'
assert_status "Cleanup user-001" "/internal/test-db" "POST" '{"secret":"dev-secret","op":"delete","collection":"test-users","id":"user-sia"}'

# ─── Summary ─────────────────────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Results: $PASS passed, $FAIL failed"
if [ $FAIL -eq 0 ]; then
    echo "  ✅ All smoke tests passed!"
else
    echo "  ❌ $FAIL test(s) failed"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

exit $FAIL
