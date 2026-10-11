/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.common;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import uk.ac.manchester.tornado.api.common.Access;

/**
 * How a kernel reads and writes its parameters, from TC (the Graal-free compiler) when it is
 * selected ({@code -Dtornado.compiler=tc}), instead of the Graal sketch. The provider lives with the
 * TC hook in the drivers; the runtime only sees this service. With
 * {@code -Dtornado.tc.checkAccess=true} both are computed and every difference is reported.
 */
public final class KernelAccess {

    /** Computes a kernel's {@link Access} per parameter, and its reductions; empty when it cannot. */
    public interface Provider {
        Optional<Access[]> access(Method method);

        /** Whether the kernel writes a loop index as data. */
        default Optional<Boolean> writesLoopIndex(Method method) {
            return Optional.empty();
        }

        /** The reductions of a task with {@code @Reduce} parameters {@code reduceIndices}. */
        default Optional<Reductions> reductions(Method method, List<Integer> reduceIndices) {
            return Optional.empty();
        }
    }

    /**
     * The operation of each {@code @Reduce} parameter ({@code add}, {@code mul}, {@code min},
     * {@code max}, or null), and the {@code @Parallel} loop's bound: the parameter whose length it is
     * (or -1), else a constant (or -1).
     */
    public record Reductions(List<String> operations, int boundParameter, long boundConstant) {
    }

    /** Whether {@code method} writes a loop index as data: TC's answer under TC, else {@code sketch}. */
    public static boolean writesLoopIndex(Method method, java.util.function.BooleanSupplier sketch) {
        if (TC && !CHECK && PROVIDER != null) {
            try {
                var tc = PROVIDER.writesLoopIndex(method);
                if (tc.isPresent()) {
                    return tc.get();
                }
            } catch (RuntimeException e) {
                // fall through to the sketch
            }
        }
        return sketch.getAsBoolean();
    }

    /** TC's reductions for the task-graph rewrite, when TC is selected or checked. */
    public static Optional<Reductions> reductions(Method method, List<Integer> reduceIndices) {
        if (!(TC || CHECK) || PROVIDER == null) {
            return Optional.empty();
        }
        try {
            return PROVIDER.reductions(method, reduceIndices);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static final boolean TC = "tc".equals(System.getProperty("tornado.compiler", "graal"));
    public static final boolean CHECK = Boolean.parseBoolean(System.getProperty("tornado.tc.checkAccess", "false"));

    private static final Provider PROVIDER = ServiceLoader.load(Provider.class).findFirst().orElse(null);

    private KernelAccess() {
    }

    /**
     * The accesses the runtime uses for {@code method}: TC's when TC is selected and computes them,
     * else the sketch's (Graal's), which {@code sketch} builds only when needed: under TC no sketch
     * is built. In check mode both are computed and a difference is printed; a TC access that misses
     * a read or a write the sketch has is marked UNSAFE (data would not be copied).
     */
    public static Access[] choose(Method method, java.util.function.Supplier<Access[]> sketch) {
        if (!(TC || CHECK) || PROVIDER == null) {
            return sketch.get();
        }
        Optional<Access[]> tc;
        try {
            tc = PROVIDER.access(method);
        } catch (RuntimeException e) {
            tc = Optional.empty();
        }
        if (CHECK) {
            var graal = sketch.get();
            report(method, graal, tc);
            return TC && tc.isPresent() && tc.get().length == graal.length ? tc.get() : graal;
        }
        return tc.isPresent() ? tc.get() : sketch.get();
    }

    /** Whether the task graph needs a Graal sketch for {@code method}: not under TC once TC computes its accesses. */
    public static boolean needsSketch(Method method) {
        if (!TC || CHECK || PROVIDER == null) {
            return true;
        }
        try {
            return PROVIDER.access(method).isEmpty();
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void report(Method method, Access[] sketch, Optional<Access[]> tc) {
        var name = method.getDeclaringClass().getName() + "." + method.getName();
        if (tc.isEmpty()) {
            System.out.println("[TC-ACCESS] " + name + " tc=unavailable sketch=" + Arrays.toString(sketch));
            return;
        }
        var t = tc.get();
        for (int i = 0; i < Math.min(t.length, sketch.length); i++) {
            if (t[i] != sketch[i]) {
                boolean unsafe = (sketch[i].position & ~t[i].position) != 0;
                System.out.println("[TC-ACCESS] " + name + " param " + i + " sketch=" + sketch[i] + " tc=" + t[i] + (unsafe ? " UNSAFE" : " superset"));
            }
        }
        if (t.length != sketch.length) {
            System.out.println("[TC-ACCESS] " + name + " arity tc=" + t.length + " sketch=" + sketch.length);
        }
    }
}
