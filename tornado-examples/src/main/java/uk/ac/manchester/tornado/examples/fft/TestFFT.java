/*
 * Copyright (c) 2021-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.examples.fft;

import uk.ac.manchester.tornado.api.ImmutableTaskGraph;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

/**
 * Example of FFT provided by Nikos Foutris.
 *
 * How to run:
 *
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.fft.TestFFT
 * </code>
 */
public class TestFFT {
    // CHECKSTYLE:OFF

    public static void nesting(IntArray input, int dim, final IntArray factors, int size, int dummyFac, IntArray dimArr) {

        for (int i = 0; i < dimArr.get(0); i++) {
            for (int j = 0; j < dimArr.get(1); j++) {
                int product = 1;
                int state = 0;

                for (int z = 0; z < factors.getSize(); z++) {
                    product *= input.get(z);

                    if (state == 0) {
                        state = 1;
                        if (factors.get(z) == 2) { // factors[z]
                            int factor = 2;
                            int q = factors.get(z) / product;
                            int p_1 = product / factor;
                            for (int k = 0; k < q; k++) {
                                for (int k1 = 0; k1 < p_1; k1++) {
                                    input.set(k1, i + j + z + k);
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

    public static void nesting2(IntArray input, int dim, final IntArray factors, int size, int dummyFac, IntArray dimArr) {
        int product = 1;
        int p_1, q = 1, factor = 2;
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                input.set(j, i + j);
            }
            for (int z = 0; z < size; z++) {
                product *= input.get(z);
                p_1 = product / factor;
                if (factors.get(z) == 2) {
                    q += z;
                } else {
                    q += product;
                }
                for (int k1 = 0; k1 < p_1; k1++) {
                    input.set(k1, i + k1 + z + q);
                }
            }
        }
    }

    public static void main(String[] args) {

        IntArray input = new IntArray(2);
        IntArray factors = new IntArray(2);
        input.set(0, 4);
        factors.set(0, 2);
        input.set(1, 4);
        factors.set(1, 2);
        int dim = 2;
        IntArray dimArr = new IntArray(3);
        dimArr.init(2);
        int size = factors.getSize();
        int dummyFac = 2;
        IntArray seq = new IntArray(2);
        seq.set(0, input.get(0));
        seq.set(1, input.get(1));

        TaskGraph taskGraph = new TaskGraph("x0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, factors, dimArr) //
                .task("t0", TestFFT::nesting, input, dim, factors, size, dummyFac, dimArr) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, input);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        TornadoExecutionPlan executorPlan = new TornadoExecutionPlan(immutableTaskGraph);
        executorPlan.execute();

        nesting(seq, dim, factors, size, dummyFac, dimArr);

        System.out.println("Tornado Output = " + input);
        System.out.println("Seq Output     = " + seq);

        boolean equals = true;
        for (int i = 0; i < input.getSize(); i++) {
            if (input.get(i) != seq.get(i)) {
                equals = false;
                break;
            }
        }

        if (equals) {
            System.out.println("Result is correct");
        } else {
            System.out.println("Result is wrong");
        }

    }

}
// CHECKSTYLE:ON
