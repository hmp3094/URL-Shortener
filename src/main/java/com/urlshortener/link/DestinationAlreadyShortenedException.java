package com.urlshortener.link;

/**
 * Thrown when a creation request supplies a custom alias for a destination URL that already
 * has a live short link under a different code. Unlike the no-alias path (which silently
 * returns the existing link), a caller who explicitly asked for a specific alias must be told
 * their alias wasn't honored rather than getting a different code back without explanation.
 */
public class DestinationAlreadyShortenedException extends RuntimeException {

    public DestinationAlreadyShortenedException() {
        super("This URL already has a short link under a different code");
    }
}
