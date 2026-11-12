#!/usr/bin/env bash
# Smoke API: health → register/login → menu → (tuỳ chọn) tạo đơn staff.
# Chạy khi BE đã lên :8080. Không ghi secret vào log.
set -euo pipefail

BASE="${RMS_API_BASE:-http://127.0.0.1:8080/api/v1}"
EMAIL="${SMOKE_EMAIL:-smoke-$(date +%s)@example.com}"
PASS="${SMOKE_PASSWORD:-SmokePass1!}"

echo "== health =="
curl -fsS "$BASE/health" | head -c 200
echo

echo "== register =="
curl -fsS -X POST "$BASE/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Smoke User\",\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"phone\":null}" \
  | head -c 300
echo

echo "== login =="
LOGIN=$(curl -fsS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
ACCESS=$(printf '%s' "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
echo "accessToken length: ${#ACCESS}"

echo "== me =="
curl -fsS "$BASE/auth/me" -H "Authorization: Bearer $ACCESS" | head -c 300
echo

echo "== menu catalog =="
MENU=$(curl -fsS "$BASE/menu")
ITEM_COUNT=$(printf '%s' "$MENU" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('items') or []))")
echo "menu items: $ITEM_COUNT"

if [[ "${SMOKE_ORDER:-0}" == "1" ]]; then
  echo "== staff order (cần STAFF token + tableSessionId) =="
  echo "Đặt SMOKE_STAFF_TOKEN và SMOKE_SESSION_ID rồi bật SMOKE_ORDER=1"
  : "${SMOKE_STAFF_TOKEN:?}"
  : "${SMOKE_SESSION_ID:?}"
  : "${SMOKE_MENU_ITEM_ID:?}"
  curl -fsS -X POST "$BASE/staff/orders" \
    -H "Authorization: Bearer $SMOKE_STAFF_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"tableSessionId\":$SMOKE_SESSION_ID,\"items\":[{\"menuItemId\":$SMOKE_MENU_ITEM_ID,\"quantity\":1}],\"customerNote\":null,\"discountCode\":null}" \
    | head -c 400
  echo
fi

echo "OK smoke-api"
