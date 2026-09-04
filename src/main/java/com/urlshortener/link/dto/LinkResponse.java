package com.urlshortener.link.dto;

import com.urlshortener.link.ShortLink;

import java.time.OffsetDateTime;

public record LinkResponse(String shortCode, String shortUrl, String longUrl, OffsetDateTime createdAt) {

    public static LinkResponse from(ShortLink shortLink, String shortUrl) {
        return new LinkResponse(shortLink.getShortCode(), shortUrl, shortLink.getLongUrl(), shortLink.getCreatedAt());
    }
}
