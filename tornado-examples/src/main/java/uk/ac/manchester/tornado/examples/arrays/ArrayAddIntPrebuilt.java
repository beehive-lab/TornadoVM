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

package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.AccessorParameters;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Example using the Prebuilt API of TornadoVM.
 *
 * <p>
 * <code>
 * $ tornado -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.ArrayAddIntPrebuilt
 * </code>
 * </p>
 */
public class ArrayAddIntPrebuilt {

    /**
     * The following method represents the prebuilt code. It performs a vector
     * addition using three parameters.
     */
    public static void add(IntArray a, IntArray b, IntArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void main(String[] args) {

        final int numElements = 8;
        IntArray a = new IntArray(numElements);
        IntArray b = new IntArray(numElements);
        IntArray c = new IntArray(numElements);

        a.init(1);
        b.init(2);
        c.init(0);

        String tornadoSDK = System.getenv("TORNADOVM_HOME");

        TornadoDevice device = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice();
        String filePath = tornadoSDK + "/examples/generated/";
        filePath += device.getPlatformName().contains("PTX") ? "add.ptx" : "add.cl";

        AccessorParameters accessorParameters = new AccessorParameters(3);
        accessorParameters.set(0, a, Access.READ_ONLY);
        accessorParameters.set(1, b, Access.READ_ONLY);
        accessorParameters.set(2, c, Access.WRITE_ONLY);

        TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .prebuiltTask("t0", "add", filePath, accessorParameters) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);

        WorkerGrid workerGrid = new WorkerGrid1D(numElements);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);

        try (TornadoExecutionPlan executor = new TornadoExecutionPlan(taskGraph.snapshot())) {
            executor.withDevice(device) //
                    .withGridScheduler(gridScheduler) //
                    .execute();
        } catch (TornadoExecutionPlanException e) {
            e.printStackTrace();
        }
    }
}
