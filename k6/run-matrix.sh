#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# 2×2 Matrix Load Test: 캐시(O/X) × 인덱스(O/X)
# ═══════════════════════════════════════════════════════════════
#
# 사용법:
#   ./k6/run-matrix.sh <test-number>
#
#   1 = 캐시X + 인덱스X  (Baseline)
#   2 = 캐시X + 인덱스O  (인덱스만)
#   3 = 캐시O + 인덱스X  (캐시만)
#   4 = 캐시O + 인덱스O  (둘 다)
#
# 실행 순서:
#   1) 앱을 SPRING_CACHE_TYPE=none 으로 시작
#   2) ./k6/run-matrix.sh 1    → Baseline
#   3) ./k6/run-matrix.sh 2    → 인덱스 생성 후 테스트
#   4) 앱을 정상(캐시 활성)으로 재시작
#   5) ./k6/run-matrix.sh 3    → 인덱스 드롭 후 테스트
#   6) ./k6/run-matrix.sh 4    → 인덱스 생성 후 테스트
# ═══════════════════════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MYSQL="docker exec docker-mysql-1 mysql -u application -papplication loopers"

case "$1" in
  1)
    LABEL="no-cache-no-index"
    echo "══════════════════════════════════════"
    echo "  [1/4] 캐시 X + 인덱스 X (Baseline)"
    echo "  ⚠ 앱이 SPRING_CACHE_TYPE=none 으로 실행 중이어야 합니다"
    echo "══════════════════════════════════════"
    echo "→ 인덱스 드롭..."
    $MYSQL -e "
      DROP INDEX idx_product_likes ON product;
      DROP INDEX idx_product_latest ON product;
      DROP INDEX idx_product_price ON product;
      DROP INDEX idx_order_member_created ON orders;
      DROP INDEX idx_issued_coupon_member ON issued_coupon;
      DROP INDEX idx_issued_coupon_coupon ON issued_coupon;
    " 2>/dev/null || true
    docker exec redis-master redis-cli FLUSHALL > /dev/null 2>&1 || true
    ;;
  2)
    LABEL="no-cache-with-index"
    echo "══════════════════════════════════════"
    echo "  [2/4] 캐시 X + 인덱스 O (인덱스만)"
    echo "  ⚠ 앱이 SPRING_CACHE_TYPE=none 으로 실행 중이어야 합니다"
    echo "══════════════════════════════════════"
    echo "→ 인덱스 생성..."
    $MYSQL -e "
      CREATE INDEX idx_product_likes ON product (likes_count DESC);
      CREATE INDEX idx_product_latest ON product (created_at DESC);
      CREATE INDEX idx_product_price ON product (price);
      CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC);
      CREATE INDEX idx_issued_coupon_member ON issued_coupon (member_id);
      CREATE INDEX idx_issued_coupon_coupon ON issued_coupon (coupon_id);
      ANALYZE TABLE product;
      ANALYZE TABLE orders;
      ANALYZE TABLE issued_coupon;
    " 2>/dev/null || true
    docker exec redis-master redis-cli FLUSHALL > /dev/null 2>&1 || true
    ;;
  3)
    LABEL="with-cache-no-index"
    echo "══════════════════════════════════════"
    echo "  [3/4] 캐시 O + 인덱스 X (캐시만)"
    echo "  ⚠ 앱이 정상(캐시 활성)으로 실행 중이어야 합니다"
    echo "══════════════════════════════════════"
    echo "→ 인덱스 드롭..."
    $MYSQL -e "
      DROP INDEX idx_product_likes ON product;
      DROP INDEX idx_product_latest ON product;
      DROP INDEX idx_product_price ON product;
      DROP INDEX idx_order_member_created ON orders;
      DROP INDEX idx_issued_coupon_member ON issued_coupon;
      DROP INDEX idx_issued_coupon_coupon ON issued_coupon;
    " 2>/dev/null || true
    docker exec redis-master redis-cli FLUSHALL > /dev/null 2>&1 || true
    ;;
  4)
    LABEL="with-cache-with-index"
    echo "══════════════════════════════════════"
    echo "  [4/4] 캐시 O + 인덱스 O (둘 다)"
    echo "  ⚠ 앱이 정상(캐시 활성)으로 실행 중이어야 합니다"
    echo "══════════════════════════════════════"
    echo "→ 인덱스 생성..."
    $MYSQL -e "
      CREATE INDEX idx_product_likes ON product (likes_count DESC);
      CREATE INDEX idx_product_latest ON product (created_at DESC);
      CREATE INDEX idx_product_price ON product (price);
      CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC);
      CREATE INDEX idx_issued_coupon_member ON issued_coupon (member_id);
      CREATE INDEX idx_issued_coupon_coupon ON issued_coupon (coupon_id);
      ANALYZE TABLE product;
      ANALYZE TABLE orders;
      ANALYZE TABLE issued_coupon;
    " 2>/dev/null || true
    docker exec redis-master redis-cli FLUSHALL > /dev/null 2>&1 || true
    ;;
  *)
    echo "Usage: $0 <1|2|3|4>"
    echo "  1 = 캐시X + 인덱스X  (Baseline)"
    echo "  2 = 캐시X + 인덱스O  (인덱스만)"
    echo "  3 = 캐시O + 인덱스X  (캐시만)"
    echo "  4 = 캐시O + 인덱스O  (둘 다)"
    exit 1
    ;;
esac

echo ""
echo "→ k6 부하 테스트 시작 [$LABEL]..."
echo ""

k6 run \
  --summary-export="$SCRIPT_DIR/summary-${LABEL}.json" \
  --env BASE_URL=http://localhost:8080 \
  --env LABEL="$LABEL" \
  "$SCRIPT_DIR/load-test.js"

echo ""
echo "══════════════════════════════════════"
echo "  [$LABEL] 완료"
echo "  결과: k6/summary-${LABEL}.json"
echo "══════════════════════════════════════"
