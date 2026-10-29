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
package uk.ac.manchester.tornado.examples.deviceview;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Runs one kernel over a mix of ordinary TornadoVM arrays and {@code deviceView} arrays bound to
 * CUDA memory allocated outside TornadoVM.
 *
 * <p>
 * Run with:
 * {@code tornado -Dtornado.printKernel=True -m tornado.examples/uk.ac.manchester.tornado.examples.deviceview.ExternalBufferExample}
 * </p>
 */
public class ExternalBufferExample {

    public static void scaleMixed(FloatArray weights, FloatArray in, FloatArray out, int numRows) {
        for (@Parallel int i = 0; i < numRows; i++) {
            out.set(i, in.get(i) * weights.get(i) + 1.0f);
        }
    }

    public static void main(String[] args) {
        if (!CudaMemory.isAvailable()) {
            System.out.println("This example needs the CUDA driver library: " + CudaMemory.unavailableCause());
            return;
        }
        final int rows = args.length > 0 ? Integer.parseInt(args[0]) : 16;
        CudaMemory.enterPrimaryContext();

        float[] inputValues = new float[rows];
        for (int i = 0; i < rows; i++) {
            inputValues[i] = i * 0.25f;
        }
        FloatArray weights = new FloatArray(rows);
        weights.init(2.0f);

        long inPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        long outPointer = CudaMemory.allocate((long) rows * Float.BYTES);
        try {
            CudaMemory.copyToDevice(inPointer, inputValues);

            FloatArray in = FloatArray.deviceView(rows);
            FloatArray out = FloatArray.deviceView(rows);

            TaskGraph graph = new TaskGraph("external") //
                    .transferToDevice(DataTransferMode.EVERY_EXECUTION, weights) //
                    .task("scale", ExternalBufferExample::scaleMixed, weights, in, out, rows) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, weights);

            TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot());
            plan.bindExternalBuffer(in, inPointer, (long) rows * Float.BYTES, rows, -1, true);
            plan.bindExternalBuffer(out, outPointer, (long) rows * Float.BYTES, rows, -1, false);
            plan.execute();
            CudaMemory.synchronizeContext();

            float[] result = CudaMemory.copyFloatsToHost(outPointer, rows);
            System.out.println("weights  (headered TornadoVM array): " + weights.get(0) + " ...");
            System.out.println("in       (headerless device view at 0x" + Long.toHexString(inPointer) + ")");
            System.out.println("out      (headerless device view at 0x" + Long.toHexString(outPointer) + ")");
            boolean correct = true;
            for (int i = 0; i < rows; i++) {
                float expected = inputValues[i] * 2.0f + 1.0f;
                if (Math.abs(expected - result[i]) > 1e-4f) {
                    System.out.printf("row %d: expected %f, got %f%n", i, expected, result[i]);
                    correct = false;
                }
            }
            System.out.println(correct ? "Result: CORRECT" : "Result: WRONG");
        } finally {
            CudaMemory.free(inPointer);
            CudaMemory.free(outPointer);
        }
    }
}
