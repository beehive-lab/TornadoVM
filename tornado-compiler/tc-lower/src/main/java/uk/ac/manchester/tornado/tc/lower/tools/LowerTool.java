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
package uk.ac.manchester.tornado.tc.lower.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.IrParseException;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.lower.SPrinter;
import uk.ac.manchester.tornado.tc.lower.Structurizer;

/**
 * The {@code tc-lower} tool: reads TC-SSA, verifies it, structures every function and prints the
 * resulting TC-S as pseudo-C. {@code tc-lower [--stage=post-abi] <file | ->}.
 */
public final class LowerTool {

    public record Result(int exitCode, String stdout, String stderr) {
    }

    private LowerTool() {
    }

    /** Command-line entry point; reads standard input when the file is {@code -} or absent. */
    public static void main(String[] args) throws java.io.IOException {
        var stdin = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var result = run(java.util.List.of(args), stdin);
        System.out.print(result.stdout());
        System.err.print(result.stderr());
        System.exit(result.exitCode());
    }

    public static Result run(List<String> args, String stdin) {
        var stage = Verifier.Stage.PRE_ABI;
        String file = null;
        for (var arg : args) {
            if (arg.equals("--stage=post-abi")) {
                stage = Verifier.Stage.POST_ABI;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new Result(2, "", "tc-lower: unknown option " + arg + "\n");
            } else {
                file = arg;
            }
        }
        try {
            var text = file == null || file.equals("-") ? stdin : Files.readString(Path.of(file));
            var module = IrParser.parseModule(text);
            var diagnostics = Verifier.verify(module, stage);
            if (!diagnostics.isEmpty()) {
                var err = new StringBuilder();
                diagnostics.forEach(d -> err.append(d.render()).append('\n'));
                return new Result(1, "", err.toString());
            }
            var out = new StringBuilder();
            for (var function : module.functions()) {
                out.append(SPrinter.print(Structurizer.structure(function)));
            }
            return new Result(0, out.toString(), "");
        } catch (IOException e) {
            return new Result(2, "", "tc-lower: cannot read " + file + ": " + e.getMessage() + "\n");
        } catch (IrParseException e) {
            return new Result(1, "", e.diagnostic().render() + "\n");
        } catch (Structurizer.StructuringException e) {
            return new Result(1, "", "error " + e.getMessage() + "\n");
        }
    }
}
