package com.urlshortener.link.dto;

import java.time.OffsetDateTime;

/** Uniform error body used for every failure response, so every endpoint fails the same way. */
public record ErrorResponse(String error, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, OffsetDateTime.now());
    }
}
