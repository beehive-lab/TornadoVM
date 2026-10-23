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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import uk.ac.manchester.tornado.api.TornadoSetting;

public final class Tornado implements TornadoSetting {

    private static final String TORNADOVM_HOME_VARIABLE = "TORNADOVM_HOME";

    private static final Properties settings = System.getProperties();

    static {
        tryLoadSettings();
    }

    private static void setProperty(String key, String value) {
        settings.setProperty(key, value);
    }

    public static String getProperty(String key) {
        return settings.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        return settings.getProperty(key, defaultValue);
    }

    private static void loadSettings(String filename) {
        final File localSettings = new File(filename);
        Properties loadProperties = new Properties();
        if (localSettings.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(localSettings)) {
                loadProperties.load(fileInputStream);
            } catch (IOException e) {
                new TornadoLogger().warn("Unable to load settings from %s", localSettings.getAbsolutePath());
            }
        }
        Set<String> localKeys = loadProperties.stringPropertyNames();
        Set<String> systemKeys = settings.stringPropertyNames();
        Set<String> diff = new HashSet<>(localKeys);
        diff.removeAll(systemKeys);
        diff.forEach(key -> settings.setProperty(key, loadProperties.getProperty(key)));
    }

    private static void tryLoadSettings() {
        final String tornadoRoot = System.getenv(TORNADOVM_HOME_VARIABLE);
        loadSettings(tornadoRoot + "/etc/tornado.properties");
    }

    @Override
    public void setTornadoProperty(String key, String value) {
        Tornado.setProperty(key, value);
    }

    @Override
    public String getTornadoProperty(String key) {
        return Tornado.getProperty(key);
    }

    @Override
    public String getTornadoProperty(String key, String defaultValue) {
        return Tornado.getProperty(key, defaultValue);
    }

    @Override
    public void loadTornadoProperty(String filename) {
        loadSettings(filename);
    }

}
