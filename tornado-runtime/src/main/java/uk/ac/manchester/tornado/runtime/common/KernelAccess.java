/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2026, APT Group, Department of Computer Science,
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

        /** The method {@code method} only forwards its arguments to (a Kotlin function reference), if any. */
        default Optional<Method> forwardedMethod(Method method) {
            return Optional.empty();
        }
    }

    /**
     * The operation of each {@code @Reduce} parameter a reduction is found for ({@code add},
     * {@code mul}, {@code min}, {@code max}), and the {@code @Parallel} loop's bound: the parameter
     * whose length it is (or -1), else a constant (or -1), and its constant start (0 when not constant).
     */
    public record Reductions(List<String> operations, int boundParameter, long boundConstant, long start) {
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

    /**
     * The method a task runs. A Kotlin function reference passed as a task is compiled to a method that
     * only forwards its arguments to the function, without the function's parameter annotations
     * ({@code @Reduce}): the function is compiled in its place. Any other method is returned as it is.
     */
    public static Method kernel(Method method) {
        if (PROVIDER == null || hasParameterAnnotations(method)) {
            return method;
        }
        try {
            return PROVIDER.forwardedMethod(method).orElse(method);
        } catch (RuntimeException e) {
            return method;
        }
    }

    private static boolean hasParameterAnnotations(Method method) {
        return Arrays.stream(method.getParameterAnnotations()).anyMatch(annotations -> annotations.length > 0);
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
