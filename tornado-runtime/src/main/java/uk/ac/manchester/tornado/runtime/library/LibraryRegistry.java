/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.library;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.library.spi.LibraryContext;
import uk.ac.manchester.tornado.runtime.library.spi.TornadoLibraryProvider;

/**
 * Discovers {@link TornadoLibraryProvider} implementations via
 * {@link ServiceLoader} and caches their native {@link LibraryContext}s per
 * (library, device, execution plan). Library binding modules register
 * themselves with {@code provides TornadoLibraryProvider with ...} in their
 * module descriptor; the core runtime needs no per-library changes.
 */
public final class LibraryRegistry {

    private static volatile List<TornadoLibraryProvider> providers;

    private record ContextKey(String libraryName, TornadoXPUDevice device, long executionPlanId) {
    }

    private static final Map<ContextKey, LibraryContext> CONTEXTS = new ConcurrentHashMap<>();

    private LibraryRegistry() {
    }

    private static List<TornadoLibraryProvider> getProviders() {
        if (providers == null) {
            synchronized (LibraryRegistry.class) {
                if (providers == null) {
                    List<TornadoLibraryProvider> discovered = new ArrayList<>();
                    for (TornadoLibraryProvider provider : ServiceLoader.load(TornadoLibraryProvider.class, LibraryRegistry.class.getClassLoader())) {
                        discovered.add(provider);
                    }
                    providers = discovered;
                }
            }
        }
        return providers;
    }

    public static TornadoLibraryProvider findProvider(String libraryName, TornadoXPUDevice device) {
        TornadoLibraryProvider match = null;
        for (TornadoLibraryProvider provider : getProviders()) {
            if (provider.libraryName().equals(libraryName)) {
                match = provider;
                if (provider.canHandle(device)) {
                    return provider;
                }
            }
        }
        if (match != null) {
            throw new TornadoRuntimeException("[ERROR] Library `" + libraryName + "` is not supported on device: " + device + ". Select a compatible backend/device for this library task.");
        }
        throw new TornadoRuntimeException("[ERROR] No provider found for library `" + libraryName + "`. Ensure the corresponding TornadoVM library module (e.g., tornado-cublas) is on the module path.");
    }

    public static LibraryContext getOrCreateContext(TornadoLibraryProvider provider, TornadoXPUDevice device, long executionPlanId) {
        return CONTEXTS.computeIfAbsent(new ContextKey(provider.libraryName(), device, executionPlanId), key -> provider.createContext(device, executionPlanId));
    }

    /**
     * Destroys all native library contexts created for the given execution plan.
     */
    public static void destroyContexts(long executionPlanId) {
        Iterator<Map.Entry<ContextKey, LibraryContext>> iterator = CONTEXTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ContextKey, LibraryContext> entry = iterator.next();
            if (entry.getKey().executionPlanId() == executionPlanId) {
                for (TornadoLibraryProvider provider : getProviders()) {
                    if (provider.libraryName().equals(entry.getKey().libraryName())) {
                        provider.destroyContext(entry.getValue());
                        break;
                    }
                }
                iterator.remove();
            }
        }
    }
}
