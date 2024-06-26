/*
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples;

import java.math.BigDecimal;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.examples.common.Messages;

/**
 * <p>
 * Run with.
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.Init <size>
 * </code>
 *
 */
public class Init {

    private static final boolean CHECK = true;

    public static void compute(FloatArray array) {
        for (@Parallel int i = 0; i < array.getSize(); i++) {
            array.set(i, array.get(i) + 100);
        }
    }

    public static void main(String[] args) {

        int size = 300000000;
        if (args.length > 0) {
            size = Integer.parseInt(args[0]);
        }

        BigDecimal bytesToAllocate = new BigDecimal(((float) ((long) (size) * 4) * (float) 1E-6));
        System.out.println("Running with size: " + size);
        System.out.println("Input size: " + bytesToAllocate + " (MB)");
        FloatArray array = new FloatArray(size);

        TornadoDevice device = TornadoRuntimeProvider.getTornadoRuntime().getBackend(0).getDevice(0);
        long maxDeviceMemory = device.getMaxAllocMemory();
        double mb = maxDeviceMemory * 1E-6;
        System.out.println("Maximum alloc device memory: " + mb + " (MB)");

        TaskGraph taskGraph = new TaskGraph("s0") //
                .task("t0", Init::compute, array) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, array);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.execute();

        if (CHECK) {
            boolean check = true;
            for (int i = 0; i < array.getSize(); i++) {
                if (array.get(i) != 100) {
                    check = false;
                    break;
                }
            }
            if (!check) {
                System.out.println(Messages.WRONG);
            } else {
                System.out.println(Messages.CORRECT);
            }
        }
    }
}
