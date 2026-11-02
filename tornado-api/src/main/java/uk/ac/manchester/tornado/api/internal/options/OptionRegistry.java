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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import uk.ac.manchester.tornado.api.options.CudaFlags;
import uk.ac.manchester.tornado.api.options.DebugFlags;
import uk.ac.manchester.tornado.api.options.DeprecatedKey;
import uk.ac.manchester.tornado.api.options.InvalidOptionValueException;
import uk.ac.manchester.tornado.api.options.LibraryFlags;
import uk.ac.manchester.tornado.api.options.MetalFlags;
import uk.ac.manchester.tornado.api.options.OpenCLFlags;
import uk.ac.manchester.tornado.api.options.Option;
import uk.ac.manchester.tornado.api.options.ProfilerFlags;
import uk.ac.manchester.tornado.api.options.RuntimeFlags;
import uk.ac.manchester.tornado.api.options.TaskFlags;
import uk.ac.manchester.tornado.api.options.TaskOption;
import uk.ac.manchester.tornado.api.options.TcFlags;

/**
 * Every option TornadoVM has, collected from the catalog classes, and every option it no longer has.
 */
public final class OptionRegistry {

    private static final List<Class<?>> CATALOGS = List.of(RuntimeFlags.class, //
            DebugFlags.class, //
            ProfilerFlags.class, //
            TaskFlags.class, //
            OpenCLFlags.class, //
            CudaFlags.class, //
            MetalFlags.class, //
            TcFlags.class, //
            LibraryFlags.class);

    private static final String TORNADO_PREFIX = "tornado.";

    /**
     * Keys owned by the test harnesses ({@code tornado-test}, the unit tests) and by the benchmark suite
     * (tornadovm-benchmark-suite, {@code -Dtornado.benchmarks.skipserial}), not by TornadoVM.
     */
    private static final List<String> HARNESS_PREFIXES = List.of("tornado.unittests.", "tornado.test.", "tornado.benchmarks.");

    private static final int MAX_SUGGESTION_DISTANCE = 3;

    private final List<Option<?>> options;
    private final List<TaskOption<?>> taskOptions;
    private final Map<String, Option<?>> optionsByKey = new HashMap<>();
    /** Every key an option answers to, deprecated ones included, and the option's own key. */
    private final Map<String, String> knownKeys = new HashMap<>();
    /** Deprecated key and its deprecation. */
    private final Map<String, DeprecatedKey> deprecatedByKey = new HashMap<>();
    /** Every suffix a task option answers to, deprecated ones included, and the option's own suffix. */
    private final Map<String, String> knownSuffixes = new HashMap<>();
    private final Map<String, TaskOption<?>> taskOptionsBySuffix = new HashMap<>();
    private final Map<String, RemovedOption> removedByKey = new HashMap<>();

    OptionRegistry(List<Option<?>> options, List<TaskOption<?>> taskOptions, List<RemovedOption> removed) {
        this.options = List.copyOf(options);
        this.taskOptions = List.copyOf(taskOptions);
        for (Option<?> option : options) {
            register(option.key(), option.key());
            optionsByKey.put(option.key(), option);
            for (DeprecatedKey deprecated : option.deprecations()) {
                register(deprecated.key(), option.key());
                optionsByKey.put(deprecated.key(), option);
                deprecatedByKey.put(deprecated.key(), deprecated);
            }
        }
        for (TaskOption<?> taskOption : taskOptions) {
            for (String suffix : taskOption.suffixes()) {
                if (knownSuffixes.put(suffix, taskOption.suffix()) != null) {
                    throw new IllegalStateException("Task option suffix " + suffix + " is declared twice");
                }
                taskOptionsBySuffix.put(suffix, taskOption);
            }
            taskOption.globalFallbackKey().ifPresent(key -> register(key, key));
            taskOption.deprecations().forEach(deprecated -> taskOption.globalFallbackKey().ifPresent(key -> {
                register(TORNADO_PREFIX + deprecated.key(), key);
                deprecatedByKey.put(TORNADO_PREFIX + deprecated.key(), deprecated);
            }));
        }
        for (RemovedOption removedOption : removed) {
            if (knownKeys.containsKey(removedOption.key())) {
                throw new IllegalStateException("Option " + removedOption.key() + " is both declared and removed");
            }
            removedByKey.put(removedOption.key(), removedOption);
        }
    }

    /**
     * @return the registry of every catalog class
     */
    public static OptionRegistry catalog() {
        return Catalog.INSTANCE;
    }

    public List<Option<?>> options() {
        return options;
    }

    public List<TaskOption<?>> taskOptions() {
        return taskOptions;
    }

    public List<RemovedOption> removed() {
        return removedByKey.values().stream().sorted(Comparator.comparing(RemovedOption::key)).toList();
    }

    /**
     * @return the option with this key, or with this key as a deprecated one
     */
    public Optional<Option<?>> find(String key) {
        return Optional.ofNullable(optionsByKey.get(key));
    }

    /**
     * @return the task option with this suffix, or with this suffix as a deprecated one
     */
    public Optional<TaskOption<?>> findTaskOption(String suffix) {
        return Optional.ofNullable(taskOptionsBySuffix.get(suffix));
    }

    /**
     * @param source
     *     the configured options, typically {@code OptionResolver.global().source()}
     * @param allowed
     *     keys the application owns, from {@code tornado.options.allow}; one ending in {@code .} allows every key
     *     it starts
     * @return one diagnostic per {@code tornado.*} key that is removed, deprecated, set together with its former
     *     name, or that no option has, sorted by key. Per-task keys ({@code s0.t0.device}) cannot be told apart
     *     from the application's own properties here; {@link #diagnoseTaskGraph} checks them once the task graph
     *     is known.
     */
    public List<OptionDiagnostic> diagnose(OptionSource source, Collection<String> allowed) {
        List<OptionDiagnostic> diagnostics = new ArrayList<>();
        source.keys().stream().sorted().filter(this::isTornadoKey).filter(key -> !isAllowed(key, allowed)).forEach(key -> {
            RemovedOption removedOption = removedByKey.get(key);
            DeprecatedKey deprecated = deprecatedByKey.get(key);
            if (removedOption != null) {
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.REMOVED, String.format("-D%s was removed and is ignored. %s", key, removedOption.reason())));
            } else if (deprecated != null) {
                String current = knownKeys.get(key);
                Optional<String> currentText = source.lookup(current);
                if (currentText.isEmpty()) {
                    diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.DEPRECATED, OptionResolver.deprecationMessage(key, current, deprecated.dropIn())));
                } else if (!currentText.equals(source.lookup(key))) {
                    diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.CONFLICT, OptionResolver.conflictMessage(key, current)));
                }
            } else if (!knownKeys.containsKey(key)) {
                String suggestion = suggest(key, knownKeys).map(known -> " Did you mean -D" + known + "?").orElse("");
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.UNKNOWN, String.format("-D%s is not a TornadoVM option and is ignored.%s"
                        + " If the application reads it, add it to -D%s.", key, suggestion, RuntimeFlags.OPTIONS_ALLOW.key())));
            }
        });
        return diagnostics;
    }

    /**
     * Checks a key set while the program runs, with
     * {@link uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider#setProperty}.
     *
     * @return a warning when setting it has no effect: the option is removed, or it is a static option that was
     *     already read as another value
     * @throws InvalidOptionValueException
     *     if the key is an option's and the option does not accept the value
     */
    public Optional<String> checkSetAtRunTime(String key, String text) {
        RemovedOption removedOption = removedByKey.get(key);
        if (removedOption != null) {
            return Optional.of(String.format("%s was removed and is ignored. %s", key, removedOption.reason()));
        }
        Option<?> option = optionsByKey.get(key);
        if (option == null) {
            return Optional.empty();
        }
        Optional<?> value = OptionResolver.parse(key, option.parser(), text);
        Optional<?> read = option.resolvedValue();
        if (read.isPresent() && value.isPresent() && !read.equals(value)) {
            return Optional.of(String.format("%s was set to %s while the program runs, but TornadoVM already read it as %s and keeps that value. "
                    + "Set it on the command line, or before TornadoVM first uses it.", key, text, read.get()));
        }
        return Optional.empty();
    }

    /**
     * Parses every option that is set, so that a bad value is reported when TornadoVM starts rather than when the
     * option is first read, which may be inside a compilation.
     *
     * @return one error per option set to a value its type does not accept
     */
    public List<InvalidOptionValueException> validate(OptionSource source) {
        OptionResolver quiet = new OptionResolver(source, message -> {
        });
        List<InvalidOptionValueException> errors = new ArrayList<>();
        for (Option<?> option : options) {
            try {
                quiet.find(option);
            } catch (InvalidOptionValueException e) {
                errors.add(e);
            }
        }
        for (TaskOption<?> taskOption : taskOptions) {
            try {
                // Any id: with no per-task key set, only the global fallback is parsed.
                quiet.lookup(taskOption, "\0");
            } catch (InvalidOptionValueException e) {
                errors.add(e);
            }
        }
        return errors;
    }

    /**
     * Checks the keys set for a task graph, {@code <graph>.<suffix>} and {@code <graph>.<task>.<suffix>}.
     *
     * <p>
     * A key naming a task of the graph must end in a task option's suffix. A key whose suffix is a task option's
     * but whose task the graph does not have is reported too. Other keys under the graph's name are reported only
     * when they are close to a task option's suffix, since the application may have properties of its own that
     * start with the graph's name.
     *
     * @return the warnings, sorted by key
     * @throws InvalidOptionValueException
     *     if a key of the graph is set to a value its option does not accept
     */
    public List<OptionDiagnostic> diagnoseTaskGraph(OptionSource source, String graph, Collection<String> tasks) {
        String prefix = graph + ".";
        List<OptionDiagnostic> diagnostics = new ArrayList<>();
        for (String key : source.keys().stream().filter(k -> k.startsWith(prefix)).sorted().toList()) {
            String rest = key.substring(prefix.length());
            Optional<String> task = tasks.stream().filter(name -> rest.startsWith(name + ".")).max(Comparator.comparingInt(String::length));
            String suffix = task.map(name -> rest.substring(name.length() + 1)).orElse(rest);
            TaskOption<?> option = taskOptionsBySuffix.get(suffix);
            if (option != null) {
                source.lookup(key).ifPresent(text -> OptionResolver.parse(key, option.parser(), text));
                continue;
            }
            Optional<String> suggestion = suggest(suffix, knownSuffixes);
            Optional<String> knownTail = knownSuffixes.keySet().stream().filter(known -> rest.endsWith("." + known)).findFirst();
            if (task.isPresent()) {
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.UNKNOWN, String.format("-D%s: %s is not a per-task option and is ignored.%s", key, suffix,
                        suggestion.map(known -> " Did you mean -D" + prefix + task.get() + "." + known + "?").orElse(""))));
            } else if (knownTail.isPresent()) {
                String missing = rest.substring(0, rest.length() - knownTail.get().length() - 1);
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.UNKNOWN, String.format("-D%s is ignored: task graph %s has no task %s (tasks: %s).", key, graph, missing,
                        String.join(", ", tasks))));
            } else if (suggestion.isPresent()) {
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.UNKNOWN, String.format("-D%s is not a per-task option and is ignored. Did you mean -D%s%s?", key, prefix,
                        suggestion.get())));
            }
        }
        return diagnostics;
    }

    private boolean isTornadoKey(String key) {
        return key.startsWith(TORNADO_PREFIX) && HARNESS_PREFIXES.stream().noneMatch(key::startsWith);
    }

    private static boolean isAllowed(String key, Collection<String> allowed) {
        return allowed.stream().anyMatch(entry -> entry.endsWith(".") ? key.startsWith(entry) : key.equals(entry));
    }

    /**
     * @return the canonical name of the known key closest to {@code key}, if one is close
     */
    private static Optional<String> suggest(String key, Map<String, String> known) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = MAX_SUGGESTION_DISTANCE + 1;
        for (Map.Entry<String, String> entry : new TreeMap<>(known).entrySet()) {
            int distance = editDistance(lowerKey, entry.getKey().toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getValue();
            }
        }
        return Optional.ofNullable(best);
    }

    private void register(String key, String canonicalKey) {
        if (knownKeys.put(key, canonicalKey) != null) {
            throw new IllegalStateException("Option key " + key + " is declared twice");
        }
    }

    /**
     * Levenshtein distance: the fewest single-character insertions, deletions and substitutions turning one text
     * into the other.
     */
    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static final class Catalog {

        private static final OptionRegistry INSTANCE = load();

        private static OptionRegistry load() {
            List<Option<?>> options = new ArrayList<>();
            List<TaskOption<?>> taskOptions = new ArrayList<>();
            for (Class<?> catalog : CATALOGS) {
                for (Field field : catalog.getDeclaredFields()) {
                    if (isPublicConstant(field)) {
                        Object value = readConstant(field);
                        if (value instanceof Option<?> option) {
                            options.add(option);
                        } else if (value instanceof TaskOption<?> taskOption) {
                            taskOptions.add(taskOption);
                        }
                    }
                }
            }
            return new OptionRegistry(options, taskOptions, RemovedFlags.ALL);
        }

        private static boolean isPublicConstant(Field field) {
            int modifiers = field.getModifiers();
            return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
        }

        private static Object readConstant(Field field) {
            try {
                return field.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read option " + field, e);
            }
        }
    }
}
