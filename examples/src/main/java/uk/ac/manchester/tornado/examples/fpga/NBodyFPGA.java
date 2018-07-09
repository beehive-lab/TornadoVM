/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.examples.compute;

import java.io.*;
import java.util.*;

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.collections.math.*;
import static uk.ac.manchester.tornado.collections.math.TornadoMath.abs;
import uk.ac.manchester.tornado.runtime.api.*;

public class NBody {

      private  static  float delT,espSqr;
      private  static  float[] posSeq,velSeq;
      private  static      int[] inputSize;
      private  static  int numBodies;
      private static  TaskSchedule graph;

    private static void usage(String[] args) {
        final PrintStream printf = System.err.printf("Usage: Number of bodies is missing or number of iterations\n");
        System.exit(1);
    }

    private static void nBody(int numBodies, float[] refPos, float[] refVel, float delT, float espSqr,int[] inputSize) {
        for (@Parallel int i = 0; i < numBodies; i++) {
            int body = 4 * i;
            float[] acc = new float[] { 0.0f, 0.0f, 0.0f };
            for (int j = 0; j < inputSize[0]; j++) {
                float[] r = new float[3];
                int index = 4 * j;

                float distSqr = 0.0f;
                for (int k = 0; k < 3; k++) {
                    r[k] = refPos[index + k] - refPos[body + k];
                    distSqr += r[k] * r[k];
                }
    
                float invDist = (float) (1.0f / (float) TornadoMath.floatSqrt(distSqr + espSqr));

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


    public static  boolean validate() {
        boolean val = true;
        float[] posSeqSeq,velSeqSeq;
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
        posSeqSeq = new float[numBodies * 4];
        velSeqSeq = new float[numBodies * 4];

        for (int i = 0; i < auxPositionRandom.length; i++) {
            posSeq[i] = auxPositionRandom[i];
            posSeqSeq[i] = auxPositionRandom[i];
        }
        for (int i = 0; i < auxVelocityZero.length; i++) {
            velSeq[i] = auxVelocityZero[i];
            velSeqSeq[i] = auxVelocityZero[i];
        }
        graph = new TaskSchedule("s0");
        graph.task("t0", NBody::nBody, numBodies, posSeq, velSeq, delT, espSqr, inputSize);
        graph.warmup();
        graph.execute();
        graph.syncObjects(posSeq, velSeq);
        graph.clearProfiles();

        nBody(numBodies, posSeqSeq, velSeqSeq, delT, espSqr,inputSize);

        for (int i = 0; i < numBodies * 4; i++) {
            if (abs(posSeqSeq[i] - posSeq[i]) > 0.01) {
                val = false;
                break;
            }
            if (abs(velSeq[i] - velSeqSeq[i]) > 0.01) {
                val = false;
                break;
            }
        }
        System.out.printf("Number validation: " + val + "\n");
        return val;
    }

    public static void main(String[] args) {


        StringBuffer resultsIterations = new StringBuffer();

        if (args.length != 2) {
            usage(args);
        }

        numBodies = Integer.parseInt(args[0]);
        final int iterations = Integer.parseInt(args[1]);

        inputSize = new int[1];
        inputSize[0] = numBodies;


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
            nBody(numBodies, posSeq, velSeq, delT, espSqr,inputSize);
            long end = System.nanoTime();
            resultsIterations.append("Sequential execution time of iteration " + i + " is: " + (end - start) + " ns");
            resultsIterations.append("\n");
        }

        System.out.println(resultsIterations.toString());

        final TaskSchedule t0 = new TaskSchedule("s0").task("t0", NBody::nBody, numBodies, posSeq, velSeq, delT, espSqr,inputSize);

        t0.warmup();
        resultsIterations = null;

        resultsIterations = new StringBuffer();

        validate();

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
