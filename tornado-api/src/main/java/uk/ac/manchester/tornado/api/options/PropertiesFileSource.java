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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Options from a properties file, such as {@code $TORNADOVM_HOME/etc/tornado.properties}. A missing file
 * configures nothing.
 */
public final class PropertiesFileSource implements OptionSource {

    private final Properties properties;

    private PropertiesFileSource(Properties properties) {
        this.properties = properties;
    }

    /**
     * @param file
     *     the properties file; may not exist
     * @param warnings
     *     told when the file exists but cannot be read, in which case it configures nothing
     */
    public static PropertiesFileSource load(Path file, Consumer<String> warnings) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                warnings.accept("Unable to load TornadoVM options from " + file + ": " + e.getMessage());
                properties.clear();
            }
        }
        return new PropertiesFileSource(properties);
    }

    @Override
    public Optional<String> lookup(String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }

    @Override
    public Set<String> keys() {
        return properties.stringPropertyNames();
    }
}
