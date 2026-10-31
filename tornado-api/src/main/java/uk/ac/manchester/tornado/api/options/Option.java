/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.options;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A TornadoVM option: a {@code tornado.*} key with a type, a default and a description.
 *
 * <p>
 * Options are declared once, as constants of the catalog classes ({@link RuntimeFlags}, {@link DebugFlags}, ...),
 * and read with {@link #get()}. The value comes from {@link OptionResolver#global()}: {@code -D} on the command
 * line, then {@code etc/tornado.properties}, then the default.
 *
 * <p>
 * A static option is resolved once, the first time it is read, and keeps that value. A {@linkplain Builder#dynamic()
 * dynamic} option is resolved on every read, for options that may be changed while the program runs.
 *
 * @param <T>
 *     the type of the value
 */
public final class Option<T> {

    private final String key;
    private final OptionParser<T> parser;
    private final Supplier<T> defaultValue;
    private final String defaultDescription;
    private final String description;
    private final OptionCategory category;
    private final OptionVisibility visibility;
    private final List<String> deprecatedKeys;
    private final boolean dynamic;

    private volatile T resolved;

    private Option(Builder<T> builder) {
        this.key = builder.key;
        this.parser = builder.parser;
        this.defaultValue = builder.defaultValue;
        this.defaultDescription = builder.defaultDescription;
        this.description = builder.description;
        this.category = builder.category;
        this.visibility = builder.visibility;
        this.deprecatedKeys = List.copyOf(builder.deprecatedKeys);
        this.dynamic = builder.dynamic;
    }

    public static Builder<Boolean> bool(String key) {
        return new Builder<>(key, OptionParsers.BOOLEAN);
    }

    public static Builder<Integer> integer(String key) {
        return new Builder<>(key, OptionParsers.INTEGER);
    }

    public static Builder<Long> longValue(String key) {
        return new Builder<>(key, OptionParsers.LONG);
    }

    public static Builder<Long> size(String key) {
        return new Builder<>(key, OptionParsers.SIZE);
    }

    public static Builder<String> string(String key) {
        return new Builder<>(key, OptionParsers.STRING);
    }

    public static <E extends Enum<E>> Builder<E> enumOf(String key, Class<E> type) {
        return new Builder<>(key, OptionParsers.enumOf(type));
    }

    /**
     * @return the value of this option, from {@link OptionResolver#global()}
     * @throws InvalidOptionValueException
     *     if the option is set to text its type does not accept
     * @throws IllegalStateException
     *     if the option has no default and is not set; use {@link #find()} for such options
     */
    public T get() {
        if (dynamic) {
            return OptionResolver.global().resolve(this);
        }
        T value = resolved;
        if (value == null) {
            value = OptionResolver.global().resolve(this);
            resolved = value;
        }
        return value;
    }

    /**
     * @return the value the option is set to, ignoring the default
     */
    public Optional<T> find() {
        return OptionResolver.global().find(this);
    }

    /**
     * @return whether the option is set, under its key or a deprecated one, rather than taking its default
     */
    public boolean isSet() {
        return OptionResolver.global().text(this).isPresent();
    }

    public String key() {
        return key;
    }

    public OptionParser<T> parser() {
        return parser;
    }

    /**
     * @return the default, or empty for an option {@linkplain Builder#noDefault(String) without one}
     */
    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue.get());
    }

    /**
     * @return the default as shown in the documentation; differs from the value when the default is computed
     */
    public String defaultDescription() {
        return defaultDescription;
    }

    public String description() {
        return description;
    }

    public OptionCategory category() {
        return category;
    }

    public OptionVisibility visibility() {
        return visibility;
    }

    /**
     * @return earlier names of this option, still honoured with a warning
     */
    public List<String> deprecatedKeys() {
        return deprecatedKeys;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    @Override
    public String toString() {
        return key;
    }

    public static final class Builder<T> {

        private final String key;
        private final OptionParser<T> parser;
        private Supplier<T> defaultValue;
        private String defaultDescription;
        private String description;
        private OptionCategory category;
        private OptionVisibility visibility = OptionVisibility.PUBLIC;
        private final List<String> deprecatedKeys = new ArrayList<>();
        private boolean dynamic;

        private Builder(String key, OptionParser<T> parser) {
            this.key = Objects.requireNonNull(key);
            this.parser = Objects.requireNonNull(parser);
        }

        public Builder<T> defaultValue(T value) {
            Objects.requireNonNull(value);
            this.defaultValue = () -> value;
            this.defaultDescription = String.valueOf(value);
            return this;
        }

        /**
         * A default computed when it is needed, such as one that depends on the number of cores.
         *
         * @param shownAs
         *     how the default reads in the documentation
         */
        public Builder<T> defaultValue(Supplier<T> value, String shownAs) {
            this.defaultValue = Objects.requireNonNull(value);
            this.defaultDescription = Objects.requireNonNull(shownAs);
            return this;
        }

        /**
         * The option has no default: unset means the consumer's own behaviour, read with {@link Option#find()}.
         *
         * @param shownAs
         *     what unset means, as shown in the documentation
         */
        public Builder<T> noDefault(String shownAs) {
            this.defaultValue = () -> null;
            this.defaultDescription = Objects.requireNonNull(shownAs);
            return this;
        }

        public Builder<T> description(String text) {
            this.description = text;
            return this;
        }

        public Builder<T> category(OptionCategory value) {
            this.category = value;
            return this;
        }

        public Builder<T> internal() {
            this.visibility = OptionVisibility.INTERNAL;
            return this;
        }

        public Builder<T> dynamic() {
            this.dynamic = true;
            return this;
        }

        public Builder<T> deprecatedKey(String oldKey) {
            this.deprecatedKeys.add(oldKey);
            return this;
        }

        public Option<T> build() {
            requireSet(defaultValue, "default value");
            requireSet(description, "description");
            requireSet(category, "category");
            return new Option<>(this);
        }

        private void requireSet(Object value, String what) {
            if (value == null) {
                throw new IllegalStateException("Option " + key + " has no " + what);
            }
        }
    }
}
