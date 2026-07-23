/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.fuzz.kernels;

import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.DataGen;
import uk.ac.manchester.tornado.fuzz.ElemType;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;
import uk.ac.manchester.tornado.fuzz.RandomGen;
import uk.ac.manchester.tornado.fuzz.ReproSpec;
import uk.ac.manchester.tornado.fuzz.oracle.Diff;
import uk.ac.manchester.tornado.fuzz.oracle.Oracle;

/**
 * All threads accumulate into a single element via {@code KernelContext.atomicAdd}.
 * Integer add is associative and commutative, so the result is independent of the
 * (nondeterministic) GPU execution order and can be checked exactly against a
 * sequential JVM sum (matching two's-complement wraparound).
 */
public final class AtomicKernel implements KernelTemplate {

    @Override
    public String id() {
        return "atomic-add";
    }

    public static void kAtomicAddInt(KernelContext c, IntArray in, IntArray out) {
        int i = c.globalIdx;
        c.atomicAdd(out, 0, in.get(i));
    }

    public static void kAtomicAddLong(KernelContext c, LongArray in, LongArray out) {
        int i = c.globalIdx;
        c.atomicAdd(out, 0, in.get(i));
    }

    @Override
    public CaseResult run(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        boolean useLong = rng.nextBoolean();
        cfg.put("elem", useLong ? "long" : "int");
        return useLong ? runLong(cfg, rng, device) : runInt(cfg, rng, device);
    }

    private CaseResult runInt(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        int size = cfg.size;
        IntArray in = new IntArray(size);
        IntArray out = new IntArray(1);
        DataGen.fill(in, rng, cfg.dataProfile);
        out.set(0, 0);

        KernelContext context = new KernelContext();
        TaskGraph tg = new TaskGraph(Kernels.GRAPH) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, out) //
                .task(Kernels.TASK, AtomicKernel::kAtomicAddInt, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        int sum = 0;
        for (int i = 0; i < size; i++) {
            sum += in.get(i);
        }
        IntArray expected = new IntArray(1);
        expected.set(0, sum);

        String kernelSource = "public static void fuzzedKernel(KernelContext context, IntArray in, IntArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    context.atomicAdd(out, 0, in.get(i));\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.INT, size, cfg.localSize) //
                .addInput("in", ElemType.INT, in).setAccumulator("out", ElemType.INT, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }

    private CaseResult runLong(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        int size = cfg.size;
        LongArray in = new LongArray(size);
        LongArray out = new LongArray(1);
        DataGen.fill(in, rng, cfg.dataProfile);
        out.set(0, 0L);

        KernelContext context = new KernelContext();
        TaskGraph tg = new TaskGraph(Kernels.GRAPH) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, out) //
                .task(Kernels.TASK, AtomicKernel::kAtomicAddLong, context, in, out) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, out);

        long sum = 0;
        for (int i = 0; i < size; i++) {
            sum += in.get(i);
        }
        LongArray expected = new LongArray(1);
        expected.set(0, sum);

        String kernelSource = "public static void fuzzedKernel(KernelContext context, LongArray in, LongArray out) {\n" //
                + "    int i = context.globalIdx;\n" //
                + "    context.atomicAdd(out, 0, in.get(i));\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.LONG, size, cfg.localSize) //
                .addInput("in", ElemType.LONG, in).setAccumulator("out", ElemType.LONG, out, expected);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, cfg.localSize), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, out);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }
}
