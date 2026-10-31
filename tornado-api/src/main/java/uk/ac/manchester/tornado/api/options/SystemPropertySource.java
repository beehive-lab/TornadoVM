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
 * Options given on the command line with {@code -D}, or set at run time with
 * {@link uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider#setProperty}. Read live, so a value set at run
 * time is seen by the next resolution of a dynamic option.
 */
public final class SystemPropertySource implements OptionSource {

    @Override
    public Optional<String> lookup(String key) {
        return Optional.ofNullable(System.getProperty(key));
    }

    @Override
    public Set<String> keys() {
        return System.getProperties().stringPropertyNames();
    }
}
