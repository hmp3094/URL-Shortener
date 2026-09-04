package com.urlshortener.link;

/** Thrown when a redirect is requested for a short code that has no mapping. */
public class ShortLinkNotFoundException extends RuntimeException {

    public ShortLinkNotFoundException(String shortCode) {
        super("No short link exists for code: " + shortCode);
    }
}
