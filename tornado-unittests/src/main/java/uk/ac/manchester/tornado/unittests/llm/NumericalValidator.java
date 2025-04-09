package uk.ac.manchester.tornado.unittests.llm;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

public class NumericalValidator {
    // Tolerance for floating point comparisons
    private static final float DEFAULT_ABSOLUTE_TOLERANCE = 1e-5f;
    private static final float DEFAULT_RELATIVE_TOLERANCE = 1e-5f;

    /**
     * Compares two FloatArrays element-wise and reports statistics on their differences
     */
    public static ValidationResult compareArrays(FloatArray array1, FloatArray array2, String arrayName) {
        return compareArrays(array1, array2, arrayName, DEFAULT_ABSOLUTE_TOLERANCE, DEFAULT_RELATIVE_TOLERANCE);
    }

    /**
     * Compares two FloatArrays with custom tolerances
     */
    public static ValidationResult compareArrays(FloatArray array1, FloatArray array2, String arrayName,
                                                 float absoluteTolerance, float relativeTolerance) {
        if (array1.getSize() != array2.getSize()) {
            throw new IllegalArgumentException("Arrays must have the same size. " +
                    arrayName + " size1=" + array1.getSize() +
                    ", size2=" + array2.getSize());
        }

        int size = array1.getSize();
        float maxAbsDiff = 0.0f;
        float maxRelDiff = 0.0f;
        float sumSquaredDiff = 0.0f;
        int numFailures = 0;
        int maxFailureIndex = -1;

        for (int i = 0; i < size; i++) {
            float val1 = array1.get(i);
            float val2 = array2.get(i);
            float absDiff = Math.abs(val1 - val2);

            // Calculate relative difference, handling division by zero
            float relDiff = 0.0f;
            float maxMagnitude = Math.max(Math.abs(val1), Math.abs(val2));
            if (maxMagnitude > 1e-6f) {
                relDiff = absDiff / maxMagnitude;
            }

            sumSquaredDiff += absDiff * absDiff;

            if (absDiff > maxAbsDiff) {
                maxAbsDiff = absDiff;
            }

            if (relDiff > maxRelDiff) {
                maxRelDiff = relDiff;
            }

            // Check if this difference exceeds tolerances
            boolean failed = (absDiff > absoluteTolerance) && (relDiff > relativeTolerance);
            if (failed) {
                numFailures++;
                maxFailureIndex = i;
            }
        }

        float rmsDiff = (float) Math.sqrt(sumSquaredDiff / size);

        return new ValidationResult(
                arrayName,
                size,
                maxAbsDiff,
                maxRelDiff,
                rmsDiff,
                numFailures,
                maxFailureIndex,
                array1.get(Math.max(0, maxFailureIndex)),
                array2.get(Math.max(0, maxFailureIndex)),
                numFailures == 0
        );
    }

    /**
     * Compares a specific layer in the KV cache
     */
    public static ValidationResult compareKVCacheLayer(
            FloatArray tornadoCache, FloatArray seqCache,
            int layer, int position, int kvDim, int contextLength) {

        int baseOffset = layer * contextLength * kvDim;
        int size = (position + 1) * kvDim;  // Only compare up to current position

        FloatArray tornadoSubset = new FloatArray(size);
        FloatArray seqSubset = new FloatArray(size);

        // Extract the relevant portion of the cache
        for (int p = 0; p <= position; p++) {
            for (int i = 0; i < kvDim; i++) {
                int srcOffset = baseOffset + (p * kvDim) + i;
                int destOffset = (p * kvDim) + i;
                tornadoSubset.set(destOffset, tornadoCache.get(srcOffset));
                seqSubset.set(destOffset, seqCache.get(srcOffset));
            }
        }

        return compareArrays(tornadoSubset, seqSubset, "KV Cache Layer " + layer);
    }

    /**
     * Analyzes the distribution of values in an array
     */
    public static DistributionStats analyzeDistribution(FloatArray array) {
        if (array.getSize() == 0) {
            return new DistributionStats(0, 0, 0, 0, 0, 0, 0);
        }

        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0.0f;
        float sumSquared = 0.0f;

        for (int i = 0; i < array.getSize(); i++) {
            float val = array.get(i);
            min = Math.min(min, val);
            max = Math.max(max, val);
            sum += val;
            sumSquared += val * val;
        }

        float mean = sum / array.getSize();
        float variance = (sumSquared / array.getSize()) - (mean * mean);
        float stdDev = (float) Math.sqrt(variance);

        // Count zeroes and NaNs
        int zeroCount = 0;
        int nanCount = 0;

        for (int i = 0; i < array.getSize(); i++) {
            float val = array.get(i);
            if (val == 0.0f) {
                zeroCount++;
            }
            if (Float.isNaN(val)) {
                nanCount++;
            }
        }

        return new DistributionStats(
                array.getSize(),
                min,
                max,
                mean,
                stdDev,
                zeroCount,
                nanCount
        );
    }

    /**
     * Class to hold distribution statistics
     */
    public static class DistributionStats {
        public final int size;
        public final float min;
        public final float max;
        public final float mean;
        public final float stdDev;
        public final int zeroCount;
        public final int nanCount;

        public DistributionStats(int size, float min, float max, float mean, float stdDev, int zeroCount, int nanCount) {
            this.size = size;
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.stdDev = stdDev;
            this.zeroCount = zeroCount;
            this.nanCount = nanCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "Size: %d, Range: [%e, %e], Mean: %e, StdDev: %e, Zeros: %d (%.1f%%), NaNs: %d",
                    size,
                    min,
                    max,
                    mean,
                    stdDev,
                    zeroCount,
                    (zeroCount * 100.0f) / size,
                    nanCount
            );
        }
    }

    /**
     * Class to hold validation results
     */
    public static class ValidationResult {
        public final String arrayName;
        public final int size;
        public final float maxAbsDiff;
        public final float maxRelDiff;
        public final float rmsDiff;
        public final int numFailures;
        public final int maxFailureIndex;
        public final float value1AtMaxFailure;
        public final float value2AtMaxFailure;
        public final boolean passed;

        public ValidationResult(
                String arrayName, int size, float maxAbsDiff, float maxRelDiff,
                float rmsDiff, int numFailures, int maxFailureIndex,
                float value1AtMaxFailure, float value2AtMaxFailure, boolean passed) {
            this.arrayName = arrayName;
            this.size = size;
            this.maxAbsDiff = maxAbsDiff;
            this.maxRelDiff = maxRelDiff;
            this.rmsDiff = rmsDiff;
            this.numFailures = numFailures;
            this.maxFailureIndex = maxFailureIndex;
            this.value1AtMaxFailure = value1AtMaxFailure;
            this.value2AtMaxFailure = value2AtMaxFailure;
            this.passed = passed;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(arrayName).append(" validation ");
            sb.append(passed ? "PASSED" : "FAILED").append("\n");
            sb.append("  Size: ").append(size).append("\n");
            sb.append("  Max Absolute Diff: ").append(maxAbsDiff).append("\n");
            sb.append("  Max Relative Diff: ").append(maxRelDiff).append("\n");
            sb.append("  RMS Diff: ").append(rmsDiff).append("\n");

            if (numFailures > 0) {
                sb.append("  Failures: ").append(numFailures)
                        .append(" (").append(String.format("%.2f%%", (numFailures * 100.0f) / size)).append(")\n");
                sb.append("  Max failure at index ").append(maxFailureIndex)
                        .append(": ").append(value1AtMaxFailure)
                        .append(" vs ").append(value2AtMaxFailure);
            }

            return sb.toString();
        }
    }
}
