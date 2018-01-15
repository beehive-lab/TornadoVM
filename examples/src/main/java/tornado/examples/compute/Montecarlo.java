/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: Juan Fumero
 *
 */
package tornado.examples.compute;

import tornado.api.Atomic;
import tornado.api.Parallel;
import tornado.runtime.api.TaskSchedule;

/**
 * Montecarlo algorithm to approximate the PI value. This version has been adapted from Marawacc test suite. 
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
