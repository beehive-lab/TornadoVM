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
import static org.junit.Assume.assumeTrue;

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
import uk.ac.manchester.tornado.cudf.enums.ParquetColumnType;
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
        if (!CudfLibraryProvider.isAvailable() || !Cudf.isColumnsAvailable() || !Cudf.isWriterAvailable() || !Cudf.isSortKeysAvailable()) {
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

    /**
     * A path holder that grows between executions of one plan. The holder is read on the host, and
     * a library task's text arguments are passed by reference rather than serialised to the
     * device, so growing it -- which reallocates its array -- has to be harmless.
     */
    @Test
    public void testPathHolderGrowsBetweenExecutions() throws TornadoExecutionPlanException {
        int copies = 20;
        int stride = copies * ROWS;
        LongArray longs = new LongArray(stride);
        StringBuilder path = new StringBuilder(fixture.toString());
        long[] rows = { ROWS };
        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, path, 0, 0, new int[] { ID }, new CudfType[] { CudfType.INT64 }, rows, stride, new IntArray(1), longs,
                        new DoubleArray(1), new ByteArray(1), false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, longs);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            for (int c = 1; c < copies; c++) {
                path.append('\n').append(fixture);
            }
            rows[0] = (long) copies * ROWS;
            plan.execute();
        }
        for (int c = 0; c < copies; c++) {
            for (int i = 0; i < ROWS; i++) {
                assertEquals("copy " + c + " row " + i, id(i), longs.get(c * ROWS + i));
            }
        }
    }

    /** Keeps every third row: the predicate half of a compaction, as a kernel. */
    public static void everyThird(LongArray ids, ByteArray keep) {
        for (@Parallel int i = 0; i < keep.getSize(); i++) {
            keep.set(i, (byte) (ids.get(i) % 3 == 0 ? 1 : 0));
        }
    }

    /** Moves the kept rows of two columns to the front. */
    public static void gatherKept(IntArray positions, IntArray count, LongArray ids, DoubleArray values, LongArray outIds, DoubleArray outValues) {
        for (@Parallel int j = 0; j < positions.getSize(); j++) {
            if (j < count.get(0)) {
                outIds.set(j, ids.get(positions.get(j)));
                outValues.set(j, values.get(positions.get(j)));
            }
        }
    }

    /**
     * Every type the writer takes, required and optional, written from device buffers and read back:
     * values, nulls and types survive the round trip.
     */
    @Test
    public void testWriteParquetColumnsRoundTrip() throws TornadoExecutionPlanException, IOException {
        Path out = Files.createTempFile("tornado-cudf-written", ".parquet");
        out.toFile().deleteOnExit();
        int[] columns = { ID, K, V, D, TS, OPT };
        CudfType[] types = { CudfType.INT64, CudfType.INT32, CudfType.FLOAT64, CudfType.INT32, CudfType.INT64, CudfType.INT64 };
        IntArray ints = new IntArray(2 * ROWS);
        LongArray longs = new LongArray(3 * ROWS);
        DoubleArray doubles = new DoubleArray(ROWS);
        ByteArray valid = new ByteArray(columns.length * ROWS);
        IntArray back = new IntArray(2 * ROWS);
        LongArray backLongs = new LongArray(3 * ROWS);
        DoubleArray backDoubles = new DoubleArray(ROWS);
        ByteArray backValid = new ByteArray(columns.length * ROWS);
        String names = "id\nk\nv\nd\nts\nopt";
        ParquetColumnType[] written = { ParquetColumnType.INT64, ParquetColumnType.INT32, ParquetColumnType.FLOAT64, ParquetColumnType.DATE,
                ParquetColumnType.TIMESTAMP_MICROS, ParquetColumnType.INT64 };

        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(fixture.toString()), 0, 0, columns, types, new long[] { ROWS }, ROWS, ints, longs, doubles,
                        valid, true) //
                .libraryTask("write", Cudf::writeParquetColumns, new StringBuilder(out.toString()), names, new int[] { 1, 2, 3, 4, 5, 6 }, written,
                        new boolean[] { false, false, false, true, false, true }, new long[] { ROWS }, new IntArray(1), ROWS, ints, longs, doubles, valid, 2, 0) //
                .libraryTask("reread", Cudf::readParquetColumns, new StringBuilder(out.toString()), 0, 0, new int[] { 0, 1, 2, 3, 4, 5 }, types, new long[] { ROWS }, ROWS,
                        back, backLongs, backDoubles, backValid, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, back, backLongs, backDoubles, backValid);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < ROWS; i++) {
            assertEquals("id " + i, id(i), backLongs.get(i));
            assertEquals("k " + i, i % 3, back.get(i));
            assertEquals("v " + i, 0.5 * i, backDoubles.get(i), 0.0);
            assertEquals("ts " + i, 1_767_225_600_000_000L + 1_000_001L * i, backLongs.get(ROWS + i));
            assertEquals("d validity " + i, dIsNull(i) ? 0 : 1, backValid.get(3 * ROWS + i));
            if (!dIsNull(i)) {
                assertEquals("d " + i, 20454 + i, back.get(ROWS + i));
            }
            assertEquals("opt validity " + i, optIsNull(i) ? 0 : 1, backValid.get(5 * ROWS + i));
            if (!optIsNull(i)) {
                assertEquals("opt " + i, -7L * i, backLongs.get(2 * ROWS + i));
            }
        }
    }

    /**
     * A compaction in one plan: a kernel decides what survives, cuDF compacts, a kernel gathers, and
     * the writer takes the survivor count from the device counter selectedIndices wrote.
     */
    @Test
    public void testWriteParquetColumnsCountFromDevice() throws TornadoExecutionPlanException, IOException {
        int n = 30_000;
        Path out = Files.createTempFile("tornado-cudf-compacted", ".parquet");
        out.toFile().deleteOnExit();
        LongArray ids = new LongArray(n);
        DoubleArray values = new DoubleArray(n);
        for (int i = 0; i < n; i++) {
            ids.set(i, i);
            values.set(i, i * 0.25);
        }
        ByteArray keep = new ByteArray(n);
        IntArray positions = new IntArray(n);
        IntArray count = new IntArray(1);
        LongArray outIds = new LongArray(n);
        DoubleArray outValues = new DoubleArray(n);
        int kept = n / 3;
        LongArray backIds = new LongArray(kept);
        DoubleArray backValues = new DoubleArray(kept);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, ids, values) //
                .task("keep", TestCudfColumns::everyThird, ids, keep) //
                .libraryTask("compact", Cudf::selectedIndices, n, n, keep, positions, count) //
                .task("gather", TestCudfColumns::gatherKept, positions, count, ids, values, outIds, outValues) //
                .libraryTask("write", Cudf::writeParquetColumns, new StringBuilder(out.toString()), "id\nvalue", new int[] { 1, 2 },
                        new ParquetColumnType[] { ParquetColumnType.INT64, ParquetColumnType.FLOAT64 }, new boolean[] { false, false }, new long[] { -1 }, count, n,
                        new IntArray(1), outIds, outValues, new ByteArray(1), 1, 0) //
                .libraryTask("reread", Cudf::readParquetColumns, new StringBuilder(out.toString()), 0, 0, new int[] { 0, 1 },
                        new CudfType[] { CudfType.INT64, CudfType.FLOAT64 }, new long[] { kept }, kept, new IntArray(1), backIds, backValues, new ByteArray(1), false) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, count, backIds, backValues);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals("survivors", kept, count.get(0));
        for (int j = 0; j < kept; j++) {
            assertEquals("id " + j, 3L * j, backIds.get(j));
            assertEquals("value " + j, 0.75 * j, backValues.get(j), 0.0);
        }
    }

    /** Binary search of a sorted set: the probe a sorted set allows, as a kernel. */
    public static void probeSorted(LongArray sorted, IntArray size, LongArray keys, ByteArray found) {
        for (@Parallel int i = 0; i < keys.getSize(); i++) {
            long key = keys.get(i);
            int low = 0;
            int high = size.get(0) - 1;
            byte hit = 0;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                long value = sorted.get(mid);
                if (value < key) {
                    low = mid + 1;
                } else if (value > key) {
                    high = mid - 1;
                } else {
                    hit = 1;
                    low = high + 1;
                }
            }
            found.set(i, hit);
        }
    }

    /**
     * The set is sorted on the device once, then probed by a generated binary search on every
     * execution; the second execution sorts nothing (size 0) and still probes the sorted copy.
     */
    @Test
    public void testSortKeysThenBinarySearch() throws TornadoExecutionPlanException {
        Random random = new Random(11);
        int m = 100_000;
        int n = 200_000;
        LongArray set = new LongArray(m);
        Set<Long> host = new HashSet<>();
        for (int j = 0; j < m; j++) {
            long value = random.nextLong() >> 20;
            set.set(j, value);
            host.add(value);
        }
        LongArray keys = new LongArray(n);
        for (int i = 0; i < n; i++) {
            keys.set(i, i % 2 == 0 ? set.get(random.nextInt(m)) : random.nextLong() >> 20);
        }
        LongArray sorted = new LongArray(m);
        IntArray probeSize = IntArray.fromElements(m);
        int[] sortSize = { m };
        ByteArray found = new ByteArray(n);

        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, set, keys, probeSize) //
                .libraryTask("sort", Cudf::sortKeys, sortSize, set, sorted) //
                .task("probe", TestCudfColumns::probeSorted, sorted, probeSize, keys, found) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, found, sorted);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
            for (int j = 1; j < m; j++) {
                assertTrue("ascending at " + j, sorted.get(j - 1) <= sorted.get(j));
            }
            sortSize[0] = 0; // the set has not changed: probe the copy already sorted
            plan.execute();
        }
        for (int i = 0; i < n; i++) {
            assertEquals("key " + i, host.contains(keys.get(i)) ? 1 : 0, found.get(i));
        }
    }

    /**
     * Rows keyed by two INT64 words sort lexicographically and stably; the row count, below the
     * stride, changes between two executions of one plan.
     */
    @Test
    public void testSortedOrderLongsTwoWords() throws TornadoExecutionPlanException {
        assumeTrue(Cudf.isSortedOrderLongsAvailable());
        Random random = new Random(5);
        int stride = 100_000;
        LongArray keys = new LongArray(2L * stride);
        for (int i = 0; i < stride; i++) {
            keys.set(i, random.nextInt(50) - 25); // many ties in the first word
            keys.set(stride + i, random.nextLong());
        }
        IntArray order = new IntArray(stride);
        int[] size = { 80_000, 2 };
        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, keys) //
                .libraryTask("order", Cudf::sortedOrderLongs, size, keys, stride, order) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, order);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            for (int n : new int[] { 80_000, 30_000 }) {
                size[0] = n;
                plan.execute();
                Integer[] expected = new Integer[n];
                for (int i = 0; i < n; i++) {
                    expected[i] = i;
                }
                java.util.Arrays.sort(expected, java.util.Comparator.<Integer> comparingLong(i -> keys.get(i)).thenComparingLong(i -> keys.get(stride + i)));
                for (int i = 0; i < n; i++) {
                    assertEquals("row " + i + " of " + n, (int) expected[i], order.get(i));
                }
            }
        }
    }

    /**
     * STRING columns of two files read as one batch: the fixture {@code strings.parquet} (pyarrow,
     * ten rows) has {@code flag} ("RAN" repeated, required) and {@code name} (UTF-8, null when
     * {@code i % 4 == 1}). Offsets, bytes and validity come back as cuDF lays them out, the
     * validity after a base the caller picks.
     */
    @Test
    public void testReadParquetStringColumnsTwoFiles() throws TornadoExecutionPlanException, IOException {
        assumeTrue(Cudf.isStringColumnsAvailable());
        Path strings = Files.createTempFile("tornado-cudf-strings", ".parquet");
        strings.toFile().deleteOnExit();
        try (InputStream in = TestCudfColumns.class.getResourceAsStream("strings.parquet")) {
            Files.copy(in, strings, StandardCopyOption.REPLACE_EXISTING);
        }
        String[] names = { "é0", null, "2", "xyz3", "Ωmega4", null, "ab6", "7", "xyz8", null };
        int rows = 20;
        int stride = 24;
        long charsStride = 256;
        IntArray offsets = new IntArray(2 * (stride + 1));
        ByteArray chars = new ByteArray(2 * charsStride);
        ByteArray valid = new ByteArray(3L * stride);
        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("strings", Cudf::readParquetStringColumns, new StringBuilder(strings + "\n" + strings), new int[] { 0, 1 }, new long[] { rows }, stride, offsets,
                        chars, charsStride, valid, 1) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, offsets, chars, valid);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < rows; i++) {
            int r = i % 10;
            String flag = new String(slice(chars, 0, offsets.get(i), offsets.get(i + 1)), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("flag " + i, String.valueOf("RANRANRANR".charAt(r)), flag);
            assertEquals("flag valid " + i, 1, valid.get(stride + i));
            boolean present = names[r] != null;
            assertEquals("name valid " + i, present ? 1 : 0, valid.get(2 * stride + i));
            if (present) {
                int from = offsets.get(stride + 1 + i);
                int to = offsets.get(stride + 1 + i + 1);
                assertEquals("name " + i, names[r], new String(slice(chars, charsStride, from, to), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private static byte[] slice(ByteArray chars, long base, int from, int to) {
        byte[] bytes = new byte[to - from];
        for (int b = from; b < to; b++) {
            bytes[b - from] = chars.get((int) (base + b));
        }
        return bytes;
    }

    /** Group-by on two INT64 key columns: sums and counts per (a, b) pair match the host's. */
    @Test
    public void testGroupAggregateLongsTwoKeys() throws TornadoExecutionPlanException {
        assumeTrue(Cudf.isStringColumnsAvailable());
        Random random = new Random(9);
        int stride = 50_000;
        int n = 40_000;
        LongArray keys = new LongArray(2L * stride);
        DoubleArray values = new DoubleArray(2L * stride);
        java.util.Map<String, double[]> expected = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            long a = random.nextInt(5) - 2;
            long b = random.nextBoolean() ? Long.MIN_VALUE : random.nextInt(7) * 1_000_000_007L;
            double v = random.nextInt(1000);
            keys.set(i, a);
            keys.set(stride + i, b);
            values.set(i, v);
            values.set(stride + i, 1.0);
            double[] sums = expected.computeIfAbsent(a + "/" + b, ignored -> new double[2]);
            sums[0] += v;
            sums[1] += 1.0;
        }
        LongArray outKeys = new LongArray(2L * stride);
        DoubleArray outSums = new DoubleArray(2L * stride);
        IntArray groups = new IntArray(1);
        TaskGraph graph = new TaskGraph("cudf") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, keys, values) //
                .libraryTask("group", Cudf::groupAggregateLongs, new int[] { n, 2, 2 }, uk.ac.manchester.tornado.cudf.enums.CudfAggregation.SUM.code(), keys, values, stride,
                        outKeys, outSums, groups) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, outKeys, outSums, groups);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        assertEquals(expected.size(), groups.get(0));
        for (int g = 0; g < groups.get(0); g++) {
            double[] sums = expected.get(outKeys.get(g) + "/" + outKeys.get(stride + g));
            assertTrue("group " + g, sums != null);
            assertEquals(sums[0], outSums.get(g), 0.0);
            assertEquals(sums[1], outSums.get(stride + g), 0.0);
        }
    }

    /**
     * FLOAT32 columns read as {@code CudfType.FLOAT32} land widened in the FP64 buffer, value for
     * value as Java widens a float, nulls included: the fixture {@code floats.parquet} (pyarrow) has a
     * required {@code f} with extremes, signed zeros and -1/3, and {@code g = 2f}, null when
     * {@code i % 3 == 1}.
     */
    @Test
    public void testReadParquetColumnsFloat32Widened() throws TornadoExecutionPlanException, IOException {
        Path floats = Files.createTempFile("tornado-cudf-floats", ".parquet");
        floats.toFile().deleteOnExit();
        try (InputStream in = TestCudfColumns.class.getResourceAsStream("floats.parquet")) {
            Files.copy(in, floats, StandardCopyOption.REPLACE_EXISTING);
        }
        float[] f = { 1.5f, -2.25f, 3.4028235e38f, 1.17549435e-38f, 0.0f, -0.0f, 16777217.0f, (float) (-1.0 / 3), 1e-3f, 42.0f };
        int rows = f.length;
        DoubleArray doubles = new DoubleArray(2L * rows);
        ByteArray valid = new ByteArray(2L * rows);
        TaskGraph graph = new TaskGraph("cudf") //
                .libraryTask("read", Cudf::readParquetColumns, new StringBuilder(floats.toString()), 0, 0, new int[] { 0, 1 },
                        new CudfType[] { CudfType.FLOAT32, CudfType.FLOAT32 }, new long[] { rows }, rows, new IntArray(1), new LongArray(1), doubles, valid, true) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, doubles, valid);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.execute();
        }
        for (int i = 0; i < rows; i++) {
            assertEquals("f " + i, Double.doubleToRawLongBits(f[i]), Double.doubleToRawLongBits(doubles.get(i)));
            boolean present = i % 3 != 1;
            assertEquals("g valid " + i, present ? 1 : 0, valid.get(rows + i));
            if (present) {
                assertEquals("g " + i, Double.doubleToRawLongBits((double) (f[i] * 2)), Double.doubleToRawLongBits(doubles.get(rows + i)));
            }
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
