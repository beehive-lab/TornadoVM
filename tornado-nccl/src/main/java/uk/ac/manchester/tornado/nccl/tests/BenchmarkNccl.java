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
package uk.ac.manchester.tornado.nccl.tests;

import java.util.Random;

import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.nccl.NcclRedOp;
import uk.ac.manchester.tornado.nccl.TornadoNccl;

/**
 * Benchmark for {@link TornadoNccl#allReduce} across message sizes. Reports the
 * per-call time and the algorithmic bandwidth (message bytes / time). The rank
 * set defaults to the single device {@code 0}; pass a comma-separated device
 * list to use multiple GPUs, e.g.
 *
 * <code>
 * tornado -m tornado.nccl/uk.ac.manchester.tornado.nccl.tests.BenchmarkNccl --params="0,1,2,3"
 * </code>
 *
 * On a single GPU the numbers reflect the host-staging + NCCL loopback path;
 * cross-GPU scaling is measured when more than one device is given.
 */
public class BenchmarkNccl {

    private static final int WARMUP = 10;
    private static final int ITERATIONS = 50;

    public static void main(String[] args) {
        int[] devices = { 0 };
        if (args.length > 0) {
            String[] parts = args[0].split(",");
            devices = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                devices[i] = Integer.parseInt(parts[i].trim());
            }
        }
        int nRanks = devices.length;
        Random random = new Random(42);

        System.out.println("NCCL allReduce (SUM, FP32) benchmark over " + nRanks + " rank(s): device(s) " + java.util.Arrays.toString(devices));
        System.out.printf("  %-12s %12s %14s%n", "elements", "time(ms)", "bandwidth(GB/s)");

        try (TornadoNccl comm = new TornadoNccl(devices)) {
            for (int log = 12; log <= 24; log += 2) {
                int count = 1 << log;
                FloatArray[] send = new FloatArray[nRanks];
                FloatArray[] recv = new FloatArray[nRanks];
                for (int r = 0; r < nRanks; r++) {
                    send[r] = new FloatArray(count);
                    recv[r] = new FloatArray(count);
                    for (int i = 0; i < count; i++) {
                        send[r].set(i, random.nextFloat());
                    }
                }

                for (int w = 0; w < WARMUP; w++) {
                    comm.allReduce(send, recv, NcclRedOp.SUM);
                }
                long start = System.nanoTime();
                for (int it = 0; it < ITERATIONS; it++) {
                    comm.allReduce(send, recv, NcclRedOp.SUM);
                }
                double nanos = (System.nanoTime() - start) / (double) ITERATIONS;
                double bytes = (double) count * Float.BYTES;
                double gbps = bytes / nanos; // bytes/ns == GB/s
                System.out.printf("  %-12d %12.4f %14.2f%n", count, nanos * 1e-6, gbps);
            }
        }
    }
}
