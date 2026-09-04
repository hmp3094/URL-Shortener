package com.urlshortener.validation;

/**
 * Thrown when a submitted destination URL fails validation (malformed, disallowed scheme, or
 * resolves to a loopback/private/link-local address) and must be rejected before any mapping
 * is created.
 */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
