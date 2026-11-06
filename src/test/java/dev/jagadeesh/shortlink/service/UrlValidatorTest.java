package dev.jagadeesh.shortlink.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void acceptsHttpAndHttps() {
        assertThat(validator.validate(" https://example.com/a?b=1 ", "http://sho.rt").getHost()).isEqualTo("example.com");
        assertThat(validator.validate("http://example.com", "http://sho.rt")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "javascript:alert(1)", "example.com", "https://", "http://exa mple.com"})
    void rejectsUnsafeOrMalformedUrls(String url) {
        assertThatThrownBy(() -> validator.validate(url, "http://sho.rt")).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsLinksToItself() {
        assertThatThrownBy(() -> validator.validate("http://SHO.RT/abc1234", "http://sho.rt"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("another short link");
    }
}
