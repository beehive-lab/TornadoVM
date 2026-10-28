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
package uk.ac.manchester.tornado.api;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import uk.ac.manchester.tornado.api.common.TaskPackage;

/** The installed {@link ObjectAdapter}s, applied where TornadoVM receives objects from a program. */
public final class ObjectAdapters {

    private static final List<ObjectAdapter> ADAPTERS = load();

    /**
     * The adapters that load. One that cannot (its library is not on the class path, say) is skipped
     * with a warning: it must not stop programs that do not use it.
     */
    private static List<ObjectAdapter> load() {
        var adapters = new ArrayList<ObjectAdapter>();
        var providers = ServiceLoader.load(ObjectAdapter.class).iterator();
        for (int attempts = 0; attempts < 1000; attempts++) {
            try {
                if (!providers.hasNext()) {
                    break;
                }
                adapters.add(providers.next());
            } catch (ServiceConfigurationError | LinkageError e) {
                System.err.println("[TornadoVM] an ObjectAdapter could not be loaded and is ignored: " + e.getMessage());
            }
        }
        return List.copyOf(adapters);
    }

    private ObjectAdapters() {
    }

    /** {@code object}'s TornadoVM stand-in, or {@code object} itself when no adapter handles it. */
    public static Object adapt(Object object) {
        if (object == null) {
            return null;
        }
        for (ObjectAdapter adapter : ADAPTERS) {
            Object adapted = adapter.adapt(object);
            if (adapted != null) {
                return adapted;
            }
        }
        return object;
    }

    /** The objects with their stand-ins; the same array when no adapter is installed. */
    public static Object[] adapt(Object... objects) {
        if (ADAPTERS.isEmpty() || objects == null) {
            return objects;
        }
        Object[] adapted = objects.clone();
        for (int i = 0; i < adapted.length; i++) {
            adapted[i] = adapt(adapted[i]);
        }
        return adapted;
    }

    /** Replaces the adapted arguments of a task in place, telling the adapters which parameters they became. */
    static void adaptTask(TaskPackage task) {
        if (ADAPTERS.isEmpty()) {
            return;
        }
        Object[] parameters = task.getTaskParameters();
        boolean lambda = task.getMethod() == null;
        Object kernel = lambda ? parameters[0] : task.getMethod();
        for (int i = lambda ? 1 : 0; i < parameters.length; i++) {
            Object original = parameters[i];
            if (original == null) {
                continue;
            }
            for (ObjectAdapter adapter : ADAPTERS) {
                Object adapted = adapter.adapt(original);
                if (adapted != null) {
                    adapter.bound(kernel, lambda ? i - 1 : i, original);
                    parameters[i] = adapted;
                    break;
                }
            }
        }
    }
}
