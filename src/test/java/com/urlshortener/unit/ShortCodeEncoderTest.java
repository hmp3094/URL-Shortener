package com.urlshortener.unit;

import com.urlshortener.link.ShortCodeEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeEncoderTest {

    private static final long MAX_CAPACITY = 36L * 36 * 36 * 36 * 36 * 36; // 36^6

    @Test
    void encodesZeroAsAllPaddingCharacters() {
        assertThat(ShortCodeEncoder.encode(0)).isEqualTo("000000");
    }

    @Test
    void encodesOneWithZeroPadding() {
        assertThat(ShortCodeEncoder.encode(1)).isEqualTo("000001");
    }

    @Test
    void encodesLastSingleDigitValue() {
        // 35 is the last value representable by a single base36 digit ('z')
        assertThat(ShortCodeEncoder.encode(35)).isEqualTo("00000z");
    }

    @Test
    void encodesFirstTwoDigitValue() {
        assertThat(ShortCodeEncoder.encode(36)).isEqualTo("000010");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 35L, 36L, 1_000L, 999_999L, MAX_CAPACITY - 1})
    void encodeIsAlwaysExactlySixLowercaseAlphanumericCharacters(long value) {
        String code = ShortCodeEncoder.encode(value);
        assertThat(code).hasSize(6);
        assertThat(code).matches("^[a-z0-9]{6}$");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 35L, 36L, 1_000L, 999_999L, MAX_CAPACITY - 1})
    void decodeReversesEncode(long value) {
        String code = ShortCodeEncoder.encode(value);
        assertThat(ShortCodeEncoder.decode(code)).isEqualTo(value);
    }

    @Test
    void encodeRejectsValuesAtOrBeyondCapacity() {
        assertThatThrownBy(() -> ShortCodeEncoder.encode(MAX_CAPACITY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encodeRejectsNegativeValues() {
        assertThatThrownBy(() -> ShortCodeEncoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
