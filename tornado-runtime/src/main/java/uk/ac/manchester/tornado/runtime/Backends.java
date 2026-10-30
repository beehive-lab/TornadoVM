/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;

/**
 * The backends TornadoVM can use: the installed providers that {@code -Dtornado.enabled.backends}
 * selects (all of them by default), in priority order, each created the first time something needs
 * it. A backend's index is fixed before any is created, so {@code -Dtornado.device=1:0} and
 * {@code TornadoExecutionPlan.getDevice(1, 0)} mean the same device whether or not the backends
 * before it exist yet, and a program that uses one backend does not pay for initialising the
 * others (OpenCL's platforms and contexts for a CUDA program, the CUDA backend for an OpenCL one).
 *
 * <p>Creating a backend is synchronised per backend. A backend that fails to be created fails the
 * same way for every later request, without trying again; until then, the other backends are
 * unaffected.</p>
 */
final class Backends {

    private final Slot[] slots;
    private final Function<TornadoBackendProvider, TornadoAcceleratorBackend> factory;

    /**
     * @param installed
     *     the installed providers, in any order
     * @param enabled
     *     a comma-separated list of backend names ({@code opencl}, {@code cuda}, ...), or blank for
     *     all of them
     * @param factory
     *     how a provider's backend is created
     */
    Backends(List<TornadoBackendProvider> installed, String enabled, Function<TornadoBackendProvider, TornadoAcceleratorBackend> factory) {
        this.factory = factory;
        List<TornadoBackendProvider> ordered = installed.stream().sorted().toList();
        Set<String> names = enabled == null || enabled.isBlank() ? null : Arrays.stream(enabled.split(",")).map(n -> n.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        slots = ordered.stream().filter(provider -> names == null || names.contains(name(provider))).map(Slot::new).toArray(Slot[]::new);
        if (slots.length == 0 && !ordered.isEmpty()) {
            throw new TornadoBackendNotFound("tornado.enabled.backends=" + enabled + " selects none of the installed backends: " + ordered.stream().map(Backends::name).toList());
        }
    }

    private static String name(TornadoBackendProvider provider) {
        return provider.getDevicePriority().name().toLowerCase(Locale.ROOT);
    }

    /** How many backends there are, created or not. */
    int count() {
        return slots.length;
    }

    /** The backend at {@code index}, created now if it does not exist yet. */
    TornadoAcceleratorBackend get(int index) {
        if (index < 0 || index >= slots.length) {
            throw new TornadoBackendNotFound("TornadoVM has no backend " + index + " (backends: " + slots.length + ")");
        }
        return slots[index].backend(factory);
    }

    /**
     * The index of the backend of class {@code type}, or -1. A backend's class is asked for by its
     * own devices and contexts, which exist only once it is created: the created backends are looked
     * at first, and the others are created only when none of them matches. A backend that cannot be
     * created is not the one asked for.
     */
    int indexOf(Class<?> type) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].created != null && slots[i].created.getClass() == type) {
                return i;
            }
        }
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].created == null && slots[i].failure == null) {
                try {
                    if (get(i).getClass() == type) {
                        return i;
                    }
                } catch (RuntimeException e) {
                    // reported to whoever asks for this backend by its index
                }
            }
        }
        return -1;
    }

    /**
     * Makes the backend at {@code index} backend 0, exchanging the two. Meant for start-up (selecting
     * the device of a test run, say): it does not coordinate with threads using the backends.
     */
    synchronized void makeDefault(int index) {
        get(index); // a backend that does not exist cannot become the default
        Slot first = slots[0];
        slots[0] = slots[index];
        slots[index] = first;
    }

    /** One backend: its provider, and the backend once created (or why it could not be). */
    private static final class Slot {
        private final TornadoBackendProvider provider;
        private volatile TornadoAcceleratorBackend created;
        private volatile RuntimeException failure;

        Slot(TornadoBackendProvider provider) {
            this.provider = provider;
        }

        TornadoAcceleratorBackend backend(Function<TornadoBackendProvider, TornadoAcceleratorBackend> factory) {
            TornadoAcceleratorBackend backend = created;
            if (backend != null) {
                return backend;
            }
            synchronized (this) {
                if (created == null && failure == null) {
                    try {
                        backend = factory.apply(provider);
                        if (backend == null) {
                            throw new TornadoBackendNotFound(provider.getName() + " did not create a backend");
                        }
                        created = backend;
                    } catch (RuntimeException e) {
                        failure = e;
                    }
                }
                if (failure != null) {
                    throw failure;
                }
                return created;
            }
        }
    }
}
