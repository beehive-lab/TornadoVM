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
 * What the runtime needs to know about a kernel before compiling it: how it reads and writes each
 * parameter, its reductions, and whether it writes a loop index as data. TC, TornadoVM's compiler,
 * answers from the kernel's bytecode; its provider lives with the compiler hook in the drivers and
 * the runtime only sees this service.
 */
public final class KernelAccess {

    /** Answers the questions above for one kernel; empty when the compiler cannot read the method. */
    public interface Provider {
        Optional<Access[]> access(Method method);

        default Optional<Boolean> writesLoopIndex(Method method) {
            return Optional.empty();
        }

        default Optional<Reductions> reductions(Method method, List<Integer> reduceIndices) {
            return Optional.empty();
        }
    }

    /**
     * The operation of each {@code @Reduce} parameter a reduction is found for ({@code add},
     * {@code mul}, {@code min}, {@code max}), and the {@code @Parallel} loop's bound: the parameter
     * whose length it is (or -1), else a constant (or -1).
     */
    public record Reductions(List<String> operations, int boundParameter, long boundConstant) {
    }

    private static final Provider PROVIDER = ServiceLoader.load(Provider.class).findFirst().orElse(null);

    private KernelAccess() {
    }

    /**
     * How {@code method} uses each of its {@code arity} parameters. When the compiler cannot read the
     * method, every reference parameter is assumed read and written (the compilation then reports
     * why it cannot compile the kernel).
     */
    public static Access[] of(Method method, int arity) {
        Optional<Access[]> tc = Optional.empty();
        if (PROVIDER != null) {
            try {
                tc = PROVIDER.access(method);
            } catch (RuntimeException e) {
                tc = Optional.empty();
            }
        }
        if (tc.isPresent() && tc.get().length == arity) {
            return tc.get();
        }
        var conservative = new Access[arity];
        Arrays.fill(conservative, Access.NONE);
        var types = method.getParameterTypes();
        int receiver = arity - types.length;
        for (int i = 0; i < arity; i++) {
            boolean reference = i < receiver || !types[i - receiver].isPrimitive();
            conservative[i] = reference ? Access.READ_WRITE : Access.NONE;
        }
        return conservative;
    }

    /** Whether {@code method} writes a loop index as data (batched execution offsets it per batch). */
    public static boolean writesLoopIndex(Method method) {
        try {
            return PROVIDER != null && PROVIDER.writesLoopIndex(method).orElse(true);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** The reductions of a task with {@code @Reduce} parameters {@code reduceIndices}. */
    public static Optional<Reductions> reductions(Method method, List<Integer> reduceIndices) {
        if (PROVIDER == null) {
            return Optional.empty();
        }
        try {
            return PROVIDER.reductions(method, reduceIndices);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
