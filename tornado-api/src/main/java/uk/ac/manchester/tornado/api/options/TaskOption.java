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

import java.util.Objects;
import java.util.Optional;

/**
 * An option set per task graph or per task, as {@code <id>.<suffix>}: {@code s0.t0.device=1:0} for task
 * {@code t0} of task graph {@code s0}.
 *
 * <p>
 * An option {@linkplain Builder#withGlobalFallback() with a global fallback} can also be set for every task at
 * once as {@code tornado.<suffix>}; the per-task key wins.
 *
 * @param <T>
 *     the type of the value
 */
public final class TaskOption<T> {

    private static final String GLOBAL_PREFIX = "tornado.";

    private final String suffix;
    private final OptionParser<T> parser;
    private final T defaultValue;
    private final String description;
    private final OptionCategory category;
    private final boolean globalFallback;

    private TaskOption(Builder<T> builder) {
        this.suffix = builder.suffix;
        this.parser = builder.parser;
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
        this.category = builder.category;
        this.globalFallback = builder.globalFallback;
    }

    public static Builder<Integer> integer(String suffix) {
        return new Builder<>(suffix, OptionParsers.INTEGER);
    }

    public static Builder<String> string(String suffix) {
        return new Builder<>(suffix, OptionParsers.STRING);
    }

    /**
     * @return the value for the task, its global fallback, or the default
     * @throws IllegalStateException
     *     if neither is set and the option has no default
     */
    public T forTask(String id) {
        return lookup(id).or(() -> Optional.ofNullable(defaultValue)) //
                .orElseThrow(() -> new IllegalStateException("Task option " + suffix + " has no default"));
    }

    /**
     * @return the value set for the task, or else its global fallback
     */
    public Optional<T> lookup(String id) {
        return OptionResolver.global().lookup(this, id);
    }

    /**
     * @return whether the value is set for this task specifically, ignoring the global fallback
     */
    public boolean isSetFor(String id) {
        return OptionResolver.global().isSetFor(this, id);
    }

    public String taskKey(String id) {
        return id + "." + suffix;
    }

    /**
     * @return {@code tornado.<suffix>}, if the option has a global fallback
     */
    public Optional<String> globalFallbackKey() {
        return globalFallback ? Optional.of(GLOBAL_PREFIX + suffix) : Optional.empty();
    }

    public String suffix() {
        return suffix;
    }

    public OptionParser<T> parser() {
        return parser;
    }

    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    public String description() {
        return description;
    }

    public OptionCategory category() {
        return category;
    }

    @Override
    public String toString() {
        return "<id>." + suffix;
    }

    public static final class Builder<T> {

        private final String suffix;
        private final OptionParser<T> parser;
        private T defaultValue;
        private String description;
        private OptionCategory category;
        private boolean globalFallback;

        private Builder(String suffix, OptionParser<T> parser) {
            this.suffix = Objects.requireNonNull(suffix);
            this.parser = Objects.requireNonNull(parser);
        }

        public Builder<T> defaultValue(T value) {
            this.defaultValue = Objects.requireNonNull(value);
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

        public Builder<T> withGlobalFallback() {
            this.globalFallback = true;
            return this;
        }

        public TaskOption<T> build() {
            if (description == null || category == null) {
                throw new IllegalStateException("Task option " + suffix + " needs a description and a category");
            }
            return new TaskOption<>(this);
        }
    }
}
