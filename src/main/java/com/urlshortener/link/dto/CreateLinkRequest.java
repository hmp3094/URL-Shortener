package com.urlshortener.link.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(

        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must not exceed 2048 characters")
        String url,

        // Opt-in only: null (the default, and the only option before this field existed) means
        // the link never expires. No default duration is applied when this is omitted.
        @Positive(message = "expiresInSeconds must be positive")
        @Max(value = 31_536_000, message = "expiresInSeconds must not exceed 31536000 (365 days)")
        Long expiresInSeconds) {
}
