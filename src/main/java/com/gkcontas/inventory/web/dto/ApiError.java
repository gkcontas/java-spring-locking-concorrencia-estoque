package com.gkcontas.inventory.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        String code,
        String message,
        List<String> details,
        Instant timestamp) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, List.of(), Instant.now());
    }

    public static ApiError of(int status, String code, String message, List<String> details) {
        return new ApiError(status, code, message, details, Instant.now());
    }
}
