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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks every option of the catalog on its own: it is well formed, its default applies when it is not set,
 * and setting it, under its key or a deprecated one, changes its value.
 */
public class EveryOptionTest {

    /** Lower case words separated by dots: no camelCase, no hyphens. */
    private static final Pattern KEY_FORMAT = Pattern.compile("tornado(\\.[a-z0-9]+)+");

    private static final Pattern SUFFIX_FORMAT = Pattern.compile("[a-z0-9]+(\\.[a-z0-9]+)*");

    static Stream<Option<?>> options() {
        return OptionRegistry.catalog().options().stream();
    }

    static Stream<TaskOption<?>> taskOptions() {
        return OptionRegistry.catalog().taskOptions().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("options")
    public void isWellFormed(Option<?> option) {
        assertTrue(KEY_FORMAT.matcher(option.key()).matches(), option.key() + " does not follow tornado.<lower.case.words>");
        assertTrue(option.description().endsWith("."), option.key() + ": the description is a sentence");
        assertFalse(option.defaultDescription().isEmpty() && !option.parser().acceptsBlank(), option.key());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("options")
    public void takesItsDefaultWhenUnset(Option<?> option) {
        assertDefaultWhenUnset(option);
    }

    private static <T> void assertDefaultWhenUnset(Option<T> option) {
        OptionResolver resolver = new OptionResolver(MapSource.of(), message -> {
        });
        assertEquals(Optional.empty(), resolver.find(option));
        option.defaultValue().ifPresent(defaultValue -> assertEquals(defaultValue, resolver.resolve(option)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("options")
    public void honoursItsKeyAndItsDeprecatedKeys(Option<?> option) {
        String text = sampleText(option);
        Object expected = option.parser().parse(text);
        assertNotEquals(option.defaultValue().orElse(null), expected, option.key() + ": the sample must differ from the default");
        List<String> keys = new ArrayList<>(option.deprecatedKeys());
        keys.add(0, option.key());
        for (String key : keys) {
            List<String> warnings = new ArrayList<>();
            OptionResolver resolver = new OptionResolver(MapSource.of(key, text), warnings::add);
            assertEquals(expected, resolver.resolve(option), key);
            assertEquals(key.equals(option.key()) ? 0 : 1, warnings.size(), key + ": " + warnings);
        }
    }

    @ParameterizedTest(name = "<id>.{0}")
    @MethodSource("taskOptions")
    public void taskOptionIsWellFormedAndHonoursItsKeys(TaskOption<?> option) {
        assertTrue(SUFFIX_FORMAT.matcher(option.suffix()).matches(), option.suffix());
        assertTrue(option.description().endsWith("."), option.suffix());
        String text = option.parser().typeName().equals("int") ? "12" : "1:0";
        OptionResolver resolver = new OptionResolver(MapSource.of("s0.t0." + option.suffix(), text), message -> {
        });
        assertEquals(option.parser().parse(text), resolver.lookup(option, "s0.t0").orElseThrow());
        assertTrue(resolver.isSetFor(option, "s0.t0"));
        assertFalse(resolver.isSetFor(option, "s0.t1"));
        option.globalFallbackKey().ifPresent(key -> {
            OptionResolver global = new OptionResolver(MapSource.of(key, text), message -> {
            });
            assertEquals(option.parser().parse(text), global.lookup(option, "s0.t1").orElseThrow(), key);
        });
    }

    /**
     * Text that is a valid value of the option and differs from its default.
     */
    private static String sampleText(Option<?> option) {
        Object defaultValue = option.defaultValue().orElse(null);
        return switch (option.parser().typeName()) {
            case "boolean" -> Boolean.toString(!Boolean.TRUE.equals(defaultValue));
            case "int", "long" -> "12345";
            case "size" -> "64MB";
            default -> "sample-value";
        };
    }
}
