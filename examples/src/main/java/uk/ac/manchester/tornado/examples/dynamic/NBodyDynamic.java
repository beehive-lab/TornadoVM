/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package uk.ac.manchester.tornado.examples.dynamic;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.Policy;
import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class NBodyDynamic {

    private static boolean VALIDATION = true;

    private static void usage() {
        System.err.printf("Usage: <numBodies> <performance|end|sequential> <iterations>\n");
        System.exit(1);
    }

    private static void nBody(int numBodies, float[] refPos, float[] refVel, float delT, float espSqr, int[] inputSize) {
        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < numBodies; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos[index + k] - refPos[body + k];
                    distSqr += r[k] * r[k];
                }

                float invDist = (1.0f / (float) Math.sqrt(distSqr + espSqr));

                float invDistCube = invDist * invDist * invDist;
                float s = refPos[index + 3] * invDistCube;

                for (int k = 0; k < 3; k++) {
                    acc[k] += s * r[k];
                }
            }
            for (int k = 0; k < 3; k++) {
                refPos[body + k] += refVel[body + k] * delT + 0.5f * acc[k] * delT * delT;
                refVel[body + k] += acc[k] * delT;
            }
        }
    }

    public static boolean validate(int numBodies, float[] positionsResult, float[] velocityResult, float delT, float espSqr, int[] inputSize, float[] initialPosition, float[] initialVelocity) {
        boolean isValid = true;
        float[] posSeqSeq;
        float[] velSeqSeq;
        posSeqSeq = new float[numBodies * 4];
        velSeqSeq = new float[numBodies * 4];

        System.arraycopy(initialPosition, 0, posSeqSeq, 0, initialPosition.length);
        System.arraycopy(initialVelocity, 0, velSeqSeq, 0, initialVelocity.length);

        nBody(numBodies, posSeqSeq, velSeqSeq, delT, espSqr, inputSize);

        for (int i = 0; i < numBodies * 4; i++) {
            if (Math.abs(posSeqSeq[i] - positionsResult[i]) > 0.1) {
                isValid = false;
                break;
            }
            if (Math.abs(velSeqSeq[i] - velocityResult[i]) > 0.1) {
                isValid = false;
                break;
            }
        }
        return isValid;
    }

    public static void main(String[] args) {

        if (args.length < 3) {
            usage();
        }

        float delT;
        float espSqr;
        float[] positions,velocity;
        int[] inputSize;
        int numBodies;

        numBodies = Integer.parseInt(args[0]);
        String executionType = args[1];
        final int iterations = Integer.parseInt(args[2]);
        long end;
        long start;

        inputSize = new int[1];
        inputSize[0] = numBodies;

        delT = 0.005f;
        espSqr = 500.0f;

        float[] initialPosition = new float[numBodies * 4];
        float[] initialVelocity = new float[numBodies * 3];

        for (int i = 0; i < initialPosition.length; i++) {
            initialPosition[i] = (float) Math.random();
        }

        Arrays.fill(initialVelocity, 0.0f);

        positions = new float[numBodies * 4];
        velocity = new float[numBodies * 4];

        System.arraycopy(initialPosition, 0, positions, 0, initialPosition.length);
        System.arraycopy(initialVelocity, 0, velocity, 0, initialVelocity.length);

        long startInit = System.nanoTime();
        // @formatter:off
        final TaskSchedule s0 = new TaskSchedule("s0")
                .task("t0", NBodyDynamic::nBody, numBodies, positions, velocity, delT, espSqr, inputSize)
                .streamOut(positions, velocity);
        // @formatter:on
        long stopInit = System.nanoTime();
        System.out.println("Initialization time:  " + (stopInit - startInit) + " ns" + "\n");

        System.out.println("Heap size  " + Runtime.getRuntime().maxMemory() + " " + "\n");
        for (int i = 0; i < iterations; i++) {
            switch (executionType) {
                case "performance":
                    start = System.nanoTime();
                    s0.executeWithProfilerSequential(Policy.PERFORMANCE);
                    end = System.nanoTime();
                    break;
                case "end":
                    start = System.nanoTime();
                    s0.executeWithProfilerSequential(Policy.END_2_END);
                    end = System.nanoTime();
                    break;
                case "sequential":
                    System.gc();
                    start = System.nanoTime();
                    nBody(numBodies, positions, velocity, delT, espSqr, inputSize);
                    end = System.nanoTime();
                    break;
                default:
                    start = System.nanoTime();
                    s0.execute();
                    end = System.nanoTime();
            }
            double milliseconds = (end - start) / 1000000.0;
            System.out.println("Total time:  " + (end - start) + " ns  = " + milliseconds + "(ms) \n");
        }

        if (VALIDATION) {
            boolean isValid = validate(numBodies, positions, velocity, delT, espSqr, inputSize, initialPosition, initialVelocity);
            if (isValid) {
                System.out.println("Result is correct");
            } else {
                System.out.println("Result is wrong");
            }
        }
    }
}
