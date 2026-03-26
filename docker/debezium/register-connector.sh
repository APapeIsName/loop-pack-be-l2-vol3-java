#!/bin/bash
# Kafka Connect가 준비될 때까지 대기 후 Debezium connector 등록

echo "Waiting for Kafka Connect..."
until curl -s http://localhost:8084/connectors > /dev/null 2>&1; do
  sleep 2
done

echo "Registering outbox connector..."
curl -X POST http://localhost:8084/connectors \
  -H "Content-Type: application/json" \
  -d @"$(dirname "$0")/register-connector.json"

echo ""
echo "Done. Checking connector status..."
curl -s http://localhost:8084/connectors/outbox-connector/status | python3 -m json.tool
