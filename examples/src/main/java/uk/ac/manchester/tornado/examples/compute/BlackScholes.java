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
 * Authors: Juan Fumero
 *
 */

package uk.ac.manchester.tornado.examples.compute;

import java.util.Random;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.collections.math.TornadoMath;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

/**
 * BlackScholes implementation adapted from AMD-OpenCL examples and Marawacc compiler framework.
 *
 */
public class BlackScholes {

    private static void blackScholesKernel(float[] input, float[] callResult, float[] putResult) {
        for (@Parallel int idx = 0; idx < callResult.length; idx++) { 
            float rand = input[idx];      
            final float S_LOWER_LIMIT = 10.0f;
            final float S_UPPER_LIMIT = 100.0f;
            final float K_LOWER_LIMIT = 10.0f;
            final float K_UPPER_LIMIT = 100.0f;
            final float T_LOWER_LIMIT = 1.0f;
            final float T_UPPER_LIMIT = 10.0f;
            final float R_LOWER_LIMIT = 0.01f;
            final float R_UPPER_LIMIT = 0.05f;
            final float SIGMA_LOWER_LIMIT = 0.01f;
            final float SIGMA_UPPER_LIMIT = 0.10f;
            final float S = S_LOWER_LIMIT * rand + S_UPPER_LIMIT * (1.0f - rand);
            final float K = K_LOWER_LIMIT * rand + K_UPPER_LIMIT * (1.0f - rand);
            final float T = T_LOWER_LIMIT * rand + T_UPPER_LIMIT * (1.0f - rand);
            final float r = R_LOWER_LIMIT * rand + R_UPPER_LIMIT * (1.0f - rand);
            final float v = SIGMA_LOWER_LIMIT * rand + SIGMA_UPPER_LIMIT * (1.0f - rand);

            float d1 = (float) ((float)(TornadoMath.log(S / K) + ((r + (v * v / 2)) * T)) / v * TornadoMath.sqrt(T));
            float d2 = (float) ((float) d1 - (v * TornadoMath.sqrt(T)));
            callResult[idx] = (float) (S * cnd(d1) - K * TornadoMath.exp(T * (-1) * r) * cnd(d2));
            putResult[idx] = (float) (K * TornadoMath.exp(T * -r) * cnd(-d2) - S * cnd(-d1));
        }
    }

    private static float cnd(float X) {
        final float c1 = 0.319381530f;
        final float c2 = -0.356563782f;
        final float c3 = 1.781477937f;
        final float c4 = -1.821255978f;
        final float c5 = 1.330274429f;
        final float zero = 0.0f;
        final float one = 1.0f;
        final float two = 2.0f;
        final float temp4 = 0.2316419f;
        final float oneBySqrt2pi = 0.398942280f;
        float absX = TornadoMath.abs(X);
        float t = one / (one + temp4 * absX);
        float y = (float) (one - oneBySqrt2pi * TornadoMath.exp(-X * X / two) * t
                * (c1 + t * (c2 + t * (c3 + t * (c4 + t * c5)))));
        float result = (X < zero) ? (one - y) : y;
        return result;
    }

    private static boolean checkResult(float[] call, float[] put, float[] callPrice, float[] putPrice) {
        double delta = 0.8;
        for (int i = 0; i < call.length; i++) {
            if (Math.abs(call[i] - callPrice[i]) > delta) {
                System.out.println("call: " + call[i] + " vs gpu " + callPrice[i] );
                return false;
            }
            if (Math.abs(put[i] - putPrice[i]) > delta) {
                System.out.println("put: " + put[i] + " vs gpu " + putPrice[i] );
                return false;
            }
        }
        return true;
    }
    
    public static void blackScholes(int size) {
        
        Random random = new Random();        
        float[] input = new float[size];        
        float[] callPrice = new float[size];
        float[] putPrice = new float[size];
        
        for (int i = 0; i < size; i++) { 
            input[i] = random.nextFloat();
        }

        System.gc();
        long start = System.nanoTime();
        
        new TaskSchedule("s0")
            .task("t0", BlackScholes::blackScholesKernel, input, callPrice, putPrice)
            .streamOut(callPrice, putPrice)
            .execute();
        
        //blackScholes(input, callPrice, putPrice);
        long end = System.nanoTime();
        System.gc();
        
        System.out.println("Total time: " +  (end-start) + " ns");

    }
    
    public static void main(String[] args) {
        System.out.println("BlackScholes Tornado");
        blackScholes(512);

    }

}
