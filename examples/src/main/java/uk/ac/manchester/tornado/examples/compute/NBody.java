/*
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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

package uk.ac.manchester.tornado.examples.compute;

import java.io.PrintStream;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class NBody {

    private static void usage(String[] args) {
        final PrintStream printf = System.err.printf("Usage: Number of bodies is missing or number of iterations\n");
        System.exit(1);
    }

    private static void nBody(int numBodies, float[] refPos, float[] refVel, float delT, float espSqr) {
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

                float invDist = (float) (1.0f / Math.sqrt(distSqr + espSqr));

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

    public static void main(String[] args) {
        float delT,espSqr;
        float[] posSeq,velSeq;

        StringBuffer resultsIterations = new StringBuffer();

        if (args.length != 2) {
            usage(args);
        }

        final int numBodies = Integer.parseInt(args[0]);
        final int iterations = Integer.parseInt(args[1]);

        delT = 0.005f;
        espSqr = 500.0f;

        float[] auxPositionRandom = new float[numBodies * 4];
        float[] auxVelocityZero = new float[numBodies * 3];

        for (int i = 0; i < auxPositionRandom.length; i++) {
            auxPositionRandom[i] = (float) Math.random();
        }

        Arrays.fill(auxVelocityZero, 0.0f);

        posSeq = new float[numBodies * 4];
        velSeq = new float[numBodies * 4];

        for (int i = 0; i < auxPositionRandom.length; i++) {
            posSeq[i] = auxPositionRandom[i];
        }
        for (int i = 0; i < auxVelocityZero.length; i++) {
            velSeq[i] = auxVelocityZero[i];
        }

        for (int i = 0; i < iterations; i++) {
            System.gc();
            long start = System.nanoTime();
            nBody(numBodies, posSeq, velSeq, delT, espSqr);
            long end = System.nanoTime();
            resultsIterations.append("Sequential execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");
        }

        System.out.println(resultsIterations.toString());

        final TaskSchedule t0 = new TaskSchedule("s0").task("t0", NBody::nBody, numBodies, posSeq, velSeq, delT, espSqr);

        t0.warmup();
        resultsIterations = null;

        resultsIterations = new StringBuffer();

        for (int i = 0; i < iterations; i++) {
            System.gc();
            long start = System.nanoTime();
            t0.execute();
            long end = System.nanoTime();
            resultsIterations.append("Tornado execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");
        }

        System.out.println(resultsIterations.toString());

    }

}
