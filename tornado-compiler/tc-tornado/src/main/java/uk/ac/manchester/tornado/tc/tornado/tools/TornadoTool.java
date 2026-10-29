/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.tc.tornado.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.IrParseException;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.ir.target.TargetCaps;
import uk.ac.manchester.tornado.tc.tornado.AbiLowering;
import uk.ac.manchester.tornado.tc.tornado.ParallelLoopLowering;
import uk.ac.manchester.tornado.tc.tornado.VectorMemory;

/**
 * The {@code tc-tornado} tool: runs TornadoVM lowerings on TC-SSA and prints the result, verified at
 * the post-ABI stage. {@code tc-tornado [--parallel] [--abi] [--split-vectors] [--header=16|24] <file | ->};
 * {@code --split-vectors} splits vector loads and stores into lane accesses after the ABI lowering, as
 * for targets without wide accesses.
 */
public final class TornadoTool {

    public record Result(int exitCode, String stdout, String stderr) {
    }

    private TornadoTool() {
    }

    /** Command-line entry point; reads standard input when the file is {@code -} or absent. */
    public static void main(String[] args) throws IOException {
        var stdin = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var result = run(List.of(args), stdin);
        System.out.print(result.stdout());
        System.err.print(result.stderr());
        System.exit(result.exitCode());
    }

    public static Result run(List<String> args, String stdin) {
        var target = TargetCaps.CUDA_SM89;
        boolean abi = false;
        boolean parallel = false;
        boolean splitVectors = false;
        String file = null;
        for (var arg : args) {
            if (arg.equals("--abi")) {
                abi = true;
            } else if (arg.equals("--parallel")) {
                parallel = true;
            } else if (arg.equals("--split-vectors")) {
                splitVectors = true;
            } else if (arg.startsWith("--header=")) {
                target = target.withArrayHeaderBytes(Integer.parseInt(arg.substring("--header=".length())));
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new Result(2, "", "tc-tornado: unknown option " + arg + "\n");
            } else {
                file = arg;
            }
        }
        try {
            var text = file == null || file.equals("-") ? stdin : Files.readString(Path.of(file));
            var module = IrParser.parseModule(text);
            var err = new StringBuilder();
            Verifier.verify(module, Verifier.Stage.PRE_ABI).forEach(d -> err.append(d.render()).append('\n'));
            if (!err.isEmpty()) {
                return new Result(1, "", err.toString());
            }
            if (parallel) {
                for (var function : module.functions()) {
                    ParallelLoopLowering.run(function).forEach(d -> err.append(d.render()).append('\n'));
                }
                if (!err.isEmpty()) {
                    return new Result(1, "", err.toString());
                }
            }
            if (abi) {
                for (var function : module.functions()) {
                    AbiLowering.run(function, target).forEach(d -> err.append(d.render()).append('\n'));
                }
                if (!err.isEmpty()) {
                    return new Result(1, "", err.toString());
                }
                if (splitVectors) {
                    module.functions().forEach(VectorMemory::split);
                }
                Verifier.verify(module, Verifier.Stage.POST_ABI).forEach(d -> err.append(d.render()).append('\n'));
                if (!err.isEmpty()) {
                    return new Result(1, "", "internal error: ABI lowering produced invalid IR\n" + err);
                }
            }
            return new Result(0, IrPrinter.print(module), "");
        } catch (IOException e) {
            return new Result(2, "", "tc-tornado: cannot read " + file + ": " + e.getMessage() + "\n");
        } catch (IrParseException e) {
            return new Result(1, "", e.diagnostic().render() + "\n");
        }
    }
}
