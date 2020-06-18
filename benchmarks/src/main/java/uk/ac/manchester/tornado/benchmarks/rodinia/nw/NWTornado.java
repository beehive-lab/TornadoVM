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
package uk.ac.manchester.tornado.benchmarks.rodinia.nw;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.annotations.Parallel;

public class NWTornado {

    private static final int LIMIT = -999;

    // @formatter:off
    static final int[] blosum62 = new int[]{
        4, -1, -2, -2, 0, -1, -1, 0, -2, -1, -1, -1, -1, -2, -1, 1, 0, -3, -2, 0, -2, -1, 0, -4,
        -1, 5, 0, -2, -3, 1, 0, -2, 0, -3, -2, 2, -1, -3, -2, -1, -1, -3, -2, -3, -1, 0, -1, -4,
        -2, 0, 6, 1, -3, 0, 0, 0, 1, -3, -3, 0, -2, -3, -2, 1, 0, -4, -2, -3, 3, 0, -1, -4,
        -2, -2, 1, 6, -3, 0, 2, -1, -1, -3, -4, -1, -3, -3, -1, 0, -1, -4, -3, -3, 4, 1, -1, -4,
        0, -3, -3, -3, 9, -3, -4, -3, -3, -1, -1, -3, -1, -2, -3, -1, -1, -2, -2, -1, -3, -3, -2, -4,
        -1, 1, 0, 0, -3, 5, 2, -2, 0, -3, -2, 1, 0, -3, -1, 0, -1, -2, -1, -2, 0, 3, -1, -4,
        -1, 0, 0, 2, -4, 2, 5, -2, 0, -3, -3, 1, -2, -3, -1, 0, -1, -3, -2, -2, 1, 4, -1, -4,
        0, -2, 0, -1, -3, -2, -2, 6, -2, -4, -4, -2, -3, -3, -2, 0, -2, -2, -3, -3, -1, -2, -1, -4,
        -2, 0, 1, -1, -3, 0, 0, -2, 8, -3, -3, -1, -2, -1, -2, -1, -2, -2, 2, -3, 0, 0, -1, -4,
        -1, -3, -3, -3, -1, -3, -3, -4, -3, 4, 2, -3, 1, 0, -3, -2, -1, -3, -1, 3, -3, -3, -1, -4,
        -1, -2, -3, -4, -1, -2, -3, -4, -3, 2, 4, -2, 2, 0, -3, -2, -1, -2, -1, 1, -4, -3, -1, -4,
        -1, 2, 0, -1, -3, 1, 1, -2, -1, -3, -2, 5, -1, -3, -1, 0, -1, -3, -2, -2, 0, 1, -1, -4,
        -1, -1, -2, -3, -1, 0, -2, -3, -2, 1, 2, -1, 5, 0, -2, -1, -1, -1, -1, 1, -3, -1, -1, -4,
        -2, -3, -3, -3, -2, -3, -3, -3, -1, 0, 0, -3, 0, 6, -4, -2, -2, 1, 3, -1, -3, -3, -1, -4,
        -1, -2, -2, -1, -3, -1, -1, -2, -2, -3, -3, -1, -2, -4, 7, -1, -1, -4, -3, -2, -2, -1, -2, -4,
        1, -1, 1, 0, -1, 0, 0, 0, -1, -2, -2, 0, -1, -2, -1, 4, 1, -3, -2, -2, 0, 0, 0, -4,
        0, -1, 0, -1, -1, -1, -1, -2, -2, -1, -1, -1, -1, -2, -1, 1, 5, -2, -2, 0, -1, -1, 0, -4,
        -3, -3, -4, -4, -2, -2, -3, -2, -2, -3, -2, -3, -1, 1, -4, -3, -2, 11, 2, -3, -4, -3, -2, -4,
        -2, -2, -2, -3, -2, -1, -2, -3, 2, -1, -1, -2, -1, 3, -3, -2, -2, 2, 7, -1, -3, -2, -1, -4,
        0, -3, -3, -3, -1, -2, -2, -3, -3, 3, 1, -2, 1, -1, -2, -2, 0, -3, -1, 4, -3, -2, -1, -4,
        -2, -1, 3, 4, -3, 0, 1, -1, 0, -3, -4, 0, -3, -3, -2, 0, -1, -4, -3, -3, 4, 1, -1, -4,
        -1, 0, 0, 1, -3, 3, 4, -2, 0, -3, -3, 1, -1, -3, -1, 0, -1, -3, -2, -2, 1, 4, -1, -4,
        0, -1, -1, -1, -2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -2, 0, 0, -2, -1, -1, -1, -1, -1, -4,
        -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, 1
    };
    // @formatter:on

    private static void usage() {
        System.err.printf("Usage: NW <max_rows/max_cols> <penalty> <num_threads>\n");
        System.err.printf("\t<dimension>      - x and y dimensions\n");
        System.err.printf("\t<penalty>        - penalty(positive integer)\n");
        System.err.printf("\t<num_threads>    - no. of threads\n");
        System.exit(1);
    }

    private static int toIndex(int x, int y, int lda) {
        return (y * lda) + x;
    }

    private static int max(int a, int b, int c) {
        return Math.max(Math.max(a, b), c);
    }

    private static void initialiseReference(int max_rows, int max_cols, int[] input_itemsets, int[] referrence, int[] bs) {
        for (@Parallel int i = 1; i < max_cols; i++) {
            for (@Parallel int j = 1; j < max_rows; j++) {
                referrence[i * max_cols + j] = bs[toIndex(input_itemsets[i * max_cols], input_itemsets[j], 24)];
            }
        }
    }

    private static void initialiseInput(int size, int stride, int penalty, int[] input_itemsets) {
        for (@Parallel int i = 1; i < size; i++) {
            input_itemsets[i * stride] = -i * penalty;
        }
    }

    private static void processTopLeft(int max_rows, int max_cols, int penalty, int[] input_itemsets, int[] referrence) {
        for (int i = 0; i < max_cols - 2; i++) {
            for (@Parallel int idx = 0; idx <= i; idx++) {
                final int index = (idx + 1) * max_cols + (i + 1 - idx);
                input_itemsets[index] = max(input_itemsets[index - 1 - max_cols] + referrence[index], input_itemsets[index - 1] - penalty, input_itemsets[index - max_cols] - penalty);

            }
        }
    }

    private static void processBottomRight(int max_rows, int max_cols, int penalty, int[] input_itemsets, int[] referrence) {
        for (@Parallel int t = 0; t < max_cols - 4; t++) {
            int i = max_cols - 4;
            for (int idx = 0; idx <= i; idx++) {
                final int index = (max_cols - idx - 2) * max_cols + idx + max_cols - i - 2;
                input_itemsets[index] = max(input_itemsets[index - 1 - max_cols] + referrence[index], input_itemsets[index - 1] - penalty, input_itemsets[index - max_cols] - penalty);
            }
            i--;

        }
    }

    private static void traceback(int max_rows, int max_cols, int penalty, int[] input_itemsets, int[] referrence) {
        try (PrintStream out = new PrintStream(new FileOutputStream("result.txt"));) {

            out.printf("print traceback value GPU:\n");
            int i = max_rows - 2;
            int j = max_rows - 2;
            for (; i >= 0 && j >= 0;) {
                int nw = 0,n = 0,w = 0,traceback;
                if (i == max_rows - 2 && j == max_rows - 2) {
                    out.printf("%d ", input_itemsets[i * max_cols + j]); // print the first element
                }
                if (i == 0 && j == 0) {
                    break;
                }
                if (i > 0 && j > 0) {
                    nw = input_itemsets[(i - 1) * max_cols + j - 1];
                    w = input_itemsets[i * max_cols + j - 1];
                    n = input_itemsets[(i - 1) * max_cols + j];
                } else if (i == 0) {
                    nw = n = LIMIT;
                    w = input_itemsets[i * max_cols + j - 1];
                } else if (j == 0) {
                    nw = w = LIMIT;
                    n = input_itemsets[(i - 1) * max_cols + j];
                }

                // traceback = maximum(nw, w, n);
                int new_nw,new_w,new_n;
                new_nw = nw + referrence[i * max_cols + j];
                new_w = w - penalty;
                new_n = n - penalty;

                traceback = max(new_nw, new_w, new_n);
                if (traceback == new_nw) {
                    traceback = nw;
                }
                if (traceback == new_w) {
                    traceback = w;
                }
                if (traceback == new_n) {
                    traceback = n;
                }

                out.printf("%d ", traceback);

                if (traceback == nw) {
                    i--;
                    j--;
                } else if (traceback == w) {
                    j--;
                } else if (traceback == n) {
                    i--;
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(NWTornado.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) {
        int input_itemsets[],output_itemsets[],referrence[];

        // the lengths of the two sequences should be able to divided by 16 and at
        // current stage max_rows needs to equal max_cols
        if (args.length != 3) {
            usage();
        }

        final int max_rows = Integer.parseInt(args[0]) + 1;
        final int max_cols = Integer.parseInt(args[0]) + 1;
        final int penalty = Integer.parseInt(args[1]);

        referrence = new int[max_rows * max_cols];
        input_itemsets = new int[max_rows * max_cols];

        Random random = new Random();
        random.setSeed(7);
        Arrays.fill(input_itemsets, 0);

        TaskSchedule s0 = new TaskSchedule("s0").task("init-ref", NWTornado::initialiseReference, max_rows, max_cols, input_itemsets, referrence, blosum62)
                .task("init-input1", NWTornado::initialiseInput, max_rows, max_cols, penalty, input_itemsets).task("init-input2", NWTornado::initialiseInput, max_cols, 1, penalty, input_itemsets)
                .task("topleft", NWTornado::processTopLeft, max_rows, max_cols, penalty, input_itemsets, referrence)
                .task("bottomright", NWTornado::processBottomRight, max_rows, max_cols, penalty, input_itemsets, referrence).streamOut(input_itemsets, referrence);

        s0.warmup();

        System.out.printf("Start Needleman-Wunsch\n");

        final long t0 = System.nanoTime();
        for (int i = 1; i < max_rows; i++) { // please define your own sequence.
            input_itemsets[i * max_cols] = Math.abs(random.nextInt()) % 10 + 1;
        }
        for (int j = 1; j < max_cols; j++) { // please define your own sequence.
            input_itemsets[j] = Math.abs(random.nextInt()) % 10 + 1;
        }

        s0.execute();
        final long t1 = System.nanoTime();
        System.out.printf("elapsed: %.9f s\n", (t1 - t0) * 1e-9);

        traceback(max_rows, max_cols, penalty, input_itemsets, referrence);
    }
}
