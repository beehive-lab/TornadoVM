/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.common;

import java.nio.file.Path;

import uk.ac.manchester.tornado.api.TornadoSetting;
import uk.ac.manchester.tornado.api.options.OptionResolver;
import uk.ac.manchester.tornado.api.options.PropertiesFileSource;

/**
 * Access to TornadoVM's configuration through {@link uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider}. Reads
 * the way every option is read, through {@link OptionResolver#global()}: {@code -D} system properties first, then
 * {@code $TORNADOVM_HOME/etc/tornado.properties}.
 */
public final class Tornado implements TornadoSetting {

    @Override
    public void setTornadoProperty(String key, String value) {
        System.setProperty(key, value);
    }

    @Override
    public String getTornadoProperty(String key) {
        return OptionResolver.global().source().lookup(key).orElse(null);
    }

    @Override
    public String getTornadoProperty(String key, String defaultValue) {
        return OptionResolver.global().source().lookup(key).orElse(defaultValue);
    }

    /**
     * Adds the properties of a file that are not already set, as if they had been given with {@code -D}.
     */
    @Override
    public void loadTornadoProperty(String filename) {
        PropertiesFileSource file = PropertiesFileSource.load(Path.of(filename), OptionResolver::warn);
        for (String key : file.keys()) {
            if (System.getProperty(key) == null) {
                file.lookup(key).ifPresent(value -> System.setProperty(key, value));
            }
        }
    }
}
