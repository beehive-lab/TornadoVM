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

/**
 * Every option TornadoVM has, collected from the catalog classes, and every option it no longer has.
 */
public final class OptionRegistry {

    private static final List<Class<?>> CATALOGS = List.of( //
            RuntimeFlags.class, //
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
     * Keys owned by the test harnesses ({@code tornado-test}, the unit tests), not by TornadoVM.
     */
    private static final List<String> HARNESS_PREFIXES = List.of("tornado.unittests.", "tornado.test.");

    private static final int MAX_SUGGESTION_DISTANCE = 3;

    private final List<Option<?>> options;
    private final List<TaskOption<?>> taskOptions;
    private final Map<String, Option<?>> optionsByKey = new HashMap<>();
    private final Map<String, String> knownKeys = new HashMap<>();
    private final Map<String, RemovedOption> removedByKey = new HashMap<>();

    OptionRegistry(List<Option<?>> options, List<TaskOption<?>> taskOptions, List<RemovedOption> removed) {
        this.options = List.copyOf(options);
        this.taskOptions = List.copyOf(taskOptions);
        for (Option<?> option : options) {
            register(option.key(), option.key());
            optionsByKey.put(option.key(), option);
            option.deprecatedKeys().forEach(deprecatedKey -> register(deprecatedKey, option.key()));
        }
        for (TaskOption<?> taskOption : taskOptions) {
            taskOption.globalFallbackKey().ifPresent(key -> register(key, key));
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

    public Optional<Option<?>> find(String key) {
        return Optional.ofNullable(optionsByKey.get(key));
    }

    /**
     * @param configuredKeys
     *     the keys configured, typically {@code OptionResolver.global().source().keys()}
     * @return one diagnostic per {@code tornado.*} key that is removed or that no option has, sorted by key.
     *     Per-task keys ({@code s0.t0.device}) cannot be told apart from the application's own properties and
     *     are not checked.
     */
    public List<OptionDiagnostic> diagnose(Collection<String> configuredKeys) {
        List<OptionDiagnostic> diagnostics = new ArrayList<>();
        configuredKeys.stream().sorted().filter(this::isTornadoKey).forEach(key -> {
            RemovedOption removedOption = removedByKey.get(key);
            if (removedOption != null) {
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.REMOVED, String.format("-D%s was removed and is ignored. %s", key, removedOption.reason())));
            } else if (!knownKeys.containsKey(key)) {
                String suggestion = suggest(key).map(known -> " Did you mean -D" + known + "?").orElse("");
                diagnostics.add(new OptionDiagnostic(key, OptionDiagnostic.Kind.UNKNOWN, String.format("-D%s is not a TornadoVM option and is ignored.%s", key, suggestion)));
            }
        });
        return diagnostics;
    }

    private boolean isTornadoKey(String key) {
        return key.startsWith(TORNADO_PREFIX) && HARNESS_PREFIXES.stream().noneMatch(key::startsWith);
    }

    private Optional<String> suggest(String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = MAX_SUGGESTION_DISTANCE + 1;
        for (Map.Entry<String, String> known : new TreeMap<>(knownKeys).entrySet()) {
            int distance = editDistance(lowerKey, known.getKey().toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = known.getValue();
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
