package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class DebeziumMessageParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractPayload(ConsumerRecord<String, ?> record) {
        Object value = record.value();

        if (value instanceof String jsonString) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(jsonString, Map.class);
                if (parsed.containsKey("payload") && parsed.get("payload") instanceof Map) {
                    return (Map<String, Object>) parsed.get("payload");
                }
                return parsed;
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("JSON 파싱 실패: " + jsonString, e);
            }
        }

        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            if (map.containsKey("payload") && map.get("payload") instanceof Map) {
                return (Map<String, Object>) map.get("payload");
            }
            return map;
        }

        throw new IllegalArgumentException("메시지 파싱 실패 — 예상치 못한 타입: " + value.getClass());
    }

    public static String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
