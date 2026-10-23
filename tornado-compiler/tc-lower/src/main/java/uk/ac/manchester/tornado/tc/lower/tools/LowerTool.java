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
