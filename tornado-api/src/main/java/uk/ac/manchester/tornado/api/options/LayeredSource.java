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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Sources in precedence order: the first one that configures a key wins.
 */
public final class LayeredSource implements OptionSource {

    private final List<OptionSource> layers;

    public LayeredSource(List<OptionSource> layers) {
        this.layers = List.copyOf(layers);
    }

    @Override
    public Optional<String> lookup(String key) {
        for (OptionSource layer : layers) {
            Optional<String> value = layer.lookup(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    @Override
    public Set<String> keys() {
        Set<String> keys = new TreeSet<>();
        layers.forEach(layer -> keys.addAll(layer.keys()));
        return keys;
    }
}
