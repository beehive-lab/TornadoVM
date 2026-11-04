/*
 * Copyright (c) 2013-2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.api.runtime;

import java.util.Objects;

import uk.ac.manchester.tornado.api.TornadoRuntime;
import uk.ac.manchester.tornado.api.TornadoSetting;
import uk.ac.manchester.tornado.api.internal.options.OptionResolver;
import uk.ac.manchester.tornado.api.options.Option;
import uk.ac.manchester.tornado.api.options.TaskOption;

public class TornadoRuntimeProvider {

    private static TornadoRuntime runtimeImpl;
    private static TornadoSetting tornadoImpl;

    static {
        init();
    }

    private static void init() {
        if (runtimeImpl == null) {
            runtimeImpl = TornadoAPIProvider.loadTornadoRuntimeImpl();
        }
        if (tornadoImpl == null) {
            tornadoImpl = TornadoAPIProvider.loadTornadoImpl();
        }
    }

    public static TornadoRuntime getTornadoRuntime() {
        return runtimeImpl;
    }

    public static boolean isProfilerEnabled() {
        return runtimeImpl.isProfilerEnabled();
    }

    /**
     * Sets a property as {@code -Dkey=value} would. For a TornadoVM option, prefer the typed
     * {@link #setProperty(Option, Object)}. A static option that TornadoVM has already read keeps the value it read,
     * and setting it is reported.
     */
    public static void setProperty(String key, String value) {
        tornadoImpl.setTornadoProperty(key, value);
    }

    /**
     * Sets a TornadoVM option for the rest of the run. The value is checked against the option's type now. A
     * {@linkplain Option#isDynamic() static} option that TornadoVM has already read keeps the value it read, and
     * setting it is reported: set such options on the command line, or before TornadoVM first uses them.
     *
     * @throws uk.ac.manchester.tornado.api.options.InvalidOptionValueException
     *     if the option does not accept the value
     */
    public static <T> void setProperty(Option<T> option, T value) {
        String text = String.valueOf(Objects.requireNonNull(value));
        OptionResolver.parse(option.key(), option.parser(), text);
        setProperty(option.key(), text);
    }

    /**
     * Sets a per-task option, {@code <id>.<suffix>}, for the rest of the run.
     *
     * @param id
     *     the task graph ({@code s0}) or task ({@code s0.t0})
     * @throws uk.ac.manchester.tornado.api.options.InvalidOptionValueException
     *     if the option does not accept the value
     */
    public static <T> void setProperty(TaskOption<T> option, String id, T value) {
        String key = option.taskKey(id);
        String text = String.valueOf(Objects.requireNonNull(value));
        OptionResolver.parse(key, option.parser(), text);
        setProperty(key, text);
    }

    public static String getProperty(String key, String value) {
        return tornadoImpl.getTornadoProperty(key, value);
    }

    public static String getProperty(String key) {
        return tornadoImpl.getTornadoProperty(key);
    }

    public static void loadSettings(String property) {
        tornadoImpl.loadTornadoProperty(property);
    }

}
