#!/bin/bash
# ═══════════════════════════════════════════════
# Before: 인덱스 없음 + 캐시 없음
# ═══════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "══════════════════════════════════════"
echo "  [Before] 인덱스 없음 + 캐시 없음"
echo "══════════════════════════════════════"

# 1) 인덱스 드롭
echo "→ 인덱스 드롭..."
mysql -h 127.0.0.1 -u application -papplication loopers 2>/dev/null <<'SQL'
DROP INDEX idx_product_likes ON product;
DROP INDEX idx_product_latest ON product;
DROP INDEX idx_product_price ON product;
DROP INDEX idx_order_member_created ON orders;
DROP INDEX idx_issued_coupon_member ON issued_coupon;
DROP INDEX idx_issued_coupon_coupon ON issued_coupon;
SQL
echo "  인덱스 드롭 완료 (에러는 이미 없는 경우 무시)"

# 2) Redis 캐시 전체 삭제
echo "→ Redis 캐시 flush..."
redis-cli -h 127.0.0.1 -p 6379 FLUSHALL 2>/dev/null || true
echo "  Redis flush 완료"

# 3) k6 실행
echo ""
echo "→ k6 부하 테스트 시작 (Before)..."
echo ""
k6 run \
  --out json="$SCRIPT_DIR/result-before.json" \
  --summary-export="$SCRIPT_DIR/summary-before.json" \
  --env BASE_URL=http://localhost:8080 \
  "$SCRIPT_DIR/load-test.js"

echo ""
echo "══════════════════════════════════════"
echo "  [Before] 완료 — result-before.json"
echo "══════════════════════════════════════"
