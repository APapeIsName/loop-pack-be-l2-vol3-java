#!/bin/bash
# 부하 테스트 중 톰캣 스레드 상태 모니터링
# 사용법: ./k6/monitor-threads.sh (부하 테스트 전에 실행, Ctrl+C로 종료)

PID=$(lsof -ti:8080 2>/dev/null)
if [ -z "$PID" ]; then
  echo "서버가 안 떠있음"
  exit 1
fi

echo "=== 톰캣 스레드 모니터링 (PID: $PID) ==="
echo "시각 | 전체 | 활성(RUNNABLE) | 대기(WAITING) | 블록(BLOCKED)"
echo "─────────────────────────────────────────────────────────"

while true; do
  DUMP=$(jstack $PID 2>/dev/null | grep "http-nio-8080")
  TOTAL=$(echo "$DUMP" | wc -l | tr -d ' ')
  RUNNABLE=$(echo "$DUMP" | grep -c "RUNNABLE")
  WAITING=$(echo "$DUMP" | grep -c "WAITING\|TIMED_WAITING")
  BLOCKED=$(echo "$DUMP" | grep -c "BLOCKED")

  TIME=$(date +%H:%M:%S)
  printf "%s | %3d   | %3d            | %3d           | %3d\n" "$TIME" "$TOTAL" "$RUNNABLE" "$WAITING" "$BLOCKED"

  sleep 1
done
