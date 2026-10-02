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

import java.io.Serializable;
import java.util.Objects;

import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task1;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task2;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task3;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task5;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task6;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task7;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task8;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task9;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task10;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task11;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task12;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task13;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task14;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task15;

/**
 * A kernel that another kernel can launch from the device, with
 * {@link KernelContext#launch(DeviceKernel, int, int, Object...)} (CUDA Dynamic Parallelism).
 *
 * <p>
 * The child kernel is a static, {@link KernelContext}-style method, named with a method reference.
 * The handle must be held in a {@code static final} field so the JIT compiler can resolve the child
 * while it compiles the parent:
 * </p>
 *
 * <pre>{@code
 * static final DeviceKernel CHILD = DeviceKernel.of(MyKernels::child);
 *
 * static void parent(KernelContext context, IntArray a, int n) {
 *     if (context.globalIdx == 0) {
 *         context.launch(CHILD, n, 256, a, n);
 *     }
 * }
 *
 * static void child(KernelContext context, IntArray a, int n) {
 *     int i = context.globalIdx;
 *     if (i < n) {
 *         a.set(i, a.get(i) * 2);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * Supported on the CUDA backend only.
 * </p>
 */
public final class DeviceKernel {

    private final Serializable kernel;

    private DeviceKernel(Serializable kernel) {
        this.kernel = Objects.requireNonNull(kernel, "kernel");
    }

    public static <T1> DeviceKernel of(Task1<T1> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2> DeviceKernel of(Task2<T1, T2> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3> DeviceKernel of(Task3<T1, T2, T3> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4> DeviceKernel of(Task4<T1, T2, T3, T4> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5> DeviceKernel of(Task5<T1, T2, T3, T4, T5> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6> DeviceKernel of(Task6<T1, T2, T3, T4, T5, T6> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7> DeviceKernel of(Task7<T1, T2, T3, T4, T5, T6, T7> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8> DeviceKernel of(Task8<T1, T2, T3, T4, T5, T6, T7, T8> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9> DeviceKernel of(Task9<T1, T2, T3, T4, T5, T6, T7, T8, T9> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> DeviceKernel of(Task10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> DeviceKernel of(Task11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> DeviceKernel of(Task12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> DeviceKernel of(Task13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> DeviceKernel of(Task14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> kernel) {
        return new DeviceKernel(kernel);
    }

    public static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> DeviceKernel of(Task15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> kernel) {
        return new DeviceKernel(kernel);
    }

    /**
     * The method reference this handle was created from. Backends resolve it to the child kernel's
     * method; it is not meant to be invoked.
     */
    public Object getKernel() {
        return kernel;
    }
}
