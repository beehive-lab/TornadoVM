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
package uk.ac.manchester.tornado.api.internal.options;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import uk.ac.manchester.tornado.api.options.DeprecatedKey;
import uk.ac.manchester.tornado.api.options.InvalidOptionValueException;
import uk.ac.manchester.tornado.api.options.Option;
import uk.ac.manchester.tornado.api.options.OptionParser;
import uk.ac.manchester.tornado.api.options.RuntimeFlags;
import uk.ac.manchester.tornado.api.options.TaskOption;

/**
 * Finds the value of an option: reads its text from an {@link OptionSource}, honours deprecated keys, and parses
 * the text, falling back to the default when the option is not set.
 */
public final class OptionResolver {

    private static final String TORNADOVM_HOME = "TORNADOVM_HOME";
    private static final String PROPERTIES_FILE = "etc/tornado.properties";
    private static final String WARNING_PREFIX = "[TornadoVM] WARNING: ";

    private static volatile OptionResolver global;

    private final OptionSource source;
    private final Consumer<String> warnings;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /**
     * @param source
     *     where option text comes from
     * @param warnings
     *     told once per deprecated key in use
     */
    public OptionResolver(OptionSource source, Consumer<String> warnings) {
        this.source = Objects.requireNonNull(source);
        this.warnings = Objects.requireNonNull(warnings);
    }

    /**
     * @return the resolver every {@link Option#get()} uses: {@code -D} system properties first, then the file of
     *     {@code -Dtornado.options.file}, then {@code $TORNADOVM_HOME/etc/tornado.properties}, with warnings on
     *     standard error
     */
    public static OptionResolver global() {
        OptionResolver resolver = global;
        if (resolver == null) {
            synchronized (OptionResolver.class) {
                resolver = global;
                if (resolver == null) {
                    resolver = createDefault();
                    global = resolver;
                }
            }
        }
        return resolver;
    }

    /**
     * Replaces the global resolver, for tests. Every static option is read again from the new one.
     *
     * @return the resolver it replaces
     */
    public static synchronized OptionResolver install(OptionResolver resolver) {
        OptionResolver previous = global();
        global = Objects.requireNonNull(resolver);
        return previous;
    }

    /**
     * @return a resolver over the system properties and the properties files, as at start-up
     */
    public static OptionResolver createDefault() {
        List<OptionSource> layers = new ArrayList<>();
        layers.add(new SystemPropertySource());
        String optionsFile = System.getProperty(RuntimeFlags.OPTIONS_FILE.key());
        if (optionsFile != null && !optionsFile.isBlank()) {
            Path file = Path.of(optionsFile);
            if (!file.toFile().isFile()) {
                warn("-D" + RuntimeFlags.OPTIONS_FILE.key() + "=" + optionsFile + " is not a file; it is ignored.");
            }
            layers.add(PropertiesFileSource.load(file, OptionResolver::warn));
        }
        String home = System.getenv(TORNADOVM_HOME);
        if (home != null) {
            layers.add(PropertiesFileSource.load(Path.of(home, PROPERTIES_FILE), OptionResolver::warn));
        }
        return new OptionResolver(new LayeredSource(layers), OptionResolver::warn);
    }

    public OptionSource source() {
        return source;
    }

    /**
     * @return the text the option is set to, under its key or else under one of its deprecated keys
     */
    public Optional<String> text(Option<?> option) {
        return text(option.key(), option.deprecations());
    }

    private Optional<String> text(String key, List<DeprecatedKey> deprecations) {
        Optional<String> text = source.lookup(key);
        for (DeprecatedKey deprecated : deprecations) {
            Optional<String> old = source.lookup(deprecated.key());
            if (old.isEmpty()) {
                continue;
            }
            if (text.isPresent()) {
                if (!old.equals(text)) {
                    reportOnce(deprecated.key(), conflictMessage(deprecated.key(), key));
                }
                continue;
            }
            reportOnce(deprecated.key(), deprecationMessage(deprecated.key(), key, deprecated.dropIn()));
            return old;
        }
        return text;
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
        Optional<T> value = resolveTask(option, id);
        if (value.isEmpty()) {
            value = option.globalFallbackKey().flatMap(key -> {
                List<DeprecatedKey> deprecated = option.deprecations().stream().map(d -> new DeprecatedKey("tornado." + d.key(), d.dropIn())).toList();
                return text(key, deprecated).flatMap(text -> parse(key, option.parser(), text));
            });
        }
        return value;
    }

    public boolean isSetFor(TaskOption<?> option, String id) {
        return resolveTask(option, id).isPresent();
    }

    private <T> Optional<T> resolveTask(TaskOption<T> option, String id) {
        String key = option.taskKey(id);
        List<DeprecatedKey> deprecated = option.deprecations().stream().map(d -> new DeprecatedKey(id + "." + d.key(), d.dropIn())).toList();
        return text(key, deprecated).flatMap(text -> parse(key, option.parser(), text));
    }

    /**
     * @throws InvalidOptionValueException
     *     if the text is not a value of the parser's type
     */
    public static <T> Optional<T> parse(String key, OptionParser<T> parser, String text) {
        if (text.isBlank() && !parser.acceptsBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.parse(text));
        } catch (IllegalArgumentException e) {
            throw new InvalidOptionValueException(key, text, parser.typeName(), e);
        }
    }

    /**
     * Shows a warning about a key unless one was shown for it already, by this resolver or by the start-up check.
     *
     * @return whether the warning was shown
     */
    public boolean reportOnce(String key, String message) {
        if (reported.add(key)) {
            warnings.accept(message);
            return true;
        }
        return false;
    }

    static String deprecationMessage(String deprecatedKey, String key, String dropIn) {
        return String.format("-D%s is deprecated and will stop working in TornadoVM %s; use -D%s instead.", deprecatedKey, dropIn, key);
    }

    static String conflictMessage(String deprecatedKey, String key) {
        return String.format("-D%s and its former name -D%s are both set, to different values; -D%s is used.", key, deprecatedKey, key);
    }

    /**
     * Prints a warning on standard error, the way every TornadoVM option warning is reported.
     */
    public static void warn(String message) {
        System.err.println(WARNING_PREFIX + message);
    }
}
