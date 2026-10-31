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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Options from a map, standing in for the command line in tests.
 */
record MapSource(Map<String, String> values) implements OptionSource {

    static MapSource of(String... keysAndValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return new MapSource(Map.copyOf(map));
    }

    @Override
    public Optional<String> lookup(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public Set<String> keys() {
        return values.keySet();
    }
}
