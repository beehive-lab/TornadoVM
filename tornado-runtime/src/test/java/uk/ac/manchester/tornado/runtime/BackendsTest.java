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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.exceptions.TornadoBackendNotFound;
import uk.ac.manchester.tornado.runtime.common.enums.TornadoBackends;

public class BackendsTest {

    /** A provider of a backend whose only behaviour is its name. */
    private record FakeProvider(TornadoBackends kind) implements TornadoBackendProvider {
        @Override
        public String getName() {
            return kind.name();
        }

        @Override
        public TornadoAcceleratorBackend createBackend(TornadoVMConfigAccess config) {
            throw new UnsupportedOperationException("created through the test's factory");
        }

        @Override
        public TornadoBackends getDevicePriority() {
            return kind;
        }

        @Override
        public int compareTo(TornadoBackendProvider o) {
            return o.getDevicePriority().value() - kind.value();
        }
    }

    private static final List<TornadoBackendProvider> INSTALLED = List.of(new FakeProvider(TornadoBackends.CUDA), new FakeProvider(TornadoBackends.OpenCL));

    /** Creates fake backends and counts how often each kind is created. */
    private static final class Factory implements Function<TornadoBackendProvider, TornadoAcceleratorBackend> {
        final Map<TornadoBackends, AtomicInteger> created = new ConcurrentHashMap<>();

        @Override
        public TornadoAcceleratorBackend apply(TornadoBackendProvider provider) {
            created.computeIfAbsent(provider.getDevicePriority(), k -> new AtomicInteger()).incrementAndGet();
            return (TornadoAcceleratorBackend) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { TornadoAcceleratorBackend.class }, (proxy, method,
                    args) -> method.getName().equals("getName") ? provider.getName() : null);
        }

        int count(TornadoBackends kind) {
            AtomicInteger n = created.get(kind);
            return n == null ? 0 : n.get();
        }
    }

    @Test
    public void nothingIsCreatedUntilItIsNeeded() {
        Factory factory = new Factory();
        Backends backends = new Backends(INSTALLED, "", factory);
        assertEquals(2, backends.count());
        assertEquals(0, factory.created.size());
    }

    @Test
    public void usingOneBackendCreatesOnlyThatOne() {
        Factory factory = new Factory();
        Backends backends = new Backends(INSTALLED, "", factory);
        // Priority order: OpenCL (10) is backend 0, CUDA (0) backend 1, created or not.
        assertEquals("CUDA", backends.get(1).getName());
        assertEquals(1, factory.count(TornadoBackends.CUDA));
        assertEquals(0, factory.count(TornadoBackends.OpenCL));
        assertSame(backends.get(1), backends.get(1));
        assertEquals(1, factory.count(TornadoBackends.CUDA));
    }

    @Test
    public void enabledBackendsSelectAndKeepTheOrder() {
        Backends cudaOnly = new Backends(INSTALLED, "cuda", new Factory());
        assertEquals(1, cudaOnly.count());
        assertEquals("CUDA", cudaOnly.get(0).getName());
        Backends both = new Backends(INSTALLED, " CUDA , opencl ", new Factory());
        assertEquals("OpenCL", both.get(0).getName());
        assertThrows(TornadoBackendNotFound.class, () -> new Backends(INSTALLED, "metal", new Factory()));
    }

    @Test
    public void aBackendThatFailsFailsAgainWithoutRetryingAndLeavesTheOthers() {
        AtomicInteger attempts = new AtomicInteger();
        Factory working = new Factory();
        Backends backends = new Backends(INSTALLED, "", provider -> {
            if (provider.getDevicePriority() == TornadoBackends.OpenCL) {
                attempts.incrementAndGet();
                throw new IllegalStateException("no OpenCL platform");
            }
            return working.apply(provider);
        });
        IllegalStateException first = assertThrows(IllegalStateException.class, () -> backends.get(0));
        IllegalStateException again = assertThrows(IllegalStateException.class, () -> backends.get(0));
        assertSame(first, again);
        assertEquals(1, attempts.get());
        assertEquals("CUDA", backends.get(1).getName());
    }

    @Test
    public void aClassLookupSkipsABackendThatCannotBeCreated() {
        Factory working = new Factory();
        Backends backends = new Backends(INSTALLED, "", provider -> {
            if (provider.getDevicePriority() == TornadoBackends.OpenCL) {
                throw new IllegalStateException("no OpenCL platform");
            }
            return working.apply(provider);
        });
        Class<?> type = working.apply(new FakeProvider(TornadoBackends.CUDA)).getClass();
        assertEquals(1, backends.indexOf(type));
    }

    @Test
    public void indicesOutOfRangeAreReported() {
        Backends backends = new Backends(INSTALLED, "", new Factory());
        assertThrows(TornadoBackendNotFound.class, () -> backends.get(2));
        assertThrows(TornadoBackendNotFound.class, () -> backends.get(-1));
    }

    @Test
    public void makeDefaultExchangesTwoBackends() {
        Backends backends = new Backends(INSTALLED, "", new Factory());
        backends.makeDefault(1);
        assertEquals("CUDA", backends.get(0).getName());
        assertEquals("OpenCL", backends.get(1).getName());
    }

    @Test
    public void aClassIsLookedUpAmongCreatedBackendsFirst() {
        Factory factory = new Factory();
        Backends backends = new Backends(INSTALLED, "", factory);
        Class<?> type = backends.get(1).getClass();
        // Backend 0 (OpenCL) would match too (the fakes share a class), but it is not created for this.
        assertEquals(1, backends.indexOf(type));
        assertEquals(0, factory.count(TornadoBackends.OpenCL));
        assertEquals(-1, backends.indexOf(String.class));
    }

    @Test
    public void concurrentFirstUsesCreateABackendOnce() throws Exception {
        Factory factory = new Factory();
        Backends backends = new Backends(INSTALLED, "", factory);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<TornadoAcceleratorBackend>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return backends.get(1);
                }));
            }
            start.countDown();
            TornadoAcceleratorBackend first = results.get(0).get();
            for (Future<TornadoAcceleratorBackend> result : results) {
                assertSame(first, result.get());
            }
        }
        assertEquals(1, factory.count(TornadoBackends.CUDA));
    }
}
