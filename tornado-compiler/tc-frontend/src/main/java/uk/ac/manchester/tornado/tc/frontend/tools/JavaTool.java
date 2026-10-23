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
package uk.ac.manchester.tornado.tc.frontend.tools;

import java.util.List;

import uk.ac.manchester.tornado.tc.frontend.Frontend;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Verifier;

/**
 * The {@code tc-java} tool: translates a Java method from class files into TC-SSA, verifies it and
 * prints it. {@code tc-java [--no-loc] <classpath> <binary.class.Name.method>}.
 */
public final class JavaTool {

    public record Result(int exitCode, String stdout, String stderr) {
    }

    private JavaTool() {
    }

    /** Command-line entry point. */
    public static void main(String[] args) {
        var result = run(List.of(args), "");
        System.out.print(result.stdout());
        System.err.print(result.stderr());
        System.exit(result.exitCode());
    }

    public static Result run(List<String> args, String stdin) {
        boolean locations = true;
        var positional = new java.util.ArrayList<String>();
        for (var arg : args) {
            if (arg.equals("--no-loc")) {
                locations = false;
            } else if (arg.startsWith("-")) {
                return new Result(2, "", "tc-java: unknown option " + arg + "\n");
            } else {
                positional.add(arg);
            }
        }
        if (positional.size() != 2) {
            return new Result(2, "", "usage: tc-java [--no-loc] <classpath> <binary.class.Name.method>\n");
        }
        var target = positional.get(1);
        var dot = target.lastIndexOf('.');
        var result = Frontend.translate(Frontend.BytecodeSource.classpath(positional.get(0)), target.substring(0, dot), target.substring(dot + 1), null);
        var err = new StringBuilder();
        result.diagnostics().forEach(d -> err.append(d.render()).append('\n'));
        if (result.function().isEmpty()) {
            return new Result(1, "", err.toString());
        }
        var function = result.function().get();
        Verifier.verify(function).forEach(d -> err.append("internal error: frontend produced invalid IR: ").append(d.render()).append('\n'));
        var text = new IrPrinter(locations).printFunction(function);
        return err.isEmpty() ? new Result(0, text, "") : new Result(1, text, err.toString());
    }
}
