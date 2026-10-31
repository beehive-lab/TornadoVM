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

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.api.utils.TornadoAPIUtils;

/**
 * The parsers every option is built from.
 */
public final class OptionParsers {

    /**
     * {@code true} or {@code false}, in any letter case. Anything else is rejected instead of being read as
     * {@code false}, so {@code -Dtornado.debug=ture} fails loudly.
     */
    public static final OptionParser<Boolean> BOOLEAN = OptionParser.of("boolean", OptionParsers::parseBoolean);

    public static final OptionParser<Integer> INTEGER = OptionParser.of("int", text -> Integer.parseInt(text.trim()));

    public static final OptionParser<Long> LONG = OptionParser.of("long", text -> Long.parseLong(text.trim()));

    /**
     * Any text, kept as given. Blank text is a value: {@code -Dtornado.opencl.compiler.flags=} means no flags.
     */
    public static final OptionParser<String> STRING = OptionParser.of("string", text -> text, true);

    /**
     * A byte count with an optional binary unit: {@code 512}, {@code 64MB}, {@code 4GB}.
     */
    public static final OptionParser<Long> SIZE = OptionParser.of("size", text -> TornadoAPIUtils.parseSize(text.trim()));

    private OptionParsers() {
    }

    /**
     * One of the constants of an enum, by name in any letter case.
     */
    public static <E extends Enum<E>> OptionParser<E> enumOf(Class<E> type) {
        String choices = Arrays.stream(type.getEnumConstants()).map(constant -> constant.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("|"));
        return OptionParser.of(choices, text -> Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT)));
    }

    private static Boolean parseBoolean(String text) {
        String value = text.trim();
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("expected true or false");
    }
}
