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
package uk.ac.manchester.tornado.benchmarks.corrmatrix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import uk.ac.manchester.tornado.api.TaskSchedule;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

/**
 * GPU calculations using OpenBitSet Intersection for OpenBitSets
 *
 * Based on code from: <br/>
 * 
 * {@link http://grepcode.com/file/repo1.maven.org/maven2/org.apache.lucene/lucene
 * -core/3.1.0/org/apache/lucene/util/BitUtil.java}
 *
 * @author ryan.lamothe at gmail.com
 * @author sedillard at gmail.com
 */
public class CorrMatrixHost {

    private static final Logger LOG = LogManager.getLogger(CorrMatrixHost.class);

    /**
     * Perform matrix intersection for two lists of Lucene OpenBitSet-based packed
     * longs
     *
     * @param matrixA
     *            The first term-document matrix
     * @param matrixB
     *            The second term-document matrix
     *
     * @return result Matrix
     *
     * @throws Exception
     */
    public static int[][] intersectionMatrix(final long[][] matrixA, final long[][] matrixB) throws Exception {

        // Basic validation
        if (matrixA == null) {
            throw new NullPointerException("MatrixA cannot be NULL");
        }

        if (matrixB == null) {
            throw new NullPointerException("MatrixB cannot be NULL");
        }

        // Size of an array is 8 bytes for the object + 4 bytes for the header
        // and length information
        final int arrayMemOverhead = 8;

        // numDocs/64 since they are packed into longs
        // We need to make our matrix sizes multiples of BLOCK_SIZE
        final int matrixA_numTerms = matrixA.length;
        final int matrixA_numLongs = matrixA[0].length;

        if (LOG.isDebugEnabled()) {
            LOG.debug("----------");
            LOG.debug("MatrixA NumTerms (Rows): " + matrixA_numTerms);
            LOG.debug("MatrixA NumLongs (Columns): " + matrixA_numLongs);
            LOG.debug("MatrixA NumDocs: " + (matrixA_numLongs * 64L));
        }

        final long matrixA_BytesPerRow = matrixA_numLongs * 8L;
        final long matrixA_TotalBytes = (matrixA_numTerms * matrixA_BytesPerRow) + arrayMemOverhead;

        if (LOG.isDebugEnabled()) {
            LOG.debug("MatrixA Total Memory Size: " + humanReadableByteCount(matrixA_TotalBytes, true));
        }

        final int matrixB_numTerms = matrixB.length;
        final int matrixB_numLongs = matrixB[0].length;

        if (LOG.isDebugEnabled()) {
            LOG.debug("----------");
            LOG.debug("MatrixB NumTerms (Rows): " + matrixB_numTerms);
            LOG.debug("MatrixB NumLongs (Columns): " + matrixB_numLongs);
            LOG.debug("MatrixB NumDocs: " + (matrixB_numLongs * 64L));
        }

        final long matrixB_BytesPerRow = matrixB_numLongs * 8L;
        final long matrixB_TotalBytes = (matrixB_numTerms * matrixB_BytesPerRow) + arrayMemOverhead;

        if (LOG.isDebugEnabled()) {
            LOG.debug("MatrixB Total Memory Size: " + humanReadableByteCount(matrixB_TotalBytes, true));
            LOG.debug("----------");
        }

        final int[][] resultMatrix = new int[matrixA_numTerms][matrixB_numTerms];

        if (LOG.isDebugEnabled()) {
            final long resultMatrix_TotalBytes = (matrixA_numTerms * matrixB_numTerms * 4L) + arrayMemOverhead;
            LOG.debug("ResultMatrix Memory Size: " + humanReadableByteCount(resultMatrix_TotalBytes, true));
            LOG.debug("Total Requested Memory Size: " + humanReadableByteCount(matrixA_TotalBytes + matrixB_TotalBytes + resultMatrix_TotalBytes, true));
            LOG.debug("----------");
        }

        int NUM_SUB_ROWS = matrixA_numTerms; // Default number of sub-rows

        // This is to create stripes of rows that will fit into OpenCL's
        // available memory
        // Calculate the number of sub-rows by calling OpenCL to find out
        // available memory
        // Length of row * 8 (size of long in bytes) * number of rows to
        // available memory
        final int maxNumTerms = Math.max(matrixA_numTerms, matrixB_numTerms);

        final long maxMemAllocSize = (long) (128.6 * 1024 * 1024);
        // final long maxMemAllocSize = device.getMaxMemAllocSize();

        // 1048576 bytes in a megabyte (1024*1024)
        // Java long is 8 bytes
        // 131072 longs in 1 megabyte
        // SAFE OpenCL spec allocation is max(1/4 GlobalMemSize)
        // ***During our testing this appears to be
        // incorrectly/inconsistently reported depending on
        // os/drivers/hardware***
        if (LOG.isDebugEnabled()) {
            LOG.debug("Available OpenCL maxMemAllocSize: " + humanReadableByteCount(maxMemAllocSize, true));
        }

        // Maybe there is a more clever way to do this :)
        // The idea here is to decide how many sub-rows of the matrix we can
        // fit on a single card
        // The long-term goal to divide up the work for both small RAM GPUs
        // and multiple GPUs
        int subRowsCounterA = 0;
        int subRowsCounterB = 0;
        long subRowsMemSizeA = 0L;
        long subRowsMemSizeB = 0L;
        long subResultMatrixMemSize = 0L;
        long subTotalMemSize = 0L;

        do {
            if (subRowsCounterA < matrixA_numTerms) {
                subRowsMemSizeA = subRowsCounterA != 0 ? (subRowsCounterA * matrixA_numLongs * 8L) + arrayMemOverhead : 0;
                subRowsCounterA += 1;
            } else if (subRowsCounterA == matrixA_numTerms) {
                subRowsMemSizeA = subRowsCounterA != 0 ? (subRowsCounterA * matrixA_numLongs * 8L) + arrayMemOverhead : 0;
            }

            if (subRowsCounterB < matrixB_numTerms) {
                subRowsMemSizeB = subRowsCounterB != 0 ? (subRowsCounterB * matrixB_numLongs * 8L) + arrayMemOverhead : 0;
                subRowsCounterB += 1;
            } else if (subRowsCounterB == matrixB_numTerms) {
                subRowsMemSizeB = subRowsCounterB != 0 ? (subRowsCounterB * matrixB_numLongs * 8L) + arrayMemOverhead : 0;
            }

            // This is 4 bytes since the sub-result matrix is an int array
            subResultMatrixMemSize = ((subRowsCounterA * subRowsCounterB) * 4L) + arrayMemOverhead;

            subTotalMemSize = subRowsMemSizeA + subRowsMemSizeB + subResultMatrixMemSize;
        } while ((Math.max(subRowsCounterA, subRowsCounterB) < maxNumTerms) && (subTotalMemSize <= maxMemAllocSize));

        // If using OpenCL override the default number of subrows
        NUM_SUB_ROWS = Math.max(subRowsCounterA, subRowsCounterB);

        if (NUM_SUB_ROWS < maxNumTerms) {
            final long subMatrixA_memSize = (NUM_SUB_ROWS * matrixA_numLongs * 8L) + arrayMemOverhead;
            final long subMatrixB_memSize = (NUM_SUB_ROWS * matrixB_numLongs * 8L) + arrayMemOverhead;
            final long subResultMatrix_memSize = (NUM_SUB_ROWS * NUM_SUB_ROWS * 4L) + arrayMemOverhead;

            LOG.warn("****************************************************************");
            LOG.warn("Requested matrix computation is larger than available OpenCL memory");
            LOG.warn("Matrix striping is occurring to fit all data into OpenCL memory...");
            LOG.warn("");
            LOG.warn("Number rows requested: " + maxNumTerms);
            LOG.warn("Number rows that fit: " + NUM_SUB_ROWS);
            LOG.warn("");
            LOG.warn("SubMatrixA Memory Size: " + humanReadableByteCount(subMatrixA_memSize, true));
            LOG.warn("SubMatrixB Memory Size: " + humanReadableByteCount(subMatrixB_memSize, true));
            LOG.warn("SubResultMatrix Memory Size: " + humanReadableByteCount(subResultMatrix_memSize, true));
            LOG.warn("SubMatrix Total Memory Size: " + humanReadableByteCount(subMatrixA_memSize + subMatrixB_memSize + subResultMatrix_memSize, true));
            LOG.warn("****************************************************************");
        }

        final int numSubBlocksA = ((matrixA_numTerms + NUM_SUB_ROWS) - 1) / NUM_SUB_ROWS;
        final int numSubBlocksB = ((matrixB_numTerms + NUM_SUB_ROWS) - 1) / NUM_SUB_ROWS;

        final long[] subMatrixA = new long[NUM_SUB_ROWS * matrixA_numLongs];
        final long[] subMatrixB = new long[NUM_SUB_ROWS * matrixB_numLongs];
        final int[] subResultMatrix = new int[NUM_SUB_ROWS * NUM_SUB_ROWS];

        final TaskSchedule s0 = new TaskSchedule("benchmark").streamIn(subMatrixA, subMatrixB)
                .task("corrmatrix", CorrMatrixKernel::run, subMatrixA, NUM_SUB_ROWS, subMatrixB, NUM_SUB_ROWS, matrixA_numLongs, subResultMatrix).streamOut(subResultMatrix);

        try {
            for (int a = 0; a < numSubBlocksA; a++) {
                for (int b = 0; b < numSubBlocksB; b++) {
                    final int aSubRowStart = a * NUM_SUB_ROWS;
                    final int aSubRowEnd = Math.min(matrixA_numTerms, aSubRowStart + NUM_SUB_ROWS);

                    for (int i = aSubRowStart; i < aSubRowEnd; i++) {
                        if (matrixA_numLongs != matrixA[i].length) {
                            throw new Exception("All rows in the matrix need be the same length");
                        }

                        System.arraycopy(matrixA[i], 0, subMatrixA, (i - aSubRowStart) * matrixA_numLongs, matrixA_numLongs);
                    }

                    final int bSubRowStart = b * NUM_SUB_ROWS;
                    final int bSubRowEnd = Math.min(matrixB_numTerms, bSubRowStart + NUM_SUB_ROWS);

                    for (int i = bSubRowStart; i < bSubRowEnd; i++) {
                        if (matrixA_numLongs != matrixB[i].length) {
                            throw new Exception("All rows in the matrix need be the same length");
                        }
                        System.arraycopy(matrixB[i], 0, subMatrixB, (i - bSubRowStart) * matrixB_numLongs, matrixB_numLongs);
                    }

                    // Since matrixA_NumLongs == matrixB_NumLongs we're only
                    // going to pass matrixA_NumLongs
                    s0.execute();

                    // Convert one dimensional array to two dimensional array in
                    // the expected output ordering
                    for (int i = 0; i < NUM_SUB_ROWS; i++) {
                        if ((i + aSubRowStart) < aSubRowEnd) {
                            System.arraycopy(subResultMatrix, i * NUM_SUB_ROWS, resultMatrix[i + aSubRowStart], bSubRowStart, bSubRowEnd - bSubRowStart);
                        }
                    }
                }
            }
        } catch (TornadoRuntimeException e) {
            e.printStackTrace();
        } finally {

            s0.dumpTimes();
            s0.dumpProfiles();
            s0.dumpEvents();
        }
        return resultMatrix;
    }

    /**
     * Very nice means to convert byte sizes into human readable format<br>
     * Based on code from http://stackoverflow.com/questions/3758606/how-to-convert
     * -byte-size-into-human-readable-format-in-java
     * <p>
     *
     * @param bytes
     * @param si
     *
     * @return humanReadableByteCount
     */
    private static String humanReadableByteCount(long bytes, boolean si) {
        final int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        final String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");

        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
