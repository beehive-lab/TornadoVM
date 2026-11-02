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

/**
 * A former key of an option, still honoured with a warning until the release {@code dropIn}.
 *
 * @param key
 *     the former key
 * @param dropIn
 *     the TornadoVM version, {@code major.minor.patch}, that stops honouring it. A unit test fails once the
 *     project version reaches it, so the key is not kept by accident.
 */
public record DeprecatedKey(String key, String dropIn) {

    public DeprecatedKey {
        Objects.requireNonNull(key);
        Objects.requireNonNull(dropIn);
    }
}
