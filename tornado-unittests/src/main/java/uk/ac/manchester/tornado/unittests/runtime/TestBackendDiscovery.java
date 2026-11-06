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
package uk.ac.manchester.tornado.unittests.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoBackendProvider;
import uk.ac.manchester.tornado.runtime.common.RuntimeServiceLoader;

/**
 * Checks that TornadoVM finds its backends when it is first used from a thread whose context class
 * loader cannot see the TornadoVM modules, as happens in applications that embed TornadoVM (for
 * example, Spark executor task threads).
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.runtime.TestBackendDiscovery
 * </code>
 */
public class TestBackendDiscovery {

    private static <T> T runWithIsolatedContextLoader(ThrowingSupplier<T> body) throws Throwable {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (URLClassLoader isolated = new URLClassLoader(new URL[0], null)) {
            Thread thread = new Thread(() -> {
                try {
                    result.set(body.get());
                } catch (Throwable t) {
                    failure.set(t);
                }
            });
            thread.setContextClassLoader(isolated);
            thread.start();
            thread.join();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    @Test
    public void testBackendProvidersFoundWithIsolatedContextLoader() throws Throwable {
        long providers = runWithIsolatedContextLoader(() -> StreamSupport.stream(RuntimeServiceLoader.load(TornadoBackendProvider.class).spliterator(), false).count());
        assertTrue(providers > 0, "no TornadoBackendProvider found from a thread with an isolated context class loader");
    }

    @Test
    public void testDeviceFoundWithIsolatedContextLoader() throws Throwable {
        TornadoDevice device = runWithIsolatedContextLoader(() -> TornadoExecutionPlan.getDevice(0, 0));
        assertNotNull(device);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
