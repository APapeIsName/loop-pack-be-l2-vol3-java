package com.loopers.infrastructure.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PgApiResponse<T>(Meta meta, T data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(String result, String errorCode, String message) {

        public boolean isSuccess() {
            return "SUCCESS".equalsIgnoreCase(result);
        }
    }

    public static <T> PgApiResponse<T> success(T data) {
        return new PgApiResponse<>(new Meta("SUCCESS", null, null), data);
    }
}
