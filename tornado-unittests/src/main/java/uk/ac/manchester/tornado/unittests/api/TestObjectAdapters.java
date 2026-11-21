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
package uk.ac.manchester.tornado.unittests.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.ObjectAdapter;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

/**
 * Objects of another library standing in for TornadoVM objects ({@link ObjectAdapter}): a {@link Holder}
 * of a {@link FloatArray}, passed as it is to a task, to transfers and to copies back on demand.
 *
 * <p>
 * How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.api.TestObjectAdapters
 * </code>
 */
public class TestObjectAdapters extends TornadoTestBase {

    /** Another library's object: TornadoVM does not know it. */
    public static final class Holder {
        final FloatArray data;

        Holder(int n) {
            data = new FloatArray(n);
        }
    }

    /** Installed through the module's {@code provides}: maps a Holder to its FloatArray. */
    public static final class HolderAdapter implements ObjectAdapter {
        static final List<String> BOUND = new CopyOnWriteArrayList<>();

        @Override
        public Object adapt(Object object) {
            return object instanceof Holder holder ? holder.data : null;
        }

        @Override
        public void bound(Object kernel, int parameter, Object object) {
            BOUND.add((kernel instanceof Method m ? m.getName() : "lambda") + "#" + parameter);
        }
    }

    public static void twice(FloatArray in, FloatArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, 2 * in.get(i));
        }
    }

    @Test
    public void testHoldersAsTaskArgumentsAndTransfers() throws TornadoExecutionPlanException, NoSuchMethodException {
        final int n = 1024;
        Holder in = new Holder(n);
        Holder out = new Holder(n);
        for (int i = 0; i < n; i++) {
            in.data.set(i, i);
        }
        Method kernel = TestObjectAdapters.class.getMethod("twice", FloatArray.class, FloatArray.class);
        TaskGraph graph = new TaskGraph("adapted") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, in) //
                .task("t", kernel, in, out) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, out);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute().transferToHost(out);
            for (int i = 0; i < n; i++) {
                assertEquals(2f * i, out.data.get(i), 0.0f, "first execution, out[" + i + "]");
            }
            // the host writes the holder; a targeted upload, then a second execution
            for (int i = 0; i < n; i++) {
                in.data.set(i, -i);
            }
            plan.transferToDevice(in);
            plan.execute().transferToHost(out);
            for (int i = 0; i < n; i++) {
                assertEquals(-2f * i, out.data.get(i), 0.0f, "second execution, out[" + i + "]");
            }
        }
        assertTrue(HolderAdapter.BOUND.contains("twice#0") && HolderAdapter.BOUND.contains("twice#1"), "bound: " + HolderAdapter.BOUND);
    }
}
