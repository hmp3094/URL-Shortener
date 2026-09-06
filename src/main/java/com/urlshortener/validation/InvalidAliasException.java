package com.urlshortener.validation;

/**
 * Thrown when a caller-supplied alias fails format validation (length, character set, or a
 * reserved name) and must be rejected before any availability check is performed.
 */
public class InvalidAliasException extends RuntimeException {

    public InvalidAliasException(String message) {
        super(message);
    }
}
