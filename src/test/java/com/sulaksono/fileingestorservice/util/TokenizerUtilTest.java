package com.sulaksono.fileingestorservice.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizerUtilTest {

    @Test
    void countTokens_simpleText_shouldReturnPositiveCount() {
        int count = TokenizerUtil.countTokens("hello world");

        assertThat(count).isPositive();
    }

    @Test
    void countTokens_emptyText_shouldReturnZero() {
        int count = TokenizerUtil.countTokens("");

        assertThat(count).isZero();
    }

    @Test
    void countTokens_nullText_shouldReturnZero() {
        int count = TokenizerUtil.countTokens(null);

        assertThat(count).isZero();
    }

    @Test
    void slice_fullRange_shouldReturnOriginalText() {
        String text = "hello world";
        int tokenCount = TokenizerUtil.countTokens(text);

        String result = TokenizerUtil.slice(text, 0, tokenCount);

        assertThat(result).isEqualTo(text);
    }

    @Test
    void slice_partialRange_shouldReturnNonBlankText() {
        String text = "hello world from tokenizer util";

        String result = TokenizerUtil.slice(text, 0, 2);

        assertThat(result).isNotBlank();
        assertThat(text).contains(result.trim().split("\\s+")[0]);
    }

    @Test
    void slice_endBeyondTokenCount_shouldCapToTokenCount() {
        String text = "hello world";
        int tokenCount = TokenizerUtil.countTokens(text);

        String result = TokenizerUtil.slice(text, 0, tokenCount + 100);

        assertThat(result).isEqualTo(text);
    }

    @Test
    void slice_negativeBegin_shouldTreatBeginAsZero() {
        String text = "hello world";
        int tokenCount = TokenizerUtil.countTokens(text);

        String result = TokenizerUtil.slice(text, -10, tokenCount);

        assertThat(result).isEqualTo(text);
    }

    @Test
    void slice_beginGreaterThanTokenCount_shouldReturnEmptyString() {
        String text = "hello world";
        int tokenCount = TokenizerUtil.countTokens(text);

        String result = TokenizerUtil.slice(text, tokenCount + 10, tokenCount + 20);

        assertThat(result).isEmpty();
    }

    @Test
    void slice_endLessThanBegin_shouldReturnEmptyString() {
        String result = TokenizerUtil.slice("hello world", 5, 2);

        assertThat(result).isEmpty();
    }

    @Test
    void slice_endEqualsBegin_shouldReturnEmptyString() {
        String result = TokenizerUtil.slice("hello world", 2, 2);

        assertThat(result).isEmpty();
    }

    @Test
    void slice_emptyText_shouldReturnEmptyString() {
        String result = TokenizerUtil.slice("", 0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void slice_nullText_shouldReturnEmptyString() {
        String result = TokenizerUtil.slice(null, 0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void constructor_shouldBePrivate() throws Exception {
        Constructor<TokenizerUtil> constructor =
                TokenizerUtil.class.getDeclaredConstructor();

        assertThat(constructor.canAccess(null)).isFalse();

        constructor.setAccessible(true);
        TokenizerUtil instance = constructor.newInstance();

        assertThat(instance).isNotNull();
    }
}