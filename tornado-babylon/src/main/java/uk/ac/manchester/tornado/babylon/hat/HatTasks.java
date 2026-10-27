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
package uk.ac.manchester.tornado.babylon.hat;

import java.io.Serializable;
import java.lang.foreign.GroupLayout;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import hat.Accelerator;
import optkl.ifacemapper.Buffer;
import optkl.ifacemapper.MappableIface;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.babylon.tc.HatLayouts;

/**
 * HAT kernels and HAT buffers in TornadoVM task graphs. A HAT kernel (a static {@code @Reflect} method
 * over HAT buffers, structs included) becomes a task of any {@link TaskGraph}, next to other tasks,
 * with TornadoVM's transfers, grid schedulers and execution plans (CUDA graphs included):
 *
 * <pre>{@code
 * var accelerator = new Accelerator(MethodHandles.lookup(), Backend.FIRST); // TornadoHatBackend
 * Universe universe = Universe.create(accelerator, n);                      // a HAT struct buffer
 * var hat = HatTasks.on(accelerator);
 * var graph = new TaskGraph("nbody")
 *         .transferToDevice(DataTransferMode.FIRST_EXECUTION, hat.of(universe));
 * hat.task(graph, "step", Kernels::step, universe, mass, delT);
 * graph.transferToHost(DataTransferMode.UNDER_DEMAND, hat.of(universe));
 * }</pre>
 *
 * A buffer is the same TornadoVM object ({@link #of}) every time, its memory in place: the HAT buffer
 * sees what the device wrote once TornadoVM has transferred it back.
 */
public final class HatTasks {

    private final Map<Buffer, ByteArray> objects = Collections.synchronizedMap(new IdentityHashMap<>());
    private final HeaderArena arena;

    private HatTasks(HeaderArena arena) {
        this.arena = arena;
    }

    /** For the buffers of {@code accelerator}, which must run on {@link TornadoHatBackend}. */
    public static HatTasks on(Accelerator accelerator) {
        if (!(accelerator.arena() instanceof HeaderArena headers)) {
            throw new IllegalArgumentException("HAT buffers can be used in TornadoVM task graphs only when their Accelerator runs on TornadoHatBackend; this one runs on "
                    + accelerator.backend.getClass().getName());
        }
        return new HatTasks(headers);
    }

    /** The TornadoVM object for {@code buffer}, over its memory in place, for transfers and task arguments. */
    public ByteArray of(Buffer buffer) {
        return objects.computeIfAbsent(buffer, b -> {
            var segment = MappableIface.getMemorySegment(b);
            return ByteArray.fromSegmentShallow(arena.tornadoSegment(segment)
                    .orElseThrow(() -> new IllegalArgumentException("this HAT buffer was not allocated by the Accelerator HatTasks.on was given")));
        });
    }

    public <A> TaskGraph task(TaskGraph graph, String id, Task1<A> kernel, A a) {
        return add(graph, id, kernel, a);
    }

    public <A, B> TaskGraph task(TaskGraph graph, String id, Task2<A, B> kernel, A a, B b) {
        return add(graph, id, kernel, a, b);
    }

    public <A, B, C> TaskGraph task(TaskGraph graph, String id, Task3<A, B, C> kernel, A a, B b, C c) {
        return add(graph, id, kernel, a, b, c);
    }

    public <A, B, C, D> TaskGraph task(TaskGraph graph, String id, Task4<A, B, C, D> kernel, A a, B b, C c, D d) {
        return add(graph, id, kernel, a, b, c, d);
    }

    public <A, B, C, D, E> TaskGraph task(TaskGraph graph, String id, Task5<A, B, C, D, E> kernel, A a, B b, C c, D d, E e) {
        return add(graph, id, kernel, a, b, c, d, e);
    }

    public <A, B, C, D, E, F> TaskGraph task(TaskGraph graph, String id, Task6<A, B, C, D, E, F> kernel, A a, B b, C c, D d, E e, F f) {
        return add(graph, id, kernel, a, b, c, d, e, f);
    }

    private TaskGraph add(TaskGraph graph, String id, Serializable kernel, Object... args) {
        Method method = method(kernel);
        Object[] tornadoArgs = Arrays.copyOf(args, args.length);
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Buffer buffer) {
                HatLayouts.record(method, i, (GroupLayout) MappableIface.getLayout(buffer));
                tornadoArgs[i] = of(buffer);
            }
        }
        TornadoHatBackend.addTask(graph, id, method, tornadoArgs);
        return graph;
    }

    /** The static method a kernel reference ({@code Kernels::step}) names. */
    private static Method method(Serializable reference) {
        try {
            Method writeReplace = reference.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            var lambda = (SerializedLambda) writeReplace.invoke(reference);
            var owner = Class.forName(lambda.getImplClass().replace('/', '.'), false, reference.getClass().getClassLoader());
            for (Method m : owner.getDeclaredMethods()) {
                if (m.getName().equals(lambda.getImplMethodName()) && java.lang.invoke.MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString()
                        .equals(lambda.getImplMethodSignature())) {
                    return m;
                }
            }
            throw new IllegalArgumentException("no method " + lambda.getImplMethodName() + lambda.getImplMethodSignature() + " in " + owner.getName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("a HAT kernel is given as a method reference, such as Kernels::step", e);
        }
    }
}
