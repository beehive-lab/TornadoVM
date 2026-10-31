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

import java.util.function.Function;

/**
 * Turns the text of an option into its typed value.
 *
 * @param <T>
 *     the type of the value
 */
public interface OptionParser<T> {

    /**
     * @param text
     *     the configured text; blank only if {@link #acceptsBlank()}
     * @return the parsed value
     * @throws IllegalArgumentException
     *     if the text is not a valid value; {@link OptionResolver} reports it against the option's key
     */
    T parse(String text);

    /**
     * @return the type shown in the documentation, for example {@code boolean} or {@code size}
     */
    String typeName();

    /**
     * @return whether blank text is a value of its own. When it is not, blank text (as left by a bare
     *     {@code -Dkey}) means the option is unset and its default applies.
     */
    boolean acceptsBlank();

    static <T> OptionParser<T> of(String typeName, Function<String, T> parse) {
        return of(typeName, parse, false);
    }

    static <T> OptionParser<T> of(String typeName, Function<String, T> parse, boolean acceptsBlank) {
        return new OptionParser<>() {
            @Override
            public T parse(String text) {
                return parse.apply(text);
            }

            @Override
            public String typeName() {
                return typeName;
            }

            @Override
            public boolean acceptsBlank() {
                return acceptsBlank;
            }
        };
    }
}
