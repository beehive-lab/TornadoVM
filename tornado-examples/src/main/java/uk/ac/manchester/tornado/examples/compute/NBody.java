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
package uk.ac.manchester.tornado.examples.compute;

import static uk.ac.manchester.tornado.api.profiler.ChromeEventTracer.enqueueTaskIfEnabled;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.TornadoExecutionResult;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.compute.NBody
 * </code>
 */
public class NBody {
    // CHECKSTYLE:OFF
    private static boolean VALIDATION = true;

    private static void nBody(int numBodies, FloatArray refPos, FloatArray refVel, float delT, float espSqr) {
        for (@Parallel int i = 0; i < numBodies; i++) {
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

                float invDist = (float) (1.0f / Math.sqrt(distSqr + espSqr));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos.get(index + 3) * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos.set(body + k, (refPos.get(body + k) + refVel.get(body + k) * delT + 0.5f * acc[k] * delT * delT));
                refVel.set(body + k, (refVel.get(body + k) + acc[k] * delT));
            }
        }
    }

    private static boolean validate(int numBodies, FloatArray posTornadoVM, FloatArray velTornadoVM, FloatArray posSequential, FloatArray velSequential) {
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
        float delT, espSqr;
        FloatArray posSeq, velSeq;
        FloatArray posTornadoVM, velTornadoVM;

        StringBuffer resultsIterations = new StringBuffer();

        int numBodies = 32;
        int iterations = 1;

        if (args.length == 2) {
            numBodies = Integer.parseInt(args[0]);
            iterations = Integer.parseInt(args[1]);
        } else if (args.length == 1) {
            numBodies = Integer.parseInt(args[0]);
        }

        System.out.println("Running Nbody with " + numBodies + " bodies" + " and " + iterations + " iterations");

        delT = 0.005f;
        espSqr = 500.0f;

        FloatArray auxPositionRandom = new FloatArray(numBodies * 4);
        FloatArray auxVelocityZero = new FloatArray(numBodies * 3);

        for (int i = 0; i < auxPositionRandom.getSize(); i++) {
            auxPositionRandom.set(i, (float) Math.random());
        }

        auxVelocityZero.init(0.0f);

        posSeq = new FloatArray(numBodies * 4);
        velSeq = new FloatArray(numBodies * 4);
        posTornadoVM = new FloatArray(numBodies * 4);
        velTornadoVM = new FloatArray(numBodies * 4);

        for (int i = 0; i < auxPositionRandom.getSize(); i++) {
            posSeq.set(i, auxPositionRandom.get(i));
            posTornadoVM.set(i, auxPositionRandom.get(i));
        }
        for (int i = 0; i < auxVelocityZero.getSize(); i++) {
            velSeq.set(i, auxVelocityZero.get(i));
            velTornadoVM.set(i, auxVelocityZero.get(i));
        }

        long start = 0;
        long end = 0;
        for (int i = 0; i < iterations; i++) {
            System.gc();
            start = System.nanoTime();
            nBody(numBodies, posSeq, velSeq, delT, espSqr);
            end = System.nanoTime();
            enqueueTaskIfEnabled("nbody sequential", start, end);
            resultsIterations.append("\tSequential execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");
        }

        long timeSequential = (end - start);

        System.out.println(resultsIterations);

        final TaskGraph taskGraph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, posTornadoVM, velTornadoVM) //
                .task("t0", NBody::nBody, numBodies, posTornadoVM, velTornadoVM, delT, espSqr) //
                .transferToHost(DataTransferMode.UNDER_DEMAND, posTornadoVM, velTornadoVM);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executor = new TornadoExecutionPlan(immutableTaskGraph).withPreCompilation();

        resultsIterations = new StringBuffer();

        TornadoExecutionResult executionResult = null;

        for (int i = 0; i < iterations; i++) {
            // System.gc();
            start = System.nanoTime();
            executionResult = executor.execute();
            end = System.nanoTime();
            enqueueTaskIfEnabled("nbody accelerated", start, end);
            resultsIterations.append("\tTornado execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");

        }
        long timeParallel = (end - start);

        if (executionResult != null) {
            executionResult.transferToHost(posTornadoVM, velTornadoVM);
        }

        System.out.println(resultsIterations);

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
