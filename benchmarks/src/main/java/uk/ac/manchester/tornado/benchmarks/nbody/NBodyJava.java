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

package uk.ac.manchester.tornado.benchmarks.nbody;

import static uk.ac.manchester.tornado.benchmarks.ComputeKernels.nBody;

import java.util.Arrays;

import uk.ac.manchester.tornado.benchmarks.BenchmarkDriver;

public class NBodyJava extends BenchmarkDriver {
    private float delT,espSqr;
    private float[] posSeq,velSeq;
    private int numBodies;

    public NBodyJava(int numBodies, int iterations) {
        super(iterations);
        this.numBodies = numBodies;
    }

    @Override
    public void setUp() {
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
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void code() {
        nBody(numBodies, posSeq, velSeq, delT, espSqr);
    }
}
