/*
 * Copyright (c) 2021-2022 APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.kernelcontext.compute;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.profiler.ChromeEventTracer;

/**
 * Montecarlo algorithm to approximate the PI value. This version has been
 * adapted from Marawacc test-suite.
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.kernelcontext.compute.NBody
 * </code>
 *
 */
public class NBody {
    // CHECKSTYLE:OFF

    private static boolean VALIDATION = true;
    private static float DELT = 0.005f;
    private static float ESP_SQR = 500.0f;

    private static void nBody(KernelContext context, int numBodies, FloatArray refPos, FloatArray refVel) {
        int i = context.globalIdx;
        int body = 4 * i;

        float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
        for (int j = 0; j < numBodies; j++) {
            float[] r = new float[3];
            int index = 4 * j;

            float distSqr = 0.0f;
            for (int k = 0; k < 3; k++) {
                r[k] = refPos.get(index + k) - refPos.get(body + k);
                distSqr += r[k] * r[k];
            }

            float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));

            float invDistCube = invDist * invDist * invDist;
            float s = refPos.get(index + 3) * invDistCube;

            for (int k = 0; k < 3; k++) {
                acc[k] += s * r[k];
            }
        }
        for (int k = 0; k < 3; k++) {
            refPos.set(body + k, refPos.get(body + k) + refVel.get(body + k) * DELT + 0.5f * acc[k] * DELT * DELT);
            refVel.set(body + k, refVel.get(body + k) + acc[k] * DELT);
        }
    }

    private static void nBody(int numBodies, FloatArray refPos, FloatArray refVel) {
        for (int i = 0; i < numBodies; i++) {
            int body = 4 * i;

            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos.get(index + k) - refPos.get(body + k);
                    distSqr += r[k] * r[k];
                }

                float invDist = (float) (1.0f / Math.sqrt(distSqr + ESP_SQR));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos.get(index + 3) * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos.set(body + k, refPos.get(body + k) + refVel.get(body + k) * DELT + 0.5f * acc[k] * DELT * DELT);
                refVel.set(body + k, refVel.get(body + k) + acc[k] * DELT);
            }
        }
    }

    public static boolean validate(int numBodies, FloatArray posTornadoVM, FloatArray velTornadoVM, FloatArray posSequential, FloatArray velSequential) {
        boolean isValid = true;

        for (int i = 0; i < numBodies * 4; i++) {
            if (Math.abs(posSequential.get(i) - posTornadoVM.get(i)) > 0.1) {
                isValid = false;
                break;
            }
            if (Math.abs(velSequential.get(i) - velTornadoVM.get(i)) > 0.1) {
                isValid = false;
                break;
            }
        }
        return isValid;
    }

    public static void main(String[] args) {
        FloatArray posTornadoVM, velTornadoVM;

        StringBuffer resultsIterations = new StringBuffer();

        int numBodies = 32768;
        int iterations = 10;

        if (args.length == 2) {
            numBodies = Integer.parseInt(args[0]);
            iterations = Integer.parseInt(args[1]);
        } else if (args.length == 1) {
            numBodies = Integer.parseInt(args[0]);
        }

        System.out.println("Running Nbody with " + numBodies + " bodies" + " and " + iterations + " iterations");

        FloatArray posSeq = new FloatArray(numBodies * 4);
        FloatArray velSeq = new FloatArray(numBodies * 4);

        for (int i = 0; i < posSeq.getSize(); i++) {
            posSeq.set(i, (float) Math.random());
        }

        velSeq.init(0.0f);

        posTornadoVM = new FloatArray(numBodies * 4);
        velTornadoVM = new FloatArray(numBodies * 4);

        for (int i = 0; i < posSeq.getSize(); i++) {
            posTornadoVM.set(i, posSeq.get(i));
        }
        for (int i = 0; i < velSeq.getSize(); i++) {
            velTornadoVM.set(i, velSeq.get(i));
        }

        long start = 0;
        long end = 0;
        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            nBody(numBodies, posSeq, velSeq);
            end = System.nanoTime();
            ChromeEventTracer.enqueueTaskIfEnabled("nbody sequential", start, end);
            resultsIterations.append("\tSequential execution time of iteration " + i + "is: " + (end - start) + " ns");
            resultsIterations.append("\n");
        }

        long timeSequential = (end - start);

        System.out.println(resultsIterations);

        WorkerGrid workerGrid = new WorkerGrid1D(numBodies);
        GridScheduler gridScheduler = new GridScheduler("s0.t0", workerGrid);
        KernelContext context = new KernelContext();
        // [Optional] Set the global work group
        workerGrid.setGlobalWork(numBodies, 1, 1);
        // [Optional] Set the local work group
        workerGrid.setLocalWork(1024, 1, 1);

        final TaskGraph t0 = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, posTornadoVM, velTornadoVM) //
                .task("t0", NBody::nBody, context, numBodies, posTornadoVM, velTornadoVM) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posTornadoVM, velTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = t0.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph);
        executor.withGridScheduler(gridScheduler);

        resultsIterations = new StringBuffer();

        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            executor.execute();
            end = System.nanoTime();
            ChromeEventTracer.enqueueTaskIfEnabled("nbody accelerated", start, end);
            resultsIterations.append("\tTornado execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");

        }
        long timeParallel = (end - start);

        System.out.println(resultsIterations.toString());

        if (VALIDATION) {
            boolean isValid = validate(numBodies, posTornadoVM, velTornadoVM, posSeq, velSeq);
            if (isValid) {
                System.out.println("Result is correct");
            } else {
                System.out.println("Result is wrong");
            }
        }

        System.out.println("Sequential time: " + timeSequential + " ns");
        System.out.println("TornadoVM time: " + timeParallel + " ns");
        System.out.println("Speedup in peak performance: " + (timeSequential / timeParallel) + "x");
    }

}
// CHECKSTYLE:ON
