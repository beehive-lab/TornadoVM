/*
 * Copyright (c) 2021, 2022, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.fft;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

/**
 * Example of FFT provided by Nikos Foutris.
 *
 * How to run:
 *
 * <code>
 *     tornado -m tornado.examples/uk.ac.manchester.tornado.examples.fft.TestFFT
 * </code>
 */
public class TestFFT {

    public static void nesting(int[] input, int dim, final int[] factors, int size, int dummyFac, int[] dimArr) {

        for (int i = 0; i < dimArr[0]; i++) {
            for (int j = 0; j < dimArr[1]; j++) {
                int product = 1;
                int state = 0;

                for (int z = 0; z < factors.length; z++) {
                    product *= input[z];

                    if (state == 0) {
                        state = 1;
                        if (factors[z] == 2) { // factors[z]
                            int factor = 2;
                            int q = factors[z] / product;
                            int p_1 = product / factor;
                            for (int k = 0; k < q; k++) {
                                for (int k1 = 0; k1 < p_1; k1++) {
                                    input[k1] = i + j + z + k;
                                }
                            }
                        }
                    } else {
                        state = 0;
                    }
                }
            }
        }
    }

    public static void nesting2(int[] input, int dim, final int[] factors, int size, int dummyFac, int[] dimArr) {
        int product = 1;
        int p_1,q = 1,factor = 2;
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                input[j] = i + j;
            }
            for (int z = 0; z < size; z++) {
                product *= input[z];
                p_1 = product / factor;
                if (factors[z] == 2) {
                    q += z;
                } else {
                    q += product;
                }
                for (int k1 = 0; k1 < p_1; k1++) {
                    input[k1] = i + k1 + z + q;
                }
            }
        }
    }

    public static void main(String[] args) {

        int[] input = new int[2];
        int[] factors = new int[2];
        input[0] = 4;
        factors[0] = 2;
        input[1] = 4;
        factors[1] = 2;
        int dim = 2;
        int[] dimArr = new int[] { 2, 2, 2 };
        int size = factors.length;
        int dummyFac = 2;
        int[] seq = new int[] { input[0], input[1] };

        TaskGraph s0 = new TaskGraph("x0");
        s0.transferToDevice(DataTransferMode.FIRST_EXECUTION, factors, dimArr);
        s0.task("t0", TestFFT::nesting, input, dim, factors, size, dummyFac, dimArr);
        s0.transferToHost(input);
        s0.execute();

        nesting(seq, dim, factors, size, dummyFac, dimArr);

        System.out.println("Tornado Output = " + Arrays.toString(input));
        System.out.println("Seq Output     = " + Arrays.toString(seq));

        boolean equals = Arrays.equals(input, seq);
        if (equals) {
            System.out.println("Result is correct");
        } else {
            System.out.println("Result is wrong");
        }

    }

}