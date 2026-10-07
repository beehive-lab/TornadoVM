/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.unittests.cudf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.cudf.Cudf;
import uk.ac.manchester.tornado.cudf.enums.CudfType;
import uk.ac.manchester.tornado.cudf.provider.CudfLibraryProvider;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;
import uk.ac.manchester.tornado.unittests.common.TornadoVMCUDANotSupported;

/**
 * Typed, nullable Parquet reads and set membership: the two cuDF primitives a lakehouse scan needs
 * that a group-by does not -- 64-bit keys, optional columns, and an anti-join that yields a mask.
 *
 * <p>
 * The fixture {@code mixed-types.parquet} is ten rows in three row groups (4, 4, 2), written by
 * pyarrow:
 *
 * <pre>
 *   id  int64  required   10_000_000_000 + 3i
 *   k   int32  required   i % 3
 *   v   double required   0.5i
 *   d   date32 optional   20454 + i days, null when i % 4 == 1
 *   ts  timestamp[us] required   1_767_225_600_000_000 + 1_000_001i
 *   opt int64  optional   -7i, null when i % 3 == 0
 * </pre>
 *
 * <p>
 * How to run:
 *
 * <code>
 * tornado-test -V uk.ac.manchester.tornado.unittests.cudf.TestCudfColumns
 * </code>
 */
public class TestCudfColumns extends TornadoTestBase {

    private static final int ROWS = 10;
    private static final int ID = 0;
    private static final int K = 1;
    private static final int V = 2;
    private static final int D = 3;
    private static final int TS = 4;
    private static final int OPT = 5;

    private static Path fixture;

    @Before
    public void cudfMustBeAvailable() throws IOException {
        if (!CudfLibraryProvider.isAvailable() || !Cudf.isColumnsAvailable()) {
            throw new TornadoVMCUDANotSupported("the cuDF shim (libtornado-cudf.so) with readParquetColumns is not built on this host");
        }
        TornadoVMBackendType backendType = getTornadoRuntime().getDefaultDevice().getTornadoVMBackend();
        if (backendType != TornadoVMBackendType.CUDA) {
            String message = "cuDF library tasks require the CUDA backend (default device is " + backendType + ")";
            switch (backendType) {
                case OPENCL, METAL -> assertNotBackend(backendType, message);
                default -> throw new TornadoVMCUDANotSupported(message);
            }
        }
        if (fixture == null) {
            // cuDF reads a path, not a stream, so the resource is materialised once per run.
            Path file = Files.createTempFile("tornado-cudf-mixed-types", ".parquet");
            file.toFile().deleteOnExit();
            try (InputStream in = TestCudfColumns.class.getResourceAsStream("mixed-types.parquet")) {
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            }
            fixture = file;
        }
    }

    private static long id(int i) {
        return 10_000_000_000L + 3L * i;
    }

    private static boolean dIsNull(int i) {
        return i % 4 == 1;
    }

    private static boolean optIsNull(int i) {
        return i % 3 == 0;
    }

    /** Reads what cuDF wrote, through the validity bytes it wrote beside it: a null-aware sum. */
    public static void sumValid(LongArray values, ByteArray valid, int offset, int n, LongArray out) {
        for (@Parallel int i = 0; i < n; i++) {
            out.set(i, valid.get(offset + i) == 1 ? values.get(n + i) : 0L);
        }
    }

    /** Clears every row whose key is in the set, which is an equality delete as a kernel sees one. */
    public static void keep(ByteArray inSet, ByteArray keep) {
        for (@Parallel int i = 0; i < keep.getSize(); i++) {
            keep.set(i, inSet.get(i) == 1 ? (byte) 0 : (byte) 1);
        }
    }

    /** Derives keys on the device, so a probe reads what a kernel wrote. */
    public static void tripleKeys(LongArray in, LongArray out) {
        for (@Parallel int i = 0; i < in.getSize(); i++) {
            out.set(i, in.get(i) * 3L);
        }
    }

    /**
     * Every physical type the reader takes, nullable and not, in one read: two INT64 columns
     * where one is required and one optional, an INT32 and a DATE, a TIMESTAMP as INT64, and FP64.
     */
    @Test
    public void testReadParquetColumnsMixedTypes() throws TornadoExecutionPlanException {
        int stride = 16; // larger than the file, as a buffer sized for the biggest of many files is
        int[] columns = { ID, K, V, D, TS, OPT };
        CudfType[] types = { CudfType.INT64, CudfType.INT32, CudfType.FLOAT64, CudfType.INT32, CudfType.INT64, CudfType.INT64 };
        IntArray ints = new IntArray(2 * stride);
        LongArray longs = new LongArray(3 * stride);
        DoubleArray doubles = new DoubleArray(stride);
        ByteArray valid = new ByteArray(columns.length * stride);

        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(fixture.toString()), 0, 0, columns, types, new long[] { ROWS }, stride, ints, longs, doubles,
                        valid, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, ints, longs, doubles, valid);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }

        for (int i = 0; i < ROWS; i++) {
            assertEquals("id " + i, id(i), longs.get(i));
            assertEquals("k " + i, i % 3, ints.get(i));
            assertEquals("v " + i, 0.5 * i, doubles.get(i), 0.0);
            assertEquals("ts " + i, 1_767_225_600_000_000L + 1_000_001L * i, longs.get(stride + i));
            assertEquals("d validity " + i, dIsNull(i) ? 0 : 1, valid.get(3 * stride + i));
            if (!dIsNull(i)) {
                assertEquals("d " + i, 20454 + i, ints.get(stride + i));
            }
            assertEquals("opt validity " + i, optIsNull(i) ? 0 : 1, valid.get(5 * stride + i));
            if (!optIsNull(i)) {
                assertEquals("opt " + i, -7L * i, longs.get(2 * stride + i));
            }
            for (int required : new int[] { 0, 1, 2, 4 }) {
                assertEquals("a required column is valid everywhere: column " + required + " row " + i, 1, valid.get(required * stride + i));
            }
        }
    }

    /** A row-group range lands at the start of the buffers. */
    @Test
    public void testReadParquetColumnsRowGroupRange() throws TornadoExecutionPlanException {
        int stride = 8;
        LongArray longs = new LongArray(stride);

        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(fixture.toString()), 1, 2, new int[] { ID }, new CudfType[] { CudfType.INT64 }, new long[] { 6 },
                        stride, new IntArray(1), longs, new DoubleArray(1), new ByteArray(1), false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < 6; i++) {
            assertEquals("row " + (4 + i) + " of the file", id(4 + i), longs.get(i));
        }
    }

    /**
     * A caller that sized for a different row count than the range holds is told, rather than
     * handed a buffer that is part stale: the row count is what every later task trusts.
     */
    @Test
    public void testReadParquetColumnsRefusesWrongRowCount() {
        LongArray longs = new LongArray(ROWS);
        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(fixture.toString()), 0, 0, new int[] { ID }, new CudfType[] { CudfType.INT64 },
                        new long[] { ROWS - 1 }, ROWS, new IntArray(1), longs, new DoubleArray(1), new ByteArray(1), false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            fail("a read of " + ROWS + " rows into a caller expecting " + (ROWS - 1) + " must not report success");
        } catch (Exception expected) {
            assertTrue("the refusal should say why; got: " + causeChain(expected), causeChain(expected).contains("rows where the caller asked"));
        }
    }

    /** Several files in one read: their rows concatenated in order, validity included. */
    @Test
    public void testReadParquetColumnsSeveralFiles() throws TornadoExecutionPlanException {
        int stride = 3 * ROWS;
        LongArray longs = new LongArray(2 * stride);
        ByteArray valid = new ByteArray(2 * stride);
        String three = fixture + "\n" + fixture + "\n" + fixture;

        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(three), 0, 0, new int[] { ID, OPT }, new CudfType[] { CudfType.INT64, CudfType.INT64 },
                        new long[] { 3 * ROWS }, stride, new IntArray(1), longs, new DoubleArray(1), valid, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs, valid);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int copy = 0; copy < 3; copy++) {
            for (int i = 0; i < ROWS; i++) {
                int row = copy * ROWS + i;
                assertEquals("id, copy " + copy + " row " + i, id(i), longs.get(row));
                assertEquals("opt validity, copy " + copy + " row " + i, optIsNull(i) ? 0 : 1, valid.get(stride + row));
                if (!optIsNull(i)) {
                    assertEquals("opt, copy " + copy + " row " + i, -7L * i, longs.get(stride + row));
                }
            }
        }
    }

    /** An empty holder and no rows: a no-op that leaves the buffers alone, then a real read from the same plan. */
    @Test
    public void testReadParquetColumnsEmptyHolderReadsNothing() throws TornadoExecutionPlanException {
        LongArray longs = new LongArray(ROWS);
        StringBuilder path = new StringBuilder();
        long[] rows = { 0 };
        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, path, 0, 0, new int[] { ID }, new CudfType[] { CudfType.INT64 }, rows, ROWS, new IntArray(1), longs,
                        new DoubleArray(1), new ByteArray(1), false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            path.append(fixture);
            rows[0] = ROWS;
            plan.execute();
        }
        for (int i = 0; i < ROWS; i++) {
            assertEquals("row " + i, id(i), longs.get(i));
        }
    }

    /** A column with nulls, read without asking for validity, is refused rather than read as dense. */
    @Test
    public void testReadParquetColumnsRefusesNullsWithoutValidity() {
        assertRefused(new int[] { OPT }, new CudfType[] { CudfType.INT64 }, false, "nulls");
    }

    /** An INT32 column requested as INT64 is refused, not widened. */
    @Test
    public void testReadParquetColumnsRefusesTypeMismatch() {
        assertRefused(new int[] { K }, new CudfType[] { CudfType.INT64 }, true, "does not cast");
    }

    private static void assertRefused(int[] columns, CudfType[] types, boolean nullable, String reason) {
        LongArray longs = new LongArray(ROWS);
        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(fixture.toString()), 0, 0, columns, types, new long[] { ROWS }, ROWS, new IntArray(1), longs,
                        new DoubleArray(1), new ByteArray(ROWS), nullable) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            fail("the read should have been refused (" + reason + ")");
        } catch (Exception expected) {
            assertTrue("the refusal should say why; got: " + causeChain(expected), causeChain(expected).contains(reason));
        }
    }

    /** cuDF reads, a generated kernel consumes the column and its validity from the same buffers. */
    @Test
    public void testReadParquetColumnsThenKernel() throws TornadoExecutionPlanException {
        int[] columns = { ID, OPT };
        CudfType[] types = { CudfType.INT64, CudfType.INT64 };
        LongArray longs = new LongArray(2 * ROWS);
        ByteArray valid = new ByteArray(2 * ROWS);
        LongArray out = new LongArray(ROWS);

        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(fixture.toString()), 0, 0, columns, types, new long[] { ROWS }, ROWS, new IntArray(1), longs,
                        new DoubleArray(1), valid, true) //
                .task("sum", TestCudfColumns::sumValid, longs, valid, ROWS, ROWS, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < ROWS; i++) {
            assertEquals("row " + i, optIsNull(i) ? 0L : -7L * i, out.get(i));
        }
    }

    /** INT64 keys against an INT64 set, checked row by row against a host set. */
    @Test
    public void testContainedInLong() throws TornadoExecutionPlanException {
        Random random = new Random(7);
        int n = 50_000;
        int m = 4_000;
        LongArray keys = new LongArray(n);
        LongArray set = new LongArray(m);
        Set<Long> host = new HashSet<>();
        for (int j = 0; j < m; j++) {
            long value = (random.nextLong() & 0xFFFFFL) + (1L << 40);
            set.set(j, value);
            host.add(value);
        }
        for (int i = 0; i < n; i++) {
            keys.set(i, (random.nextLong() & 0xFFFFFL) + (1L << 40));
        }
        ByteArray mask = new ByteArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, set) //
                .libraryTask("probe", Cudf::containedIn, new int[] { n, m }, keys, set, mask) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, mask);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        int hits = 0;
        for (int i = 0; i < n; i++) {
            boolean expected = host.contains(keys.get(i));
            assertEquals("key " + i, expected ? 1 : 0, mask.get(i));
            hits += expected ? 1 : 0;
        }
        assertTrue("the data should exercise both answers", hits > 0 && hits < n);
    }

    /** INT32 keys, with a generated kernel after the probe turning membership into a keep mask. */
    @Test
    public void testContainedInIntThenKernel() throws TornadoExecutionPlanException {
        int n = 4096;
        IntArray keys = new IntArray(n);
        IntArray set = new IntArray(3);
        for (int i = 0; i < n; i++) {
            keys.set(i, i % 100);
        }
        set.set(0, 5);
        set.set(1, 50);
        set.set(2, 1_000);
        ByteArray inSet = new ByteArray(n);
        ByteArray keep = new ByteArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, keys, set) //
                .libraryTask("probe", Cudf::containedIn, new int[] { n, 3 }, keys, set, inSet) //
                .task("keep", TestCudfColumns::keep, inSet, keep) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, keep);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < n; i++) {
            int key = i % 100;
            assertEquals("row " + i, key == 5 || key == 50 ? 0 : 1, keep.get(i));
        }
    }

    /**
     * One plan, a different set every execution: the sizes are read when the task runs, so a
     * shrinking set and then an empty one both answer correctly from the same graph.
     */
    @Test
    public void testContainedInSizesChangeBetweenExecutions() throws TornadoExecutionPlanException {
        int n = 1024;
        LongArray base = new LongArray(n);
        for (int i = 0; i < n; i++) {
            base.set(i, i);
        }
        LongArray keys = new LongArray(n);
        LongArray set = new LongArray(n);
        for (int j = 0; j < n; j++) {
            set.set(j, 3L * j); // every key tripleKeys produces, so the set size decides the answer
        }
        int[] sizes = { n, n };
        ByteArray mask = new ByteArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, base, set) //
                .task("triple", TestCudfColumns::tripleKeys, base, keys) //
                .libraryTask("probe", Cudf::containedIn, sizes, keys, set, mask) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, mask);

        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            for (int m : new int[] { n, 100, 0 }) {
                sizes[1] = m;
                plan.execute();
                for (int i = 0; i < n; i++) {
                    assertEquals("set of " + m + ", key " + 3L * i, i < m ? 1 : 0, mask.get(i));
                }
            }
        }
    }

    private static String causeChain(Throwable t) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = t; current != null; current = current.getCause()) {
            chain.append(current.getMessage()).append(' ');
        }
        return chain.toString();
    }
}
