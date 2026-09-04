package com.urlshortener.link.dto;

import com.urlshortener.link.ShortLink;

import java.time.OffsetDateTime;

public record LinkStatsResponse(
        String shortCode,
        String longUrl,
        long clickCount,
        OffsetDateTime createdAt,
        OffsetDateTime lastAccessedAt,
        OffsetDateTime expiresAt) {

    public static LinkStatsResponse from(ShortLink shortLink) {
        return new LinkStatsResponse(
                shortLink.getShortCode(),
                shortLink.getLongUrl(),
                shortLink.getClickCount(),
                shortLink.getCreatedAt(),
                shortLink.getLastAccessedAt(),
                shortLink.getExpiresAt());
    }
}
