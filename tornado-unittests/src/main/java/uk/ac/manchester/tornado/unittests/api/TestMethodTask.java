/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoTaskRuntimeException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Tests naming a kernel by {@link Method} rather than by a method reference.
 *
 * <p>Every other way of adding a task takes a lambda, and the kernel is recovered from the
 * {@code SerializedLambda} the compiler emits. That only works for a method reference written in
 * source, which rules out kernels generated at run time -- the case this API exists for.
 *
 * <p>How to run?
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestMethodTask
 * </code>
 */
public class TestMethodTask extends TornadoTestBase {

    public static void scale(FloatArray in, FloatArray out, float factor) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * factor);
        }
    }

    /** Not static, so it cannot be a kernel: a Method carries no receiver to invoke it on. */
    public void instanceKernel(FloatArray in, FloatArray out) {
    }

    @Test
    public void testMethodTask() throws Exception {
        final int size = 1024;
        FloatArray in = new FloatArray(size);
        FloatArray out = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            in.set(i, i);
        }
        out.init(0.0f);

        Method kernel = TestMethodTask.class.getMethod("scale", FloatArray.class, FloatArray.class, float.class);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t0", kernel, in, out, 3.0f) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executionPlan.execute();
        }

        for (int i = 0; i < size; i++) {
            assertEquals(i * 3.0f, out.get(i), 0.001f);
        }
    }

    @Test
    public void testMethodTaskRejectsNonStatic() throws Exception {
        Method kernel = TestMethodTask.class.getMethod("instanceKernel", FloatArray.class, FloatArray.class);
        TaskGraph taskGraph = new TaskGraph("s1");

        TornadoTaskRuntimeException e = assertThrows(TornadoTaskRuntimeException.class, //
                () -> taskGraph.task("t0", kernel, new FloatArray(1), new FloatArray(1)));
        assertTrue(e.getMessage(), e.getMessage().contains("must be static"));
    }

    @Test
    public void testMethodTaskRejectsNull() {
        TaskGraph taskGraph = new TaskGraph("s2");
        assertThrows(TornadoTaskRuntimeException.class, () -> taskGraph.task("t0", (Method) null));
    }
}
