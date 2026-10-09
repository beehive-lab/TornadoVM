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
