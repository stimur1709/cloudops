package com.github.stimur1709.cloudops.common.persistence.search;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.github.stimur1709.cloudops.common.search.InvalidSearchException;

public final class SearchValueConverter<T> {

    private final Function<String, T> converter;
    private final String invalidValueMessage;

    private SearchValueConverter(Function<String, T> converter, String invalidValueMessage) {
        this.converter = Objects.requireNonNull(converter);
        this.invalidValueMessage = Objects.requireNonNull(invalidValueMessage);
    }

    public static SearchValueConverter<String> stringValue() {
        return new SearchValueConverter<>(Function.identity(), "Value must be a string");
    }

    public static SearchValueConverter<Long> longInteger() {
        return of(Long::valueOf, "Value must be a valid integer number");
    }

    public static SearchValueConverter<Instant> instant() {
        return of(Instant::parse, "Value must be a valid ISO-8601 instant");
    }

    public static <E extends Enum<E>> SearchValueConverter<E> enumeration(Class<E> enumType) {
        String allowedValues = Arrays.stream(enumType.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        return of(
                value -> Enum.valueOf(enumType, value),
                "Value must be one of: " + allowedValues
        );
    }

    public static <T> SearchValueConverter<T> of(
            Function<String, T> converter,
            String invalidValueMessage
    ) {
        return new SearchValueConverter<>(converter, invalidValueMessage);
    }

    T convert(String value, String fieldPath) {
        try {
            return converter.apply(value);
        } catch (RuntimeException exception) {
            throw new InvalidSearchException(fieldPath, invalidValueMessage, exception);
        }
    }
}
