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
package uk.ac.manchester.tornado.fuzz.gen;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.WorkerGrid1D;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.common.TornadoFunctions.Task4;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.DataGen;
import uk.ac.manchester.tornado.fuzz.ElemType;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;
import uk.ac.manchester.tornado.fuzz.RandomGen;
import uk.ac.manchester.tornado.fuzz.ReproSpec;
import uk.ac.manchester.tornado.fuzz.oracle.Diff;
import uk.ac.manchester.tornado.fuzz.oracle.Oracle;

/**
 * Phase 2 generative fuzzer. For each seed it generates a random integer
 * expression, emits a Java class (device kernel + identical JVM reference),
 * compiles it in-process, runs the kernel on CUDA and the reference on the JVM,
 * and compares them exactly. Because both come from the same expression text, any
 * mismatch is a genuine CUDA codegen defect. On a finding it {@link Shrinker
 * shrinks} the expression to a minimal reproducer before emitting the bundle.
 *
 * Requires {@code -Dtornado.fuzz.genDir=<dir>} AND that {@code <dir>} is on the
 * launch {@code -cp}. See {@link InProcessCompiler}.
 */
public final class Phase2Fuzzer {

    private static final int[] SIZES = { 256, 1024, 4096, 512 };
    private static final int MAX_DEPTH = 5;
    private static final int SHRINK_BUDGET = 40;

    private final InProcessCompiler compiler;
    private final AtomicLong classCounter = new AtomicLong();

    public Phase2Fuzzer(InProcessCompiler compiler) {
        this.compiler = compiler;
    }

    public CaseResult run(FuzzConfig cfg, RandomGen rng, TornadoDevice device) {
        cfg.templateId = "gen-expr";
        cfg.elemType = ElemType.INT;
        int size = SIZES[rng.nextInt(SIZES.length)];
        int local = chooseLocal(size);
        cfg.size = size;
        cfg.localSize = local;

        int depth = rng.nextIntBetween(2, MAX_DEPTH);
        Expr expr = Expr.generate(rng, depth);
        String exprText = expr.render();
        cfg.put("depth", depth);
        cfg.put("expr", exprText);
        cfg.put("nodes", expr.nodeCount());

        IntArray a = new IntArray(size);
        IntArray b = new IntArray(size);
        DataGen.fill(a, rng, cfg.dataProfile);
        DataGen.fill(b, rng, cfg.dataProfile);

        Attempt first = evaluate(exprText, a, b, size, local, device);
        if (!first.compiled) {
            // Compilation/reflection failure is a harness problem, not a backend finding.
            return CaseResult.exception(first.execError, KernelSourceEmitter.methodText(exprText), null);
        }
        if (first.pass) {
            return CaseResult.pass(KernelSourceEmitter.methodText(exprText));
        }

        // Shrink the failing expression to a minimal reproducer.
        Expr minimal = Shrinker.shrink(expr, cand -> {
            Attempt at = evaluate(cand.render(), a, b, size, local, device);
            return at.compiled && !at.pass;
        }, SHRINK_BUDGET);

        String minimalText = minimal.render();
        cfg.put("exprOriginal", exprText);
        cfg.put("expr", minimalText);
        cfg.put("nodesOriginal", expr.nodeCount());
        cfg.put("nodes", minimal.nodeCount());

        Attempt fin = evaluate(minimalText, a, b, size, local, device);
        String kernelMethod = KernelSourceEmitter.methodText(minimalText);
        IntArray reproOut = new IntArray(size); // zeroed; JUnitEmitter allocates output fresh
        ReproSpec repro = new ReproSpec(kernelMethod, ElemType.INT, size, local) //
                .addInput("a", ElemType.INT, a).addInput("b", ElemType.INT, b) //
                .setOutput("out", ElemType.INT, reproOut, fin.expected != null ? fin.expected : first.expected);

        if (fin.execError != null) {
            return CaseResult.exception(fin.execError, kernelMethod, repro);
        }
        Diff diff = fin.diff != null ? fin.diff : first.diff;
        return CaseResult.mismatch(diff, kernelMethod, repro);
    }

    /** Compile + run one expression on CUDA and the JVM reference; report the outcome. */
    private Attempt evaluate(String exprText, IntArray a, IntArray b, int size, int local, TornadoDevice device) {
        String className = "G" + classCounter.incrementAndGet();
        String fqcn = KernelSourceEmitter.fqcn(className);
        String source = KernelSourceEmitter.emit(className, exprText);

        Task4<KernelContext, IntArray, IntArray, IntArray> task;
        IntArray expected = new IntArray(size);
        try {
            Class<?> generated = compiler.compileAndLoad(fqcn, source);
            @SuppressWarnings("unchecked")
            Task4<KernelContext, IntArray, IntArray, IntArray> t = (Task4<KernelContext, IntArray, IntArray, IntArray>) generated.getMethod("task").invoke(null);
            task = t;
            Method reference = generated.getMethod("reference", IntArray.class, IntArray.class, IntArray.class);
            reference.invoke(null, a, b, expected);
        } catch (Throwable e) {
            return Attempt.notCompiled(e);
        }

        IntArray out = new IntArray(size);
        try {
            KernelContext context = new KernelContext();
            WorkerGrid worker = new WorkerGrid1D(size);
            worker.setGlobalWork(size, 1, 1);
            worker.setLocalWork(local, 1, 1);
            GridScheduler scheduler = new GridScheduler();
            scheduler.addWorkerGrid("fuzz.t0", worker);
            TaskGraph tg = new TaskGraph("fuzz") //
                    .transferToDevice(DataTransferMode.FIRST_EXECUTION, a, b) //
                    .task("t0", task, context, a, b, out) //
                    .transferToHost(DataTransferMode.EVERY_EXECUTION, out);
            try (TornadoExecutionPlan plan = new TornadoExecutionPlan(tg.snapshot())) {
                plan.withGridScheduler(scheduler).withDevice(device).execute();
            }
        } catch (Throwable e) {
            return Attempt.execFailed(expected, e);
        }

        Diff diff = Oracle.compare(expected, out);
        return Attempt.executed(expected, out, diff);
    }

    private static int chooseLocal(int size) {
        int local = 1;
        for (int c = 2; c <= 256 && c <= size; c <<= 1) {
            if (size % c == 0) {
                local = c;
            }
        }
        return local;
    }

    /** Outcome of one compile+run: whether it compiled, whether it passed, and the artifacts. */
    private static final class Attempt {
        final boolean compiled;
        final boolean pass;
        final Diff diff;
        final Throwable execError;
        final IntArray expected;
        final IntArray out;

        private Attempt(boolean compiled, boolean pass, Diff diff, Throwable execError, IntArray expected, IntArray out) {
            this.compiled = compiled;
            this.pass = pass;
            this.diff = diff;
            this.execError = execError;
            this.expected = expected;
            this.out = out;
        }

        static Attempt notCompiled(Throwable e) {
            return new Attempt(false, false, null, e, null, null);
        }

        static Attempt execFailed(IntArray expected, Throwable e) {
            return new Attempt(true, false, null, e, expected, null);
        }

        static Attempt executed(IntArray expected, IntArray out, Diff diff) {
            return new Attempt(true, diff == null, diff, null, expected, out);
        }
    }
}
