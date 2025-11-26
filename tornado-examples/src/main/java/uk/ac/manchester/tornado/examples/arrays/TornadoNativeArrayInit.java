/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.examples.arrays;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Performance benchmark comparing different FloatArray initialization methods:
 * - Regular constructor: new FloatArray(size)
 * - fromSegment method: FloatArray.fromSegment(segment)
 * - fromSegmentShallow method: FloatArray.fromSegmentShallow(segment)
 *
 * <p>
 * How to run?
 * </p>
 * <code>
 * tornado -m tornado.examples/uk.ac.manchester.tornado.examples.arrays.TornadoNativeArrayInit
 * </code>
 */
public class TornadoNativeArrayInit {

    private static void benchmarkRegularConstructor(int size, int iterations) {
        System.out.println("Benchmarking Regular Constructor...");

        long startTime = System.nanoTime();
        for (int iter = 0; iter < iterations; iter++) {
            FloatArray array = new FloatArray(size);
            // Initialize with some data
            for (int i = 0; i < size; i++) {
                array.set(i, i * 1.5f);
            }
            // Touch some data to ensure it's actually used
            float sum = 0;
            for (int i = 0; i < Math.min(10, size); i++) {
                sum += array.get(i);
            }
        }
        long duration = System.nanoTime() - startTime;

        double ms = duration / 1_000_000.0;
        System.out.printf("Regular Constructor: %.3f ms for %d iterations\n", ms, iterations);
    }

    private static void benchmarkFromSegment(int size, int iterations) {
        System.out.println("Benchmarking fromSegment Method...");

        // Pre-create the data segment (data-only, no header)
        MemorySegment dataSegment = Arena.ofAuto().allocate(ValueLayout.JAVA_FLOAT.byteSize() * size);
        for (int i = 0; i < size; i++) {
            dataSegment.setAtIndex(ValueLayout.JAVA_FLOAT, i, i * 1.5f);
        }

        long startTime = System.nanoTime();
        for (int iter = 0; iter < iterations; iter++) {
            FloatArray array = FloatArray.fromSegment(dataSegment);
            // Touch some data to ensure it's actually used
            float sum = 0;
            for (int i = 0; i < Math.min(10, size); i++) {
                sum += array.get(i);
            }
        }
        long duration = System.nanoTime() - startTime;

        double ms = duration / 1_000_000.0;
        System.out.printf("fromSegment Method: %.3f ms for %d iterations\n", ms, iterations);
    }

    private static void benchmarkFromSegmentShallow(int size, int iterations) {
        System.out.println("Benchmarking fromSegmentShallow Method...");

        // Pre-create the segment with proper TornadoNativeArray layout
        long headerSize = TornadoNativeArray.ARRAY_HEADER;
        long totalSize = headerSize + (ValueLayout.JAVA_FLOAT.byteSize() * size);
        MemorySegment segment = Arena.ofAuto().allocate(totalSize);

        // Pre-populate with data
        long headerOffsetInFloats = headerSize / ValueLayout.JAVA_FLOAT.byteSize();
        for (int i = 0; i < size; i++) {
            segment.setAtIndex(ValueLayout.JAVA_FLOAT, headerOffsetInFloats + i, i * 1.5f);
        }

        long startTime = System.nanoTime();
        for (int iter = 0; iter < iterations; iter++) {
            FloatArray array = FloatArray.fromSegmentShallow(segment);
            // Touch some data to ensure it's actually used
            float sum = 0;
            for (int i = 0; i < Math.min(10, size); i++) {
                sum += array.get(i);
            }
        }
        long duration = System.nanoTime() - startTime;

        double ms = duration / 1_000_000.0;
        System.out.printf("fromSegmentShallow Method: %.3f ms for %d iterations\n", ms, iterations);
    }

    private static String formatArraySize(int elements) {
        long bytes = (long) elements * ValueLayout.JAVA_FLOAT.byteSize();

        if (bytes >= 1_073_741_824L) { // 1 GB
            return String.format("%.2f GB", bytes / 1_073_741_824.0);
        } else if (bytes >= 1_048_576L) { // 1 MB
            return String.format("%.2f MB", bytes / 1_048_576.0);
        } else if (bytes >= 1024L) { // 1 KB
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return bytes + " bytes";
        }
    }

    public static void main(String[] args) {
        System.out.println("TornadoVM Native Array Initialization Performance Benchmark");
        System.out.println("Testing FloatArray initialization methods\n");

        // Test different array sizes
        int[] sizes = { 1_000, 10_000, 100_000, 1_000_000 };
        int iterations = 100;

        for (int size : sizes) {
            System.out.println("=".repeat(80));
            System.out.printf("Performance Benchmark - Array Size: %,d elements (%s), Iterations: %,d\n", size, formatArraySize(size), iterations);
            System.out.printf("Header Size: %d bytes\n", TornadoNativeArray.ARRAY_HEADER);
            System.out.println("=".repeat(80));

            // Warm up JVM
            System.out.println("Warming up JVM...");
            for (int i = 0; i < 10; i++) {
                FloatArray warmup = new FloatArray(100);
                warmup.set(0, 1.0f);
            }

            // Run benchmarks
            long[] times = new long[3];

            // Benchmark 1: Regular Constructor
            long startTime = System.nanoTime();
            benchmarkRegularConstructor(size, iterations);
            times[0] = System.nanoTime() - startTime;

            // Benchmark 2: fromSegment
            startTime = System.nanoTime();
            benchmarkFromSegment(size, iterations);
            times[1] = System.nanoTime() - startTime;

            // Benchmark 3: fromSegmentShallow
            startTime = System.nanoTime();
            benchmarkFromSegmentShallow(size, iterations);
            times[2] = System.nanoTime() - startTime;

            // Calculate speedups
            double regularMs = times[0] / 1_000_000.0;
            double fromSegmentMs = times[1] / 1_000_000.0;
            double fromSegmentShallowMs = times[2] / 1_000_000.0;

            double speedupVsRegular = regularMs / fromSegmentShallowMs;
            double speedupVsFromSegment = fromSegmentMs / fromSegmentShallowMs;

            System.out.println("\n" + "-".repeat(60));
            System.out.println("PERFORMANCE SUMMARY:");
            System.out.println("-".repeat(60));
            System.out.printf("Regular Constructor:    %.3f ms\n", regularMs);
            System.out.printf("fromSegment Method:     %.3f ms\n", fromSegmentMs);
            System.out.printf("fromSegmentShallow Method:  %.3f ms\n", fromSegmentShallowMs);
            System.out.println("-".repeat(60));
            System.out.printf("fromSegmentShallow vs Regular:      %.2fx faster\n", speedupVsRegular);
            System.out.printf("fromSegmentShallow vs fromSegment:  %.2fx faster\n", speedupVsFromSegment);
            System.out.println("-".repeat(60));
        }
    }
}
