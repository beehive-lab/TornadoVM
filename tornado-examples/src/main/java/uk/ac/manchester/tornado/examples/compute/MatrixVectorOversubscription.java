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
package uk.ac.manchester.tornado.examples.compute;

import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.runtime.TornadoRuntimeProvider;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

/**
 * Demonstrates CUDA Unified Memory <b>over-subscription</b>: a kernel whose total
 * device working set exceeds physical VRAM.
 *
 * <p>
 * A single TornadoVM {@code FloatArray} is int-indexed (capped at ~8 GB), so
 * over-subscription is shown <i>cumulatively</i>: several large arrays that
 * together exceed VRAM, all resident on the device at once. The element-wise
 * kernel {@code out = a + b + c} keeps four such arrays live simultaneously.
 *
 * <ol>
 *   <li><b>Default</b> ({@code cuMemAlloc}): once the live allocations exceed VRAM
 *       the driver allocation fails.</li>
 *   <li><b>Unified Memory</b> ({@code withCudaUM()} -&gt; {@code cuMemAllocManaged}):
 *       the CUDA runtime pages the buffers on demand, so it succeeds and produces a
 *       correct result.</li>
 * </ol>
 *
 * <p>
 * The arrays must fit in host RAM, and the TornadoVM device-memory accounting cap
 * must be raised above the total working set:
 *
 * <p>
 * A failed {@code cuMemAlloc} leaves the CUDA context in an unrecoverable state, so
 * the two paths must be run in <b>separate processes</b> (one mode per run):
 *
 * <p>
 * <code>
 * # Unified Memory: succeeds
 * tornado --jvm="-Dtornado.device.memory=48GB -XX:MaxDirectMemorySize=48g" \
 *     -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorOversubscription um [totalGB]
 *
 * # Default: fails (out of device memory)
 * tornado --jvm="-Dtornado.device.memory=48GB -XX:MaxDirectMemorySize=48g" \
 *     -m tornado.examples/uk.ac.manchester.tornado.examples.compute.MatrixVectorOversubscription default [totalGB]
 * </code>
 */
public class MatrixVectorOversubscription {

    public static void addThree(FloatArray a, FloatArray b, FloatArray c, FloatArray out) {
        for (@Parallel int i = 0; i < out.getSize(); i++) {
            out.set(i, a.get(i) + b.get(i) + c.get(i));
        }
    }

    private static boolean tryRun(String label, boolean unifiedMemory, FloatArray a, FloatArray b, FloatArray c, FloatArray out) {
        TaskGraph graph = new TaskGraph(label.replace(" ", "_").replaceAll("[^A-Za-z0-9_]", "")) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b, c) //
                .task("t0", MatrixVectorOversubscription::addThree, a, b, c, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
        long start = System.nanoTime();
        try (TornadoExecutionPlan plan = new TornadoExecutionPlan(graph.snapshot())) {
            if (unifiedMemory) {
                plan.withCudaUM();
            }
            plan.execute();
            double s = (System.nanoTime() - start) / 1e9;
            float expected = a.get(0) + b.get(0) + c.get(0);
            boolean ok = Math.abs(expected - out.get(0)) < 1e-3f;
            // A GPU out-of-memory does not throw: TornadoVM bails to a sequential
            // fallback and the device result is invalid. Treat a wrong result as a
            // failure of this path, not a success.
            System.out.printf("  %-32s %s in %.1f s  (out[0]=%.3f, expected=%.3f)%n", label, ok ? "SUCCESS" : "FAILED (invalid result)", s, out.get(0), expected);
            return ok;
        } catch (Throwable e) {
            double s = (System.nanoTime() - start) / 1e9;
            System.out.printf("  %-32s FAILED after %.1f s: %s%n", label, s, e.getClass().getSimpleName() + " - " + firstLine(e.getMessage()));
            return false;
        }
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "(no message)";
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    public static void main(String[] args) {
        // One mode per process: a failed cuMemAlloc poisons the CUDA context.
        boolean unifiedMemory = args.length < 1 || !args[0].equalsIgnoreCase("default");
        String mode = unifiedMemory ? "Unified Memory (.withCudaUM)" : "Default (cuMemAlloc)";

        double maxAllocGB = TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getMaxAllocMemory() / (1024.0 * 1024.0 * 1024.0);

        // Four equal arrays (a, b, c, out); default total ~= 1.2x VRAM so the live set
        // cannot fit on the device at once.
        double totalGB = maxAllocGB * 1.2;
        if (args.length >= 2) {
            totalGB = Double.parseDouble(args[1]);
        }
        long elementsPerArray = (long) (totalGB * 1024.0 * 1024.0 * 1024.0 / Float.BYTES / 4.0);
        // FloatArray is int-indexed; keep within range with margin.
        if (elementsPerArray > 2_000_000_000L) {
            elementsPerArray = 2_000_000_000L;
        }
        int len = (int) elementsPerArray;
        double perArrayGB = (long) len * Float.BYTES / (1024.0 * 1024.0 * 1024.0);

        System.out.println("CUDA Unified Memory Over-subscription Demo");
        System.out.println("==========================================");
        System.out.println("- Mode               : " + mode);
        System.out.println("- Backend            : " + TornadoRuntimeProvider.getTornadoRuntime().getBackendType(TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getBackendIndex()));
        System.out.println("- Device             : " + TornadoRuntimeProvider.getTornadoRuntime().getDefaultDevice().getPhysicalDevice().getDeviceName());
        System.out.printf("- Device max alloc   : %.1f GB%n", maxAllocGB);
        System.out.printf("- Arrays             : 4 x %.1f GB  ->  %.1f GB live working set%n", perArrayGB, 4 * perArrayGB);
        System.out.println("- Reminder           : raise -Dtornado.device.memory above the working set");
        System.out.println();

        System.out.println("Allocating host arrays (large; please wait)...");
        FloatArray a = new FloatArray(len);
        FloatArray b = new FloatArray(len);
        FloatArray c = new FloatArray(len);
        FloatArray out = new FloatArray(len);
        a.init(1.0f);
        b.init(2.0f);
        c.init(3.0f);

        System.out.println("Running:");
        boolean success = tryRun(mode, unifiedMemory, a, b, c, out);

        System.out.println();
        if (unifiedMemory) {
            System.out.println(success //
                    ? "Unified Memory paged the " + String.format("%.1f", 4 * perArrayGB) + " GB working set through " + String.format("%.1f", maxAllocGB) + " GB of VRAM." //
                    : "Unexpected: Unified Memory failed.");
        } else {
            System.out.println(success //
                    ? "Unexpected: the default path fit in VRAM (working set may be too small)." //
                    : "As expected: the default path cannot fit the working set in VRAM.");
        }
    }
}
