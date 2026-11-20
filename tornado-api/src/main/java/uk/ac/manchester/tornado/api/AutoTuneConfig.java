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
package uk.ac.manchester.tornado.api;


import uk.ac.manchester.tornado.api.enums.AutoTuneMetric;
import uk.ac.manchester.tornado.api.enums.AutoTuneStrategyType;

/**
 * Settings for local work-group size auto-tuning, passed to
 * {@link TornadoExecutionPlan#withAutoTune(AutoTuneConfig)}. Each candidate local size is run
 * {@code warmupIterations} times unmeasured, then {@code measuredIterations} times measured; the median
 * of the measured values is its score and the lowest score wins. The backend's default scheduler is
 * always the first candidate, so it wins ties.
 *
 * <p>
 * The no-argument constructor uses 2 warm-up and 5 measured iterations, {@link AutoTuneMetric#KERNEL_TIME},
 * {@link AutoTuneStrategyType#EXHAUSTIVE}, no time budget, no output verification and no custom
 * candidates. Instances are immutable.
 * </p>
 */
public final class AutoTuneConfig {
    private final int    warmupIterations;
    private final int    measuredIterations;
    private final AutoTuneMetric metric;
    private final AutoTuneStrategyType strategy;
    private final long timeBudgetMillis;
    private final boolean verifyOutput;
    private final long[]  customCandidates1D;
    /**
     * @param warmupIterations
     *     unmeasured runs of the task-graph per candidate. Must be {@code >= 0}.
     * @param measuredIterations
     *     measured runs of the task-graph per candidate. Must be {@code > 0}.
     * @param metric
     *     the quantity to measure and minimise.
     * @param strategy
     *     the candidate-generation algorithm.
     * @param timeBudgetMillis
     *     maximum time for the whole tuning run, in milliseconds, or {@code 0} for no limit. It is checked
     *     before each candidate; once spent, the best candidate measured so far is selected and tasks not
     *     yet started keep their configuration.
     * @param verifyOutput
     *     accepted but currently has no effect.
     * @param customCandidates1D
     *     local sizes to try for 1D tasks instead of the strategy's candidates, or {@code null} to use the
     *     strategy's. The backend default and the task's current local size are still tried. Ignored for
     *     2D and 3D tasks. Each entry must be between 1 and {@code Integer.MAX_VALUE}.
     * @throws NullPointerException
     *     if {@code metric} or {@code strategy} is {@code null}.
     * @throws IllegalArgumentException
     *     if a count, the time budget or a custom candidate is outside the range above.
     */
    public AutoTuneConfig(int warmupIterations,int measuredIterations,AutoTuneMetric metric,AutoTuneStrategyType strategy, long timeBudgetMillis,boolean verifyOutput,long [] customCandidates1D){
        if (warmupIterations < 0) {
            throw new IllegalArgumentException(
                    "warmupIterations must be >= 0");
        }
        if (measuredIterations <= 0) {
            throw new IllegalArgumentException(
                    "measuredIterations must be > 0");
        }
        if (metric == null) {
            throw new NullPointerException(
                    "metric must not be null");
        }
        if (strategy == null) {
            throw new NullPointerException(
                    "strategy must not be null");
        }
        if (timeBudgetMillis < 0) {
            throw new IllegalArgumentException(
                    "timeBudgetMillis must be >= 0");
        }
        if (customCandidates1D != null) {
        for (long candidate : customCandidates1D) {
            if (candidate < 1 || candidate > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("customCandidates1D entries must be between 1 and " + Integer.MAX_VALUE);
            }
        }
    }
        this.warmupIterations = warmupIterations;
        this.measuredIterations=measuredIterations;
        this.metric=metric;
        this.strategy=strategy;
        this.timeBudgetMillis=timeBudgetMillis;
        this.verifyOutput=verifyOutput;
        this.customCandidates1D = (customCandidates1D == null) ? null : customCandidates1D.clone();
    }
    
    /** Creates a configuration with the default values listed in the class Javadoc. */
    public AutoTuneConfig() {
        this(
            2,
            5,
            AutoTuneMetric.KERNEL_TIME,
            AutoTuneStrategyType.EXHAUSTIVE,
            0,
            false,
            null
        );
    }

    public int getWarmupIterations() {
        return this.warmupIterations;
    }

    public int getMeasuredIterations() {
        return this.measuredIterations;
    }

    public AutoTuneMetric getMetric() {
        return this.metric;
    }

    public AutoTuneStrategyType getStrategy() {
        return this.strategy;
    }

    public long getTimeBudgetMillis() {
        return this.timeBudgetMillis;
    }

    /** Returns {@code true} if output verification was requested. It currently has no effect. */
    public boolean isVerifyOutput() {
        return this.verifyOutput;
    }
    /** Returns a copy of the custom 1D candidates, or {@code null} if none were set. */
    public long[] getCustomCandidates1D() {
        return (this.customCandidates1D == null) ? null : this.customCandidates1D.clone();
    }
}
