package com.github.stimur1709.cloudops.probe.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.github.stimur1709.cloudops.probe.ProbeType;
import org.junit.jupiter.api.Test;

class ProbeHandlerRegistryTest {

    @Test
    void returnsRegisteredHandler() {
        ProbeHandler handler = handler(ProbeType.HTTP_CHECK);
        ProbeHandlerRegistry registry = new ProbeHandlerRegistry(List.of(handler));

        assertThat(registry.get(ProbeType.HTTP_CHECK)).isSameAs(handler);
    }

    @Test
    void rejectsDuplicateHandler() {
        ProbeHandler first = handler(ProbeType.HTTP_CHECK);
        ProbeHandler second = handler(ProbeType.HTTP_CHECK);

        assertThatIllegalStateException()
                .isThrownBy(() -> new ProbeHandlerRegistry(List.of(first, second)))
                .withMessageContaining("HTTP_CHECK");
    }

    @Test
    void reportsMissingHandler() {
        ProbeHandlerRegistry registry = new ProbeHandlerRegistry(List.of());

        assertThatExceptionOfType(ProbeHandlerNotFoundException.class)
                .isThrownBy(() -> registry.get(ProbeType.HTTP_CHECK))
                .withMessageContaining("HTTP_CHECK");
    }

    private ProbeHandler handler(ProbeType type) {
        ProbeHandler handler = mock(ProbeHandler.class);
        when(handler.supports()).thenReturn(type);
        return handler;
    }
}
