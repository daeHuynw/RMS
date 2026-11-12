#!/usr/bin/env bash
# Smoke FE: build Vite + (tuỳ chọn) HTTP GET vài trang khi dev server đang chạy.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FE="$ROOT/frontend"
BASE="${RMS_FE_BASE:-http://127.0.0.1:5173}"

echo "== vite build =="
(cd "$FE" && npm run build)

PAGES=(
  /
  /home.html
  /menu.html
  /register.html
  /profile.html
  /reservations.html
  /my-orders.html
  /qr.html
  /staff/tables.html
  /staff/orders.html
  /staff/reservations.html
  /kitchen/
  /admin/dashboard.html
  /admin/users.html
  /admin/menu.html
  /admin/categories.html
  /admin/tables.html
  /admin/discounts.html
  /admin/settings.html
  /admin/audit.html
)

if curl -fsS -o /dev/null --connect-timeout 1 "$BASE/" 2>/dev/null; then
  echo "== HTTP smoke $BASE =="
  for path in "${PAGES[@]}"; do
    code=$(curl -sS -o /dev/null -w '%{http_code}' "$BASE$path")
    echo "$code  $path"
    [[ "$code" == "200" ]] || { echo "FAIL $path"; exit 1; }
  done
else
  echo "Dev server chưa chạy — bỏ qua HTTP GET (build đã OK)."
fi

echo "OK smoke-fe"
