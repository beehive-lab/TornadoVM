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

import java.util.Optional;
import java.util.Set;

/**
 * Where option text comes from: JVM system properties, the {@code etc/tornado.properties} file, or a map in a
 * test.
 */
public interface OptionSource {

    /**
     * @return the text configured for the key, if any
     */
    Optional<String> lookup(String key);

    /**
     * @return every key this source configures, used to report unknown and removed options
     */
    Set<String> keys();
}
