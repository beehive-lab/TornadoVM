package uk.ac.manchester.tornado.unittests.autotune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.AutoTuneConfig;
import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.enums.AutoTuneMetric;
import uk.ac.manchester.tornado.api.enums.AutoTuneStrategyType;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.enums.TornadoVMBackendType;
import uk.ac.manchester.tornado.api.exceptions.TornadoExecutionPlanException;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestAutoTune extends TornadoTestBase {

    // 4 candidates (default, 32, 64, 128) are benchmarked at least once each.
    private static final int MIN_TUNING_RUNS = 4;

    public static void vectorAdd(FloatArray a, FloatArray b, FloatArray c) {
        for (@Parallel int i = 0; i < c.getSize(); i++) {
            c.set(i, a.get(i) + b.get(i));
        }
    }

    public static void increment(IntArray x) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            x.set(i, x.get(i) + 1);
        }
    }

    public static void addTwo(IntArray x) {
        for (@Parallel int i = 0; i < x.getSize(); i++) {
            x.set(i, x.get(i) + 2);
        }
    }

    public static void incrementKernelContext(KernelContext context, IntArray x) {
        int i = context.globalIdx;
        if (i < x.getSize()) {
            x.set(i, x.get(i) + 1);
        }
    }

    public static void reduceAdd(IntArray input, @Reduce IntArray result) {
        result.set(0, 0);
        for (@Parallel int i = 0; i < input.getSize(); i++) {
            result.set(0, result.get(0) + input.get(i));
        }
    }

    public static void matmul(FloatArray a, FloatArray b, FloatArray c, int n, int m, int k) {
        for (@Parallel int i = 0; i < n; i++) {
            for (@Parallel int j = 0; j < m; j++) {
                float sum = 0.0f;
                for (int l = 0; l < k; l++) {
                    sum += a.get(i * k + l) * b.get(l * m + j);
                }
                c.set(i * m + j, sum);
            }
        }
    }

    private static boolean isMetal() {
        return getTornadoRuntime().getDefaultDevice().getTornadoVMBackend() == TornadoVMBackendType.METAL;
    }

    private static AutoTuneConfig quickConfig() {
        return new AutoTuneConfig(0, 1, AutoTuneMetric.END_TO_END_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, false, new long[] { 32, 64, 128 });
    }

    private static void assertTunerRan(int runs) {
        if (isMetal()) {
            assertTrue(runs >= MIN_TUNING_RUNS, "expected the tuner to benchmark candidates, but the graph ran " + runs + " time(s)");
        }
    }

    private static IntArray zeros(int n) {
        IntArray x = new IntArray(n);
        x.init(0);
        return x;
    }

    private static int min(IntArray x) {
        int m = Integer.MAX_VALUE;
        for (int i = 0; i < x.getSize(); i++) {
            m = Math.min(m, x.get(i));
        }
        return m;
    }

    private static int max(IntArray x) {
        int m = Integer.MIN_VALUE;
        for (int i = 0; i < x.getSize(); i++) {
            m = Math.max(m, x.get(i));
        }
        return m;
    }

    private static TaskGraph countingGraph(IntArray x) {
        return new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", TestAutoTune::increment, x) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
    }

    /*
     * Sizes: the cache is static and shared by all tests, keyed by device, task id, kernel and global size. Every test that
     * tunes the same kernel therefore needs its own global size. Sizes up to 1024 are not padded by the Metal scheduler, so
     * each one is its own key. A prime above 1024 is padded to a multiple of 256 and would share a key with its neighbours.
     */

    /** Graph runs seen after each execute(); step 2 uses the +2 kernel. Also checks that every run covered all elements. */
    private static int[] runsAfterEachExecute(int size, int step, int executions, AutoTuneConfig config) throws TornadoExecutionPlanException {
        IntArray x = zeros(size);
        TaskGraph graph = new TaskGraph("s0").transferToDevice(DataTransferMode.FIRST_EXECUTION, x);
        graph = (step == 2) ? graph.task("t0", TestAutoTune::addTwo, x) : graph.task("t0", TestAutoTune::increment, x);
        graph = graph.transferToHost(DataTransferMode.EVERY_EXECUTION, x);
        int[] runs = new int[executions];
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            if (config != null) {
                plan.withAutoTune(config);
            }
            for (int e = 0; e < executions; e++) {
                plan.execute();
                assertEquals(min(x), max(x), "a graph run did not cover every element");
                runs[e] = max(x) / step;
            }
        }
        return runs;
    }

    @Test
    public void testVectorAddIsCorrectWhenAutoTuned() throws TornadoExecutionPlanException {
        final int size = 1031;
        FloatArray a = new FloatArray(size);
        FloatArray b = new FloatArray(size);
        FloatArray c = new FloatArray(size);
        for (int i = 0; i < size; i++) {
            a.set(i, i);
            b.set(i, 2 * i);
        }
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestAutoTune::vectorAdd, a, b, c) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withAutoTune(quickConfig());
            plan.execute();
            for (int i = 0; i < size; i++) {
                assertEquals(3.0f * i, c.get(i), 0.001f);
            }
            c.init(0.0f);
            plan.execute(); // after tuning
            for (int i = 0; i < size; i++) {
                assertEquals(3.0f * i, c.get(i), 0.001f);
            }
        }
    }

    @Test
    public void testMatrixMultiplyIsCorrectWhenAutoTuned() throws TornadoExecutionPlanException {
        final int n = 37;
        final int m = 29;
        final int k = 16; // none of these divide a typical local size
        FloatArray a = new FloatArray(n * k);
        FloatArray b = new FloatArray(k * m);
        FloatArray c = new FloatArray(n * m);
        for (int i = 0; i < a.getSize(); i++) {
            a.set(i, (i % 7) * 0.5f);
        }
        for (int i = 0; i < b.getSize(); i++) {
            b.set(i, (i % 5) * 0.25f);
        }
        c.init(0.0f);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                .task("t0", TestAutoTune::matmul, a, b, c, n, m, k) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, c);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            // The 2D search space is large, so the time budget cuts it short.
            plan.withAutoTune(new AutoTuneConfig(0, 1, AutoTuneMetric.END_TO_END_TIME, AutoTuneStrategyType.EXHAUSTIVE, 2_000, false, null));
            plan.execute();
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                float expected = 0.0f;
                for (int l = 0; l < k; l++) {
                    expected += a.get(i * k + l) * b.get(l * m + j);
                }
                assertEquals(expected, c.get(i * m + j), 1e-3f);
            }
        }
    }

    @Test
    public void testTuningRunsAndCoversAllData() throws TornadoExecutionPlanException {
        int[] runs = runsAfterEachExecute(1013, 1, 1, quickConfig());
        assertTunerRan(runs[0]);
    }

    @Test
    public void testKernelTimeMetricTunes() throws TornadoExecutionPlanException {
        // Guards against the profiler producing all-zero kernel timings.
        AutoTuneConfig config = new AutoTuneConfig(0, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, false, new long[] { 32, 64, 128 });
        int[] runs = runsAfterEachExecute(1009, 1, 1, config);
        assertTunerRan(runs[0]);
    }

    @Test
    public void testUserGridDoesNotDisableTuning() throws TornadoExecutionPlanException {
        final int size = 1063;
        IntArray x = zeros(size);
        WorkerGrid1D worker = new WorkerGrid1D(size);
        worker.setLocalWork(8, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(countingGraph(x).snapshot())) {
            plan.withGridScheduler(new GridScheduler("s0.t0", worker));
            plan.withAutoTune(quickConfig());
            plan.execute();
            assertEquals(min(x), max(x));
            assertTunerRan(max(x));
        }
    }

    // ---- gating: when tuning must NOT happen ----

    @Test
    public void testNoTuningWithoutWithAutoTune() throws TornadoExecutionPlanException {
        int[] runs = runsAfterEachExecute(1069, 1, 1, null);
        assertEquals(1, runs[0]);
    }

    @Test
    public void testKernelContextTaskIsNotTuned() throws TornadoExecutionPlanException {
        final int size = 1000;
        IntArray x = zeros(size);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, x) //
                .task("t0", TestAutoTune::incrementKernelContext, new KernelContext(), x) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, x);
        WorkerGrid1D worker = new WorkerGrid1D(size);
        worker.setLocalWork(100, 1, 1);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withGridScheduler(new GridScheduler("s0.t0", worker));
            plan.withAutoTune(quickConfig());
            plan.execute();
            assertEquals(1, max(x));
            assertEquals(1, min(x));
        }
    }

    @Test
    public void testReductionStaysCorrectWithAutoTune() throws TornadoExecutionPlanException {
        IntArray input = new IntArray(1024);
        input.init(1);
        IntArray result = new IntArray(1);
        TaskGraph graph = new TaskGraph("s0") //
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, input) //
                .task("t0", TestAutoTune::reduceAdd, input, result) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, result);
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            plan.withAutoTune(quickConfig());
            plan.execute();
        }
        assertEquals(1024, result.get(0));
    }

    // ---- cache and run counts ----

    @Test
    public void testLaterExecutesRunTheGraphOnce() throws TornadoExecutionPlanException {
        int[] runs = runsAfterEachExecute(997, 1, 3, quickConfig());
        assertTunerRan(runs[0]);
        // The probe happens only on the first execute; afterwards the cached size is used and the graph runs once.
        assertEquals(1, runs[1] - runs[0], "second execute() should run the graph exactly once");
        assertEquals(1, runs[2] - runs[1], "third execute() should run the graph exactly once");
    }

    @Test
    public void testCacheServesANewPlanWithTheSameKernelAndSize() throws TornadoExecutionPlanException {
        // 1049 is a prime above 1024, so the Metal scheduler pads its default launch (global 1280). This checks that the
        // cache key survives that padding. No other test using 'increment' may use a size from 1025 to 1280.
        int[] first = runsAfterEachExecute(1049, 1, 1, quickConfig());
        int[] second = runsAfterEachExecute(1049, 1, 1, quickConfig());
        assertTunerRan(first[0]);
        // A new graph has no global work size yet, so it does one probe run plus the real run, and no search.
        assertTrue(second[0] <= 2, "a cached kernel/size was benchmarked again (" + second[0] + " runs)");
    }

    @Test
    public void testDifferentKernelSameNamesIsTunedSeparately() throws TornadoExecutionPlanException {
        runsAfterEachExecute(991, 1, 1, quickConfig());
        int[] other = runsAfterEachExecute(991, 2, 1, quickConfig());
        assertTunerRan(other[0]);
    }

    @Test
    public void testDifferentGlobalSizeIsTunedSeparately() throws TornadoExecutionPlanException {
        runsAfterEachExecute(983, 1, 1, quickConfig());
        int[] other = runsAfterEachExecute(977, 1, 1, quickConfig());
        assertTunerRan(other[0]);
    }

    // ---- time budget ----

    @Test
    public void testTimeBudgetStopsTheSearchEarly() throws TornadoExecutionPlanException {
        AutoTuneConfig config = new AutoTuneConfig(1, 3, AutoTuneMetric.END_TO_END_TIME, AutoTuneStrategyType.EXHAUSTIVE, 50, false, null);
        int[] runs = runsAfterEachExecute(971, 1, 1, config);
        if (isMetal()) {
            assertTrue(runs[0] > 2 && runs[0] < 2000, "budgeted search ran the graph " + runs[0] + " times (a full search is about 4100)");
        }
    }
}