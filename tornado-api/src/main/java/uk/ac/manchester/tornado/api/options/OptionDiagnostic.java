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

/**
 * A problem with a configured {@code tornado.*} key, found by {@link OptionRegistry#diagnose}.
 *
 * @param key
 *     the configured key
 * @param kind
 *     what is wrong with it
 * @param message
 *     the warning to show
 */
public record OptionDiagnostic(String key, Kind kind, String message) {

    public enum Kind {
        /** No option has this key. */
        UNKNOWN,
        /** The option was removed. */
        REMOVED
    }
}
