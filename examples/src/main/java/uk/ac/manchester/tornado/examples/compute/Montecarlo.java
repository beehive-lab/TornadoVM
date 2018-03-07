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

import uk.ac.manchester.tornado.api.Atomic;
import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;

/**
 * Montecarlo algorithm to approximate the PI value. This version has been adapted from Marawacc test-suite. 
 *
 */
public class Montecarlo {

    private static void computeMontecarlo(float[] output, final int iterations) {
        @Atomic float sum = 0.0f;
        for (@Parallel int j = 0; j < iterations; j++) {
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
                //synchronized(output) {
                //    output[0] += 1.0f;
                //}
                output[j] = 1.0f;
            } else {
                output[j] = 0.0f;
            }
        }
        // output[0] = sum;
        //    sum *= 4;
        //    output[idx] = sum / (float)iterations;
        //}
    }
    
    public static void montecarlo(final int size) {
        float[] output = new float[size];
        float[] seq = new float[size];
        
        TaskSchedule t0 = new TaskSchedule("s0")
            .task("t0", Montecarlo::computeMontecarlo, output, size)
            .streamOut(output);
        
        for (int i = 0; i < 21; i++) {
            long start = System.nanoTime();
            t0.execute();
            long end = System.nanoTime();
            long tornadoTime = (end-start);

            float sum = 0;
            for (int j = 0; j < size; j++) {
                sum += output[j];
            }
            //sum = output[0];
            sum *= 4;
            System.out.println("Total time (Tornado)   : " + (tornadoTime));
            System.out.println("Pi value(Tornado)   : " + (sum / size));
            
            start = System.nanoTime();
            computeMontecarlo(seq, size);
            end = System.nanoTime();
            long sequentialTime = (end-start);
            
            sum = 0;
            for (int j = 0; j < size; j++) {
                sum += seq[j];
            }
            sum *= 4;
            
            System.out.println("Total time (Sequential): " + (sequentialTime));
            System.out.println("Pi value(seq)   : " + (sum / size));

            double speedup = (double)sequentialTime / (double)tornadoTime;
            System.out.println("Speedup: " + speedup);
        }
        
    }
    
    
    public static void main(String[] args) {
        System.out.println("Compute Montecarlo");
        montecarlo(100000000);
    }
    
    
}
