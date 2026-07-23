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
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.DataGen;
import uk.ac.manchester.tornado.fuzz.ElemType;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;
import uk.ac.manchester.tornado.fuzz.RandomGen;
import uk.ac.manchester.tornado.fuzz.ReproSpec;
import uk.ac.manchester.tornado.fuzz.oracle.Diff;
import uk.ac.manchester.tornado.fuzz.oracle.Oracle;

/**
 * Classic shared-memory tree reduction: each work-group reduces {@code LOCAL}
 * elements into one partial using {@code allocateFloatLocalArray} +
 * {@code localBarrier()}. Exercises __shared__ + __syncthreads codegen. The JVM
 * reference replicates the identical reduction tree, so float re-association is
 * not a source of false positives — only real codegen divergence is.
 */
public final class LocalMemReduceKernel implements KernelTemplate {

    private static final int LOCAL = 256;

    @Override
    public String id() {
        return "localmem-reduce";
    }

    public static void kReduceLocal(KernelContext c, FloatArray in, FloatArray partial, int localSize) {
        int gid = c.globalIdx;
        int lid = c.localIdx;
        int groupId = c.groupIdx;
        float[] shared = c.allocateFloatLocalArray(256);
        shared[lid] = in.get(gid);
        for (int stride = localSize / 2; stride > 0; stride >>= 1) {
            c.localBarrier();
            if (lid < stride) {
                shared[lid] = shared[lid] + shared[lid + stride];
            }
        }
        if (lid == 0) {
            partial.set(groupId, shared[0]);
        }
    }

    @Override
    public CaseResult run(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        int groups = Math.max(1, cfg.size / LOCAL);
        int size = groups * LOCAL;
        cfg.put("effectiveSize", size);
        cfg.put("groups", groups);
        cfg.put("localSize", LOCAL);

        FloatArray in = new FloatArray(size);
        FloatArray partial = new FloatArray(groups);
        DataGen.fill(in, rng, cfg.dataProfile);

        KernelContext context = new KernelContext();
        TaskGraph tg = new TaskGraph(Kernels.GRAPH) //
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, in, LOCAL) //
                .task(Kernels.TASK, LocalMemReduceKernel::kReduceLocal, context, in, partial, LOCAL) //
                .transferToHost(DataTransferMode.EVERY_EXECUTION, partial);

        // JVM reference: identical per-group tree reduction.
        FloatArray expected = new FloatArray(groups);
        float[] shared = new float[LOCAL];
        for (int g = 0; g < groups; g++) {
            for (int lid = 0; lid < LOCAL; lid++) {
                shared[lid] = in.get(g * LOCAL + lid);
            }
            for (int stride = LOCAL / 2; stride > 0; stride >>= 1) {
                for (int lid = 0; lid < stride; lid++) {
                    shared[lid] = shared[lid] + shared[lid + stride];
                }
            }
            expected.set(g, shared[0]);
        }

        String kernelSource = "public static void fuzzedKernel(KernelContext context, FloatArray in, FloatArray partial, int localSize) {\n" //
                + "    int gid = context.globalIdx;\n" //
                + "    int lid = context.localIdx;\n" //
                + "    int groupId = context.groupIdx;\n" //
                + "    float[] shared = context.allocateFloatLocalArray(256);\n" //
                + "    shared[lid] = in.get(gid);\n" //
                + "    for (int stride = localSize / 2; stride > 0; stride >>= 1) {\n" //
                + "        context.localBarrier();\n" //
                + "        if (lid < stride) { shared[lid] = shared[lid] + shared[lid + stride]; }\n" //
                + "    }\n" //
                + "    if (lid == 0) { partial.set(groupId, shared[0]); }\n" //
                + "}";
        ReproSpec repro = new ReproSpec(kernelSource, ElemType.FLOAT, size, LOCAL) //
                .addInput("in", ElemType.FLOAT, in).setOutput("partial", ElemType.FLOAT, partial, expected).withScalar("localSize", LOCAL);

        try {
            Kernels.run(tg, Kernels.gridScheduler(size, LOCAL), device);
        } catch (Throwable e) {
            return CaseResult.exception(e, kernelSource, repro);
        }
        Diff diff = Oracle.compare(expected, partial);
        return diff == null ? CaseResult.pass(kernelSource) : CaseResult.mismatch(diff, kernelSource, repro);
    }
}
