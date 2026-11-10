/*
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
package uk.ac.manchester.tornado.babylon.tc;

import java.lang.foreign.GroupLayout;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bound memory layouts of HAT buffers. A buffer's layout depends on the instance (an S32Array's
 * sequence length is its size; in a buffer of two arrays the second starts where the first one ends),
 * so it is recorded with the TornadoVM object that stands for the buffer, and the frontend reads the
 * layouts of the arguments of the task it compiles ({@code Frontend.taskArguments()}).
 *
 * <p>The runtime also asks about a kernel with no task at hand (which parameters it writes): for
 * that, the last layout a kernel's parameter was launched with is kept by method, and any one will do,
 * since the answer does not depend on sizes.</p>
 */
public final class HatLayouts {

    private static final Map<Object, GroupLayout> BY_OBJECT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Map<Method, Map<Integer, GroupLayout>> LAST = new ConcurrentHashMap<>();

    private HatLayouts() {
    }

    /** {@code object} (a {@code ByteArray} over a HAT buffer's memory) is the buffer of this {@code layout}. */
    public static void record(Object object, GroupLayout layout) {
        BY_OBJECT.put(object, layout);
    }

    /** {@code kernel} is launched with a buffer of this {@code layout} as its parameter {@code parameter}. */
    public static void record(Method kernel, int parameter, GroupLayout layout) {
        LAST.computeIfAbsent(kernel, k -> new ConcurrentHashMap<>()).put(parameter, layout);
    }

    /** The layout of the HAT buffer {@code object} stands for, or null when it is not one. */
    public static GroupLayout of(Object object) {
        return object == null ? null : BY_OBJECT.get(object);
    }

    /** The layout {@code kernel}'s parameter was last launched with, or null. */
    static GroupLayout last(Method kernel, int parameter) {
        var forKernel = LAST.get(kernel);
        return forKernel == null ? null : forKernel.get(parameter);
    }
}
