#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PRODUCT_ID="${PRODUCT_ID:-1}"

echo "══════════════════════════════════════"
echo "  대기열 부하 테스트"
echo "══════════════════════════════════════"

# 1. 대기열 활성화
echo "→ 대기열 활성화 (productId=$PRODUCT_ID)..."
curl -s -X POST "http://localhost:8080/api/admin/queue/products/$PRODUCT_ID/activate" || true
echo ""

run_scenario() {
    local scenario=$1
    local label=$2
    echo ""
    echo "── [$label] 시작 ──"
    k6 run \
        --out json="$SCRIPT_DIR/result-queue-$scenario.json" \
        --summary-export="$SCRIPT_DIR/summary-queue-$scenario.json" \
        --env BASE_URL=http://localhost:8080 \
        --env PRODUCT_ID="$PRODUCT_ID" \
        --env TEST_SCENARIO="$scenario" \
        "$SCRIPT_DIR/queue-load-test.js"
    echo "── [$label] 완료 ──"
}

case "${1:-all}" in
    enter)
        run_scenario "enter" "대기열 진입 부하"
        ;;
    polling)
        run_scenario "polling" "순번 조회 부하"
        ;;
    full)
        run_scenario "full" "전체 흐름"
        ;;
    all)
        run_scenario "enter" "대기열 진입 부하"
        run_scenario "polling" "순번 조회 부하"
        run_scenario "full" "전체 흐름"
        ;;
    *)
        echo "Usage: $0 [enter|polling|full|all]"
        exit 1
        ;;
esac

echo ""
echo "══════════════════════════════════════"
echo "  부하 테스트 완료"
echo "══════════════════════════════════════"
