package uk.ac.manchester.tornado.unittests.autotune;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import uk.ac.manchester.tornado.api.AutoTuneConfig;
import uk.ac.manchester.tornado.api.enums.AutoTuneMetric;
import uk.ac.manchester.tornado.api.enums.AutoTuneStrategyType;

public class TestAutoTuneConfig {

    private static AutoTuneConfig with(int warmup, int measured, AutoTuneMetric metric, AutoTuneStrategyType strategy, long budget, long[] candidates) {
        return new AutoTuneConfig(warmup, measured, metric, strategy, budget, false, candidates);
    }

    @Test
    public void testDefaults() {
        AutoTuneConfig c = new AutoTuneConfig();
        assertEquals(2, c.getWarmupIterations());
        assertEquals(5, c.getMeasuredIterations());
        assertEquals(AutoTuneMetric.KERNEL_TIME, c.getMetric());
        assertEquals(AutoTuneStrategyType.EXHAUSTIVE, c.getStrategy());
        assertEquals(0, c.getTimeBudgetMillis());
        assertNull(c.getCustomCandidates1D());
    }

    @Test
    public void testRejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> with(-1, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, null));
        assertThrows(IllegalArgumentException.class, () -> with(0, 0, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, null));
        assertThrows(IllegalArgumentException.class, () -> with(0, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, -1, null));
    }

    @Test
    public void testRejectsNullMetricAndStrategy() {
        assertThrows(NullPointerException.class, () -> with(0, 1, null, AutoTuneStrategyType.EXHAUSTIVE, 0, null));
        assertThrows(NullPointerException.class, () -> with(0, 1, AutoTuneMetric.KERNEL_TIME, null, 0, null));
    }

    @Test
    public void testRejectsInvalidCandidates() {
        assertThrows(IllegalArgumentException.class, () -> with(0, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, new long[] { 32, 0 }));
        assertThrows(IllegalArgumentException.class, () -> with(0, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, new long[] { -5 }));
        assertThrows(IllegalArgumentException.class, () -> with(0, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, new long[] { (long) Integer.MAX_VALUE + 1 }));
    }

    @Test
    public void testCandidatesAreCopiedDefensively() {
        long[] input = { 32, 64 };
        AutoTuneConfig c = with(0, 1, AutoTuneMetric.KERNEL_TIME, AutoTuneStrategyType.EXHAUSTIVE, 0, input);
        input[0] = 999;
        assertArrayEquals(new long[] { 32, 64 }, c.getCustomCandidates1D());
        c.getCustomCandidates1D()[0] = 999;
        assertArrayEquals(new long[] { 32, 64 }, c.getCustomCandidates1D());
    }
}
