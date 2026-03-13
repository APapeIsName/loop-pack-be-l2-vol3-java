#!/bin/bash
# ═══════════════════════════════════════════════
# After: 인덱스 적용 + 캐시 활성
# ═══════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "══════════════════════════════════════"
echo "  [After] 인덱스 적용 + 캐시 활성"
echo "══════════════════════════════════════"

# 1) 인덱스 생성
echo "→ 인덱스 생성..."
mysql -h 127.0.0.1 -u application -papplication loopers 2>/dev/null <<'SQL'
CREATE INDEX idx_product_likes ON product (likes_count DESC);
CREATE INDEX idx_product_latest ON product (created_at DESC);
CREATE INDEX idx_product_price ON product (price);
CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC);
CREATE INDEX idx_issued_coupon_member ON issued_coupon (member_id);
CREATE INDEX idx_issued_coupon_coupon ON issued_coupon (coupon_id);
ANALYZE TABLE product;
ANALYZE TABLE orders;
ANALYZE TABLE issued_coupon;
SQL
echo "  인덱스 생성 완료"

# 2) Redis 캐시 전체 삭제 (깨끗한 시작)
echo "→ Redis 캐시 flush..."
redis-cli -h 127.0.0.1 -p 6379 FLUSHALL 2>/dev/null || true
echo "  Redis flush 완료"

# 3) k6 실행
echo ""
echo "→ k6 부하 테스트 시작 (After)..."
echo ""
k6 run \
  --out json="$SCRIPT_DIR/result-after.json" \
  --summary-export="$SCRIPT_DIR/summary-after.json" \
  --env BASE_URL=http://localhost:8080 \
  "$SCRIPT_DIR/load-test.js"

echo ""
echo "══════════════════════════════════════"
echo "  [After] 완료 — result-after.json"
echo "══════════════════════════════════════"
