package com.urlshortener.unit;

import com.urlshortener.validation.DestinationUrlValidator;
import com.urlshortener.validation.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationUrlValidatorTest {

    private final DestinationUrlValidator validator = new DestinationUrlValidator();

    // 192.0.2.0/24 (TEST-NET-1, RFC 5737) is reserved for documentation: it is a literal IP
    // (no real DNS lookup needed) and is neither loopback, RFC1918-private, nor link-local.
    @ParameterizedTest
    @ValueSource(strings = {"http://192.0.2.1/path", "https://192.0.2.1:8443/x?y=1"})
    void acceptsWellFormedHttpAndHttpsUrlsTargetingAPublicAddress(String url) {
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-url"})
    void rejectsBlankOrMalformedInput(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "file:///etc/passwd", "ftp://192.0.2.1/file"})
    void rejectsDisallowedSchemes(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/admin",
            "http://10.0.0.5/internal",
            "http://192.168.1.1/router",
            "http://169.254.169.254/latest/meta-data"
    })
    void rejectsUrlsTargetingLoopbackPrivateOrLinkLocalAddresses(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }
}
