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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Finds the value of an option: reads its text from an {@link OptionSource}, honours deprecated keys, and parses
 * the text, falling back to the default when the option is not set.
 */
public final class OptionResolver {

    private static final String TORNADOVM_HOME = "TORNADOVM_HOME";
    private static final String PROPERTIES_FILE = "etc/tornado.properties";
    private static final String WARNING_PREFIX = "[TornadoVM] WARNING: ";

    private final OptionSource source;
    private final Consumer<String> warnings;
    private final Set<String> reportedDeprecatedKeys = ConcurrentHashMap.newKeySet();

    /**
     * @param source
     *     where option text comes from
     * @param warnings
     *     told once per deprecated key in use
     */
    public OptionResolver(OptionSource source, Consumer<String> warnings) {
        this.source = source;
        this.warnings = warnings;
    }

    /**
     * @return the resolver every {@link Option#get()} uses: {@code -D} system properties first, then
     *     {@code $TORNADOVM_HOME/etc/tornado.properties}, with warnings on standard error
     */
    public static OptionResolver global() {
        return Global.INSTANCE;
    }

    public OptionSource source() {
        return source;
    }

    /**
     * @return the text the option is set to, under its key or else under one of its deprecated keys
     */
    public Optional<String> text(Option<?> option) {
        Optional<String> text = source.lookup(option.key());
        if (text.isPresent()) {
            return text;
        }
        for (String deprecatedKey : option.deprecatedKeys()) {
            text = source.lookup(deprecatedKey);
            if (text.isPresent()) {
                reportDeprecated(deprecatedKey, option.key());
                return text;
            }
        }
        return Optional.empty();
    }

    /**
     * @return the value the option is set to, or else its default
     * @throws IllegalStateException
     *     if the option has no default and is not set
     */
    public <T> T resolve(Option<T> option) {
        return find(option).or(option::defaultValue).orElseThrow(() -> new IllegalStateException("Option " + option.key() + " is not set and has no default"));
    }

    /**
     * @return the value the option is set to, ignoring its default
     */
    public <T> Optional<T> find(Option<T> option) {
        return text(option).flatMap(text -> parse(option.key(), option.parser(), text));
    }

    /**
     * @return the value set for the task, or else the option's global fallback
     */
    public <T> Optional<T> lookup(TaskOption<T> option, String id) {
        Optional<T> value = resolve(option.taskKey(id), option.parser());
        if (value.isEmpty()) {
            value = option.globalFallbackKey().flatMap(key -> resolve(key, option.parser()));
        }
        return value;
    }

    public boolean isSetFor(TaskOption<?> option, String id) {
        return resolve(option.taskKey(id), option.parser()).isPresent();
    }

    private <T> Optional<T> resolve(String key, OptionParser<T> parser) {
        return source.lookup(key).flatMap(text -> parse(key, parser, text));
    }

    private static <T> Optional<T> parse(String key, OptionParser<T> parser, String text) {
        if (text.isBlank() && !parser.acceptsBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.parse(text));
        } catch (IllegalArgumentException e) {
            throw new InvalidOptionValueException(key, text, parser.typeName(), e);
        }
    }

    private void reportDeprecated(String deprecatedKey, String key) {
        if (reportedDeprecatedKeys.add(deprecatedKey)) {
            warnings.accept(String.format("-D%s is deprecated and will be removed in a future release; use -D%s instead.", deprecatedKey, key));
        }
    }

    /**
     * Prints a warning on standard error, the way every TornadoVM option warning is reported.
     */
    public static void warn(String message) {
        System.err.println(WARNING_PREFIX + message);
    }

    private static final class Global {

        private static final OptionResolver INSTANCE = create();

        private static OptionResolver create() {
            String home = System.getenv(TORNADOVM_HOME);
            OptionSource fileSource = home == null ? new LayeredSource(List.of()) : PropertiesFileSource.load(Path.of(home, PROPERTIES_FILE), OptionResolver::warn);
            return new OptionResolver(new LayeredSource(List.of(new SystemPropertySource(), fileSource)), OptionResolver::warn);
        }
    }
}
