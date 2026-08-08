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
package uk.ac.manchester.tornado.fuzz.report;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import uk.ac.manchester.tornado.fuzz.CaseResult;
import uk.ac.manchester.tornado.fuzz.FuzzConfig;

/**
 * Writes a self-contained, agent-fixable debug bundle per finding:
 * {@code <outDir>/findings/<kind>-<seed>/} with the kernel source, config,
 * numeric diff, stack trace, a standalone JUnit reproducer, and an LLM-ready
 * {@code FIX_ME.md} that points at the likely culprit codegen files. The Python
 * driver enriches the same directory with {@code kernel.cu} / {@code bytecodes.txt}
 * (from a targeted replay) and {@code hs_err.log} on a native crash.
 */
public final class BundleWriter {

    private final Path root;

    public BundleWriter(Path outDir) {
        this.root = outDir.resolve("findings");
    }

    /** @return the created bundle directory. */
    public Path write(FuzzConfig cfg, CaseResult result) throws IOException {
        String reproClass = "Repro_" + shortTemplate(cfg.templateId) + "_" + cfg.seed;
        Path dir = root.resolve(result.status.name().toLowerCase() + "-" + cfg.seed);
        Files.createDirectories(dir);

        Files.writeString(dir.resolve("config.json"), cfg.toJson() + "\n", StandardCharsets.UTF_8);
        if (result.kernelText != null) {
            Files.writeString(dir.resolve("kernel.java"), result.kernelText + "\n", StandardCharsets.UTF_8);
        }
        if (result.diff != null) {
            String diff = "firstBadIndex=" + result.diff.firstBadIndex + " mismatchCount=" + result.diff.mismatchCount + "\n\n" + result.diff.detail;
            Files.writeString(dir.resolve("diff.txt"), diff, StandardCharsets.UTF_8);
        }
        if (result.error != null) {
            StringWriter sw = new StringWriter();
            result.error.printStackTrace(new PrintWriter(sw));
            Files.writeString(dir.resolve("stacktrace.txt"), sw.toString(), StandardCharsets.UTF_8);
        }
        if (result.repro != null) {
            String source = JUnitEmitter.emit(result.repro, reproClass);
            Files.writeString(dir.resolve(reproClass + ".java"), source, StandardCharsets.UTF_8);
        }
        Files.writeString(dir.resolve("FIX_ME.md"), fixMe(cfg, result, reproClass), StandardCharsets.UTF_8);
        return dir;
    }

    private static String shortTemplate(String templateId) {
        return templateId == null ? "case" : templateId.replace('-', '_');
    }

    private static String fixMe(FuzzConfig cfg, CaseResult result, String reproClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("# CUDA backend fuzz finding — ").append(result.status).append("\n\n");
        sb.append("## Symptom\n\n");
        switch (result.status) {
            case MISMATCH -> {
                sb.append("The CUDA-backend result differs from the JVM sequential reference.\n");
                if (result.diff != null) {
                    sb.append("- First bad index: `").append(result.diff.firstBadIndex).append("`\n");
                    sb.append("- Expected: `").append(result.diff.expected).append("`, actual: `").append(result.diff.actual).append("`\n");
                    sb.append("- Total mismatching elements: `").append(result.diff.mismatchCount).append("`\n");
                }
            }
            case EXCEPTION -> {
                sb.append("Compilation/execution threw on the CUDA backend.\n");
                if (result.error != null) {
                    sb.append("- Exception: `").append(result.error.getClass().getName()).append("`\n");
                    sb.append("- Message: `").append(String.valueOf(result.error.getMessage())).append("`\n");
                }
            }
            default -> sb.append("(unexpected status)\n");
        }

        sb.append("\n## Kernel\n\n```java\n").append(result.kernelText == null ? "(n/a)" : result.kernelText).append("\n```\n");

        sb.append("\n## Reproduce\n\n");
        sb.append("Deterministic replay through the fuzzer:\n\n");
        sb.append("```bash\n");
        sb.append("tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \\\n");
        sb.append("    --params \"seed=").append(cfg.seed).append(" count=1 phase=").append(cfg.phase).append(" outDir=/tmp/fuzz\"\n");
        sb.append("```\n\n");
        sb.append("Standalone regression test (copy `").append(reproClass).append(".java` into\n");
        sb.append("`tornado-unittests/src/main/java/uk/ac/manchester/tornado/unittests/fuzz/`):\n\n");
        sb.append("```bash\n");
        sb.append("tornado-test -V ").append(JUnitEmitter.PACKAGE).append('.').append(reproClass).append("#test \\\n");
        sb.append("    --jvm=\"-Dtornado.cuda.priority=100\"\n");
        sb.append("```\n\n");
        sb.append("Dump the generated CUDA-C and bytecodes into this bundle:\n\n");
        sb.append("```bash\n");
        sb.append("tornado -m tornado.fuzz/uk.ac.manchester.tornado.fuzz.FuzzMain \\\n");
        sb.append("    --params \"seed=").append(cfg.seed).append(" count=1 phase=").append(cfg.phase).append(" outDir=/tmp/fuzz\" \\\n");
        sb.append("    --jvm=\"-Dtornado.printKernel=true -Dtornado.print.kernel.dir=$(pwd) -Dtornado.print.bytecodes=true\"\n");
        sb.append("```\n");

        sb.append("\n## Likely culprit files\n\n");
        for (String hint : culprits(cfg)) {
            sb.append("- ").append(hint).append('\n');
        }
        sb.append("\nSee `kernel.cu` (once dumped) to inspect the emitted CUDA source, and\n");
        sb.append("`config.json` for the full generation parameters.\n");
        return sb.toString();
    }

    private static String[] culprits(FuzzConfig cfg) {
        String template = String.valueOf(cfg.templateId);
        Object op = cfg.params.get("op");
        Object fn = cfg.params.get("fn");
        return switch (template) {
            case "elementwise-arith" -> {
                if ("F2I".equals(op)) {
                    yield new String[] { "`tornado-drivers/cuda/.../graal/lir/CUDAArithmeticTool.java` — `emitFloatConvert` (only `I2D` is implemented; `F2I` is a known `unimplemented()` edge)", "`tornado-drivers/cuda/.../graal/asm/CUDAAssembler.java` — `CAST_TO_*` unary ops" };
                }
                if ("DIV_INT".equals(op) || "MOD_INT".equals(op)) {
                    yield new String[] { "`tornado-drivers/cuda/.../graal/lir/CUDAArithmeticTool.java` — `emitDiv`/`emitRem`/`emitUDiv`", "`tornado-drivers/cuda/.../graal/asm/CUDAAssembler.java` — DIV/MOD binary ops" };
                }
                if ("SHL_INT".equals(op) || "SHR_INT".equals(op)) {
                    yield new String[] { "`tornado-drivers/cuda/.../graal/lir/CUDAArithmeticTool.java` — `emitShl`/`emitShr`/`emitUShr`" };
                }
                yield new String[] { "`tornado-drivers/cuda/.../graal/lir/CUDAArithmeticTool.java` — binary arithmetic/bitwise emission", "`tornado-drivers/cuda/.../graal/asm/CUDAAssembler.java` — `CUDABinaryOp`" };
            }
            case "math-intrinsic" -> new String[] { //
                    "`tornado-drivers/cuda/.../graal/compiler/plugins/CUDAMathPlugins.java` — is `" + fn + "` registered/reachable?", //
                    "`tornado-drivers/cuda/.../graal/lir/CUDABuiltinTool.java` — LIR emission for `" + fn + "` (many builtins are `unimplemented()`)" };
            case "localmem-reduce" -> new String[] { //
                    "`tornado-drivers/cuda/.../graal/nodes/` — `LocalArrayNode` / `FixedArrayNode` (__shared__ allocation)", //
                    "`tornado-drivers/cuda/.../graal/nodes/CUDABarrierNode.java` — `__syncthreads()` lowering", //
                    "`tornado-drivers/cuda/.../graal/asm/CUDAAssemblerConstants.java` — `SHARED/LOCAL_MEM_MODIFIER`" };
            case "atomic-add" -> new String[] { //
                    "`tornado-drivers/cuda/.../graal/lir/` — `StoreAtomicAddStmt` / `StoreAtomicAddFloatStmt`", //
                    "`tornado-drivers/cuda/.../graal/compiler/plugins/AtomicPlugins.java` — `atomicAdd` intrinsic", //
                    "`tornado-drivers/cuda/.../graal/lir/CUDAKind.java` — `ATOMIC_ADD_INT/LONG` kinds" };
            default -> new String[] { "`tornado-drivers/cuda/.../graal/` — CUDA backend code generator" };
        };
    }
}
