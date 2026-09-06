package com.urlshortener.link;

/** Thrown when a requested custom alias already has a live short link. */
public class AliasAlreadyTakenException extends RuntimeException {

    public AliasAlreadyTakenException(String alias) {
        super("Alias '" + alias + "' is already in use");
    }
}
