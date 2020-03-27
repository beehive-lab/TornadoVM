/*
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.benchmarks.corrmatrix;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.util.FixedBitSet;
import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.common.TimedEvent;
import uk.ac.manchester.tornado.benchmarks.BenchLogger;

/**
 * This test class performs the following functions:
 *
 * 1) Create a randomly populated set of matrices for correlation/co-occurrence
 * computation 2) Execute the CPU-based computation using Lucene OpenBitSets 3)
 * Execute the GPU-based computation using Aparapi CorrMatrix host and kernel 4)
 * Verify the results of OpenBitSet and CorrMatrix by comparing matrices to each
 * other
 *
 * @author ryan.lamothe at gmail.com
 *
 */
public class CorrMatrixTest extends BenchLogger {

    // private static final Logger LOG = Logger.getLogger(CorrMatrixTest.class);
    private final List<Pair<FixedBitSet, FixedBitSet>> obsPairs = new ArrayList<>();;

    private final Random rand = new Random();

    private int[][] obsResultMatrix;

    private static final boolean traceEnabled = false;

    private static final boolean useJava = true;

    private static final boolean verify = true;

    /**
     * NumTerms and NumLongs (documents) need to be adjusted manually right now to
     * force 'striping' to occur (see Host code for details)
     */
    @Before
    public void setup() throws Exception {
        /*
         * Populate test data
         */
        debug("----------");
        debug("Populating test matrix data using settings from build.xml...");
        // System.out.println("----------");

        final int numTerms = Integer.getInteger("numRows", 1024); // #1024
        // Rows
        // numLongs*64 for number of actual documents since these are 'packed'
        // longs
        final int numLongs = Integer.getInteger("numColumns", 16384); // 16384
        // //
        // #
        // Columns

        for (int i = 0; i < numTerms; ++i) {
            final long[] bits = new long[numLongs];
            for (int j = 0; j < numLongs; ++j) {
                bits[j] = rand.nextLong();
            }

            obsPairs.add(i, new ImmutablePair<>(new FixedBitSet(bits, numLongs), new FixedBitSet(bits, numLongs)));
        }

        /*
         * OpenBitSet calculations
         */
        debug("Executing OpenBitSet intersectionCount");

        if (useJava) {
            final long startTime = System.nanoTime();

            obsResultMatrix = new int[obsPairs.size()][obsPairs.size()];

            // This is an N^2 comparison loop
            // FIXME This entire loop needs to be parallelized to show an
            // apples-to-apples comparison to Aparapi
            for (int i = 0; i < obsPairs.size(); i++) {
                final Pair<FixedBitSet, FixedBitSet> docFreqVector1 = obsPairs.get(i);

                for (int j = 0; j < obsPairs.size(); j++) {
                    final Pair<FixedBitSet, FixedBitSet> docFreqVector2 = obsPairs.get(j);

                    // # of matches in both sets of documents
                    final int result = (int) FixedBitSet.intersectionCount(docFreqVector1.getLeft(), docFreqVector2.getRight());
                    obsResultMatrix[i][j] = result;
                }
            }

            final long endTime = System.nanoTime();
            TimedEvent event = new TimedEvent(startTime, endTime);

            System.out.println("OpenBitSet Gross Execution Time: " + event.getTime() + " s <------OpenBitSet");
            System.out.println("----------");
        }
    }

    private static void print_mat(final int A[][]) {
        final int N = A.length;
        final int M = A[0].length;

        for (int j = 0; j < N; j++) {
            String str = "";
            for (int i = 0; i < M; i++) {
                str += " " + A[j][i];
            }
            System.out.printf("%02d | %s \n", j, str);
        }
    }

    @Test
    public void testCorrelationMatrix() throws Exception {
        /*
         * GPU calculations
         */
        debug("Executing Tornado intersectionCount");

        final long[][] matrixA = new long[obsPairs.size()][];
        final long[][] matrixB = new long[obsPairs.size()][];

        // Convert OpenBitSet pairs to long primitive arrays for use with
        // Aparapi
        // TODO It would be nice if we could find a way to put the obsPairs onto
        // the GPU directly :)
        for (int i = 0; i < obsPairs.size(); i++) {
            final FixedBitSet obsA = obsPairs.get(i).getLeft();
            final FixedBitSet obsB = obsPairs.get(i).getRight();

            matrixA[i] = obsA.getBits();
            matrixB[i] = obsB.getBits();
        }

        // The reason for setting this property is because the CorrMatrix
        // host/kernel code
        // came from a GUI where a user could select "Use Hardware Acceleration"
        // instead
        // of the application forcing the setting globally on the command-line
        final int[][] gpuResultMatrix;

        final long startTime = System.nanoTime();
        gpuResultMatrix = CorrMatrixHost.intersectionMatrix(matrixA, matrixB);
        final long endTime = System.nanoTime();
        TimedEvent event = new TimedEvent(startTime, endTime);

        System.out.println("OpenBitSet Gross Execution Time: " + event.getTime() + " s <------OpenBitSet");
        System.out.println("----------");
        boolean printResults = false;
        if (printResults) {
            print_mat(obsResultMatrix);
            System.out.println("");
            print_mat(gpuResultMatrix);
        }

        if (verify) {
            // Compare the two result arrays to make sure we are generating the
            // same
            // output
            int errors = 0;
            for (int i = 0; i < obsResultMatrix.length; i++) {
                // Assert.assertTrue("Arrays are not equal",
                for (int j = 0; j < obsResultMatrix[i].length; j++) {
                    int v1 = obsResultMatrix[i][j];
                    int v2 = gpuResultMatrix[i][j];
                    if (v1 != v2) {
                        errors++;
                        // System.out.printf("[%4d,%4d]: %d != %d, delta=%d\n",
                        // i,
                        // j,
                        // v1, v2, Math.abs(v1 - v2));
                    }
                }
            }

            if (errors > 0) {
                warn("found %d errors (%.2f %%)\n", errors, ((double) errors / (double) (obsResultMatrix.length * obsResultMatrix[0].length)) * 100.0);
            }
        }
        // Visually compare/third-party tool compare if desired
        if (traceEnabled) {
            // We're not using "try with resources" because Aparapi currently
            // targets JDK 6
            final PrintWriter cpuOut = new PrintWriter(new File(System.getProperty("user.dir"), "trace/cpuOut.txt"));
            final PrintWriter gpuOut = new PrintWriter(new File(System.getProperty("user.dir"), "trace/gpuOut.txt"));

            try {
                for (int i = 0; i < obsResultMatrix.length; i++) {
                    if (traceEnabled) {
                        trace("obsResultMatrix length: " + obsResultMatrix.length);
                        trace("gpuResultMatrix length: " + gpuResultMatrix.length);

                        cpuOut.println(Arrays.toString(obsResultMatrix[i]));
                        gpuOut.println(Arrays.toString(gpuResultMatrix[i]));
                    }
                }
            } finally {
                if (cpuOut != null) {
                    cpuOut.flush();
                    cpuOut.close();
                }

                if (gpuOut != null) {
                    gpuOut.flush();
                    gpuOut.close();
                }
            }
        }
    }

    public static void main(String[] args) {
        CorrMatrixTest test = new CorrMatrixTest();
        try {
            test.setup();
            test.testCorrelationMatrix();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
