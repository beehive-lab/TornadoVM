/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
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

/**
 * The {@code tc-tornado} tool: runs TornadoVM lowerings on TC-SSA and prints the result, verified at
 * the post-ABI stage. {@code tc-tornado [--parallel] [--abi] [--header=16|24] <file | ->}.
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
        String file = null;
        for (var arg : args) {
            if (arg.equals("--abi")) {
                abi = true;
            } else if (arg.equals("--parallel")) {
                parallel = true;
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
