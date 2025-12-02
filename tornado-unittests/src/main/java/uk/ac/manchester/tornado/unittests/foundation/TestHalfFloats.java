/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.foundation;

import org.junit.Test;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * <p>
 * How to test?
 * </p>
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.foundation.TestHalfFloats
 * </code>
 */
public class TestHalfFloats extends TornadoTestBase {

    public static void convertFP32toFP16v1(KernelContext context, FloatArray wrapX, HalfFloatArray x) {
        int i = context.globalIdx;
        float valInput = wrapX.get(i);
        HalfFloat val = new HalfFloat(valInput);
        x.set(i,val);
    }

    public static void convertFP32toFP16v2(KernelContext context, FloatArray wrapX, HalfFloatArray x) {
        int i = context.globalIdx;
        HalfFloat val = new HalfFloat(wrapX.get(i));
        x.set(i,val);
    }


    public static void convertFP32toFP16Parallel(FloatArray wrapX, HalfFloatArray x) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            float valInput = wrapX.get(i);
            HalfFloat val = new HalfFloat(valInput);
            x.set(i,val);
        }
    }

    @Test
    public void testConvertFP32toFP16v1() throws TornadoExecutionPlanException {
        FloatArray x = new FloatArray(1024);
        HalfFloatArray y = new HalfFloatArray(1024);

        x.init(new Random().nextFloat());

        KernelContext context = new KernelContext();

        TaskGraph tg = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x)
                .task("t0", TestHalfFloats::convertFP32toFP16v1,context, x, y)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        WorkerGrid workerGrid  = new WorkerGrid1D(1024);
        workerGrid.setLocalWork(32,1,1);

        GridScheduler scheduler = new GridScheduler("s0.t0", workerGrid);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        for (int i = 0; i < 1024; i++) {
            assertEquals(x.get(i), y.get(i).getFloat32(), 0.001f);
        }
    }

    @Test
    public void testConvertFP32toFP16v2() throws TornadoExecutionPlanException {
        FloatArray x = new FloatArray(1024);
        HalfFloatArray y = new HalfFloatArray(1024);

        x.init(new Random().nextFloat());

        KernelContext context = new KernelContext();

        TaskGraph tg = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x)
                .task("t0", TestHalfFloats::convertFP32toFP16v2, context, x, y)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();
        WorkerGrid workerGrid  = new WorkerGrid1D(1024);
        workerGrid.setLocalWork(32,1,1);

        GridScheduler scheduler = new GridScheduler("s0.t0", workerGrid);

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.withGridScheduler(scheduler).execute();
        }

        for (int i = 0; i < 1024; i++) {
            assertEquals(x.get(i), y.get(i).getFloat32(), 0.001f);
        }
    }

    @Test
    public void testConvertFP32toFP16Parallel() throws TornadoExecutionPlanException {
        FloatArray x = new FloatArray(1024);
        HalfFloatArray y = new HalfFloatArray(1024);

        x.init(new Random().nextFloat());

        TaskGraph tg = new TaskGraph("s0")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, x)
                .task("t0", TestHalfFloats::convertFP32toFP16Parallel, x, y)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, y);

        ImmutableTaskGraph immutableTaskGraph = tg.snapshot();

        try (TornadoExecutionPlan executionPlan = new TornadoExecutionPlan(immutableTaskGraph)) {
            executionPlan.execute();
        }

        for (int i = 0; i < 1024; i++) {
            assertEquals(x.get(i), y.get(i).getFloat32(), 0.001f);
        }
    }

}
