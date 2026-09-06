package com.urlshortener.validation;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a caller-supplied alias's shape: length, character set, and reserved names. Never
 * checks availability — that's resolved atomically at insert time (see
 * {@code ShortLinkRepository#insertWithAlias}), since a format check here can't race with a
 * concurrent request the way an availability pre-check could.
 */
@Component
public class CustomAliasValidator {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 32;
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("^[A-Za-z0-9_-]+$");

    // The literal first-path-segment names this application's own routes and Spring
    // Boot/springdoc defaults actually use today. Extend this set whenever a new root-level
    // route is added to the application. "v3" (springdoc's /v3/api-docs) is deliberately not
    // listed: it's only 2 characters, already unreachable as an alias below MIN_LENGTH, and the
    // real springdoc route is a two-segment path this single-segment {code} route never matches
    // anyway — listing it here would be dead, misleading defense.
    private static final Set<String> RESERVED_NAMES =
            Set.of("api", "actuator", "health", "error", "swagger-ui");

    public void validate(String alias) {
        if (alias.length() < MIN_LENGTH || alias.length() > MAX_LENGTH) {
            throw new InvalidAliasException(
                    "alias must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }

        if (!ALLOWED_CHARACTERS.matcher(alias).matches()) {
            throw new InvalidAliasException(
                    "alias must contain only letters, digits, hyphens, and underscores");
        }

        if (RESERVED_NAMES.contains(alias.toLowerCase())) {
            throw new InvalidAliasException("alias '" + alias + "' is a reserved name");
        }
    }
}
