package com.github.stimur1709.cloudops.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebCorsPropertiesTest {

    @Test
    void acceptsExplicitHttpOriginsAndCopiesLists() {
        var origins = new ArrayList<>(List.of("http://localhost:5173", "https://app.example.com"));

        var properties = properties(origins);
        origins.clear();

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:5173", "https://app.example.com")
                .isUnmodifiable();
    }

    @Test
    void acceptsEmptyOriginsToKeepCrossOriginAccessClosed() {
        assertThat(properties(List.of()).allowedOrigins()).isEmpty();
    }

    @Test
    void rejectsWildcardAndValuesThatAreNotOrigins() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of("*")));
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of("https://app.example.com/path")));
        assertThatIllegalArgumentException().isThrownBy(() -> properties(List.of("ftp://app.example.com")));
    }

    @Test
    void rejectsUnsafeOrIncompletePolicy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WebCorsProperties(
                        List.of("https://app.example.com"),
                        List.of("GET"),
                        List.of("*"),
                        List.of(),
                        true,
                        Duration.ofMinutes(30)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WebCorsProperties(
                        List.of("https://app.example.com"),
                        List.of("GET WITH SPACE"),
                        List.of("Authorization"),
                        List.of(),
                        true,
                        Duration.ofMinutes(30)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WebCorsProperties(
                        List.of("https://app.example.com"),
                        List.of(),
                        List.of("Authorization"),
                        List.of(),
                        true,
                        Duration.ofMinutes(30)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WebCorsProperties(
                        List.of("https://app.example.com"),
                        List.of("GET"),
                        List.of("Authorization"),
                        List.of(),
                        true,
                        Duration.ofSeconds(-1)));
    }

    private WebCorsProperties properties(List<String> origins) {
        return new WebCorsProperties(
                origins,
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
                List.of("Authorization", "Content-Type"),
                List.of(),
                true,
                Duration.ofMinutes(30));
    }
}
