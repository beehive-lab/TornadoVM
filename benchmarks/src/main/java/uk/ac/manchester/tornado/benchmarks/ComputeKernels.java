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
 * Authors: Michalis Papadimitriou,Juan Fumero
 *
 */
package uk.ac.manchester.tornado.benchmarks;

import org.apache.lucene.util.*;

import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.collections.math.*;

public class ComputeKernels {
    static final float S_LOWER_LIMIT = 10.0f;

    static final float S_UPPER_LIMIT = 100.0f;

    static final float K_LOWER_LIMIT = 10.0f;

    static final float K_UPPER_LIMIT = 100.0f;

    static final float T_LOWER_LIMIT = 1.0f;

    static final float T_UPPER_LIMIT = 10.0f;

    static final float R_LOWER_LIMIT = 0.01f;

    static final float R_UPPER_LIMIT = 0.05f;

    static final float SIGMA_LOWER_LIMIT = 0.01f;

    static final float SIGMA_UPPER_LIMIT = 0.10f;

    /**
     * Parallel Implementation of the MonteCarlo computation: this is based on the
     * Marawacc compiler framework.
     * 
     * @author Juan Fumero
     *
     */
    public static void monteCarlo(float[] result, int size) {

        int total = size;
        final int iter = 25000;

        for (@Parallel int idx = 0; idx < total; idx++) {

            long seed = idx;
            float sum = 0.0f;

            for (int j = 0; j < iter; ++j) {
                // generate a pseudo random number (you do need it twice)
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

                // this generates a number between 0 and 1 (with an awful entropy)
                float x = ((float) (seed & 0x0FFFFFFF)) / 268435455f;

                // repeat for y
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
                float y = ((float) (seed & 0x0FFFFFFF)) / 268435455f;

                float dist = TornadoMath.sqrt(x * x + y * y);
                if (dist <= 1.0f) {
                    sum += 1.0f;
                }
            }
            sum = sum * 4;
            result[idx] = sum / (float) iter;
        }
    }

    public static void computeMontecarlo(float[] output, final int size) {
        for (@Parallel int j = 0; j < size; j++) {
            long seed = j;
            // generate a pseudo random number (you do need it twice)
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);

            // this generates a number between 0 and 1 (with an awful entropy)
            float x = (seed & 0x0FFFFFFF) / 268435455f;

            // repeat for y
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            float y = (seed & 0x0FFFFFFF) / 268435455f;

            float dist = (float) Math.sqrt(x * x + y * y);
            if (dist <= 1.0f) {
                output[j] = 1.0f;
            } else {
                output[j] = 0.0f;
            }
        }
    }

    public static void nBody(int numBodies, float[] refPos, float[] refVel, float delT, float espSqr) {
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

    /**
     * @brief Abromowitz Stegun approxmimation for PHI (Cumulative Normal
     *        Distribution Function)
     * @param X
     *            input value
     */
    final static float phi(final float X) {
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

        final float absX = Math.abs(X);
        final float t = one / (one + (temp4 * absX));

        final float y = (one - (oneBySqrt2pi * TornadoMath.exp((-X * X) / two) * t * (c1 + (t * (c2 + (t * (c3 + (t * (c4 + (t * c5))))))))));

        final float result = (X < zero) ? (one - y) : y;

        return result;
    }

    /*
     * @brief Calculates the call and put prices by using Black Scholes model
     * 
     * @param s Array of random values of current option price @param sigma Array of
     * random values sigma @param k Array of random values strike price
     * 
     * @param t Array of random values of expiration time @param r Array of random
     * values of risk free interest rate @param width Width of call price or put
     * price array @param call Array of calculated call price values
     * 
     * @param put Array of calculated put price values
     */
    public static void blackscholes(final float[] randArray, final float[] put, final float[] call) {
        for (@Parallel int gid = 0; gid < call.length; gid++) {
            final float two = 2.0f;
            final float inRand = randArray[gid];
            final float S = (S_LOWER_LIMIT * inRand) + (S_UPPER_LIMIT * (1.0f - inRand));
            final float K = (K_LOWER_LIMIT * inRand) + (K_UPPER_LIMIT * (1.0f - inRand));
            final float T = (T_LOWER_LIMIT * inRand) + (T_UPPER_LIMIT * (1.0f - inRand));
            final float R = (R_LOWER_LIMIT * inRand) + (R_UPPER_LIMIT * (1.0f - inRand));
            final float sigmaVal = (SIGMA_LOWER_LIMIT * inRand) + (SIGMA_UPPER_LIMIT * (1.0f - inRand));

            final float sigmaSqrtT = sigmaVal * TornadoMath.sqrt(T);

            final float d1 = (TornadoMath.log(S / K) + ((R + ((sigmaVal * sigmaVal) / two)) * T)) / sigmaSqrtT;
            final float d2 = d1 - sigmaSqrtT;

            final float KexpMinusRT = K * TornadoMath.exp(-R * T);

            float phiD1 = phi(d1);
            float phiD2 = phi(d2);

            call[gid] = (S * phiD1) - (KexpMinusRT * phiD2);
            phiD1 = phi(-d1);
            phiD2 = phi(-d2);

            put[gid] = (KexpMinusRT * phiD2) - (S * phiD1);
        }
    }

    public static final int intersectionCount(int numWords, LongBitSet a, LongBitSet b) {
        final long[] aBits = a.getBits();
        final long[] bBits = b.getBits();
        int sum = 0;
        for (@Parallel int i = 0; i < numWords; i++) {
            Long.bitCount(aBits[i] & bBits[i]);
        }
        return sum;
    }

    public static void vectorMultiply(final float[] a, final float[] b, final float[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] * b[i];
        }
    }

    public static void computeDft(double[] inreal, double[] inimag, double[] outreal, double[] outimag) {
        int n = inreal.length;
        for (@Parallel int k = 0; k < n; k++) { // For each output element
            double sumreal = 0;
            double sumimag = 0;
            for (int t = 0; t < n; t++) { // For each input element
                double angle = (2 * Math.PI * t * k) / n;
                sumreal += inreal[t] * Math.cos(angle) + inimag[t] * Math.sin(angle);
                sumimag += -inreal[t] * Math.sin(angle) + inimag[t] * Math.cos(angle);
            }
            outreal[k] = sumreal;
            outimag[k] = sumimag;
        }
    }

    /**
     * Parallel Implementation of the Mandelbrot: this is based on the Marawacc
     * compiler framework.
     * 
     * @author Juan Fumero
     *
     */
    public static void mandelbrot(int size, short[] output) {
        final int iterations = 10000;
        float space = 2.0f / size;

        for (@Parallel int i = 0; i < size; i++) {
            int indexIDX = i;
            for (@Parallel int j = 0; j < size; j++) {

                int indexJDX = j;

                float Zr = 0.0f;
                float Zi = 0.0f;
                float Cr = (1 * indexJDX * space - 1.5f);
                float Ci = (1 * indexIDX * space - 1.0f);

                float ZrN = 0;
                float ZiN = 0;
                int y = 0;

                for (y = 0; y < iterations; y++) {
                    float s = ZiN + ZrN;
                    if (s > 4.0f) {
                        break;
                    } else {
                        Zi = 2.0f * Zr * Zi + Ci;
                        Zr = 1 * ZrN - ZiN + Cr;
                        ZiN = Zi * Zi;
                        ZrN = Zr * Zr;
                    }

                }
                short r = (short) ((y * 255) / iterations);
                output[i * size + j] = r;
            }
        }
    }
}
