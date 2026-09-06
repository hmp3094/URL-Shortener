package com.urlshortener.unit;

import com.urlshortener.validation.CustomAliasValidator;
import com.urlshortener.validation.InvalidAliasException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomAliasValidatorTest {

    private final CustomAliasValidator validator = new CustomAliasValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "abc", // minimum length (3)
            "summer-sale",
            "Promo2026",
            "with_underscore",
            "with-hyphen"
    })
    void acceptsEveryWellFormedAliasShape(String alias) {
        assertThatCode(() -> validator.validate(alias)).doesNotThrowAnyException();
    }

    @Test
    void acceptsAliasAtMaximumLength() {
        String alias = "a".repeat(32);
        assertThatCode(() -> validator.validate(alias)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a"})
    void rejectsAnAliasShorterThanTheMinimumLength(String alias) {
        assertThatThrownBy(() -> validator.validate(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("between");
    }

    @Test
    void rejectsAnAliasLongerThanTheMaximumLength() {
        assertThatThrownBy(() -> validator.validate("a".repeat(33)))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("between");
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "has/slash", "has.dot", "has$symbol"})
    void rejectsAnAliasWithADisallowedCharacter(String alias) {
        assertThatThrownBy(() -> validator.validate(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("letters, digits, hyphens, and underscores");
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "actuator", "health", "error", "swagger-ui", "ACTUATOR"})
    void rejectsAReservedName(String alias) {
        assertThatThrownBy(() -> validator.validate(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("reserved");
    }
}
