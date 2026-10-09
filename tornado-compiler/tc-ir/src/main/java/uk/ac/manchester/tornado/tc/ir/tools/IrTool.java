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
package uk.ac.manchester.tornado.tc.ir.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import uk.ac.manchester.tornado.tc.ir.Diagnostic;
import uk.ac.manchester.tornado.tc.ir.IrModule;
import uk.ac.manchester.tornado.tc.ir.IrParseException;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.ir.analysis.DominatorTree;

/**
 * The {@code tc-ir} tool: reads TC-SSA text and prints it back (a round-trip), verifies it, or prints
 * analyses. Used by the lit tests and by the {@code tc} command-line interface.
 *
 * <pre>
 * tc-ir [options] &lt;file | -&gt;
 *   --verify                 run the verifier; on errors print them to stderr and exit 1
 *   --verify-diagnostics     compare parse/verifier errors with "expected-error" annotations
 *   --stage=pre-abi|post-abi verifier stage (default pre-abi)
 *   --dom                    print the dominator tree of every function
 *   --no-loc                 do not print source locations
 * </pre>
 *
 * <p>Annotations for {@code --verify-diagnostics} are comments in the input, in MLIR's style:
 * {@code ; expected-error {{text}}} refers to its own line, {@code ; expected-error@+1 {{text}}}
 * to the next line, {@code @-1} to the previous one, {@code @*} to any line (for diagnostics on
 * blocks or functions). The text must occur in "code: message".</p>
 */
public final class IrTool {

    public record Result(int exitCode, String stdout, String stderr) {
    }

    private static final Pattern EXPECTED = Pattern.compile(";\\s*expected-error(?:@([+-]\\d+|\\*))?\\s*\\{\\{(.*?)\\}\\}");

    private static final int ANY_LINE = Integer.MIN_VALUE;

    private IrTool() {
    }

    public static Result run(List<String> args, String stdin) {
        boolean verify = false;
        boolean verifyDiagnostics = false;
        boolean dom = false;
        boolean locations = true;
        var stage = Verifier.Stage.PRE_ABI;
        String file = null;
        for (var arg : args) {
            switch (arg) {
                case "--verify" -> verify = true;
                case "--verify-diagnostics" -> verifyDiagnostics = true;
                case "--dom" -> dom = true;
                case "--no-loc" -> locations = false;
                case "--stage=pre-abi" -> stage = Verifier.Stage.PRE_ABI;
                case "--stage=post-abi" -> stage = Verifier.Stage.POST_ABI;
                default -> {
                    if (arg.startsWith("-") && !arg.equals("-")) {
                        return new Result(2, "", "tc-ir: unknown option " + arg + "\n");
                    }
                    file = arg;
                }
            }
        }
        String text;
        try {
            text = file == null || file.equals("-") ? stdin : Files.readString(Path.of(file));
        } catch (IOException e) {
            return new Result(2, "", "tc-ir: cannot read " + file + ": " + e.getMessage() + "\n");
        }
        if (verifyDiagnostics) {
            return verifyDiagnostics(text, stage);
        }
        IrModule module;
        try {
            module = IrParser.parseModule(text);
        } catch (IrParseException e) {
            return new Result(1, "", e.diagnostic().render() + "\n");
        }
        if (verify) {
            var diagnostics = Verifier.verify(module, stage);
            if (!diagnostics.isEmpty()) {
                var err = new StringBuilder();
                diagnostics.forEach(d -> err.append(d.render()).append('\n'));
                return new Result(1, "", err.toString());
            }
        }
        if (dom) {
            return new Result(0, dominatorTrees(module), "");
        }
        return new Result(0, new IrPrinter(locations).printModule(module), "");
    }

    private static String dominatorTrees(IrModule module) {
        var sb = new StringBuilder();
        for (var function : module.functions()) {
            var printer = new IrPrinter(false);
            printer.printFunction(function);
            var tree = new DominatorTree(function);
            sb.append("dominators @").append(function.name()).append('\n');
            for (var block : function.blocks()) {
                sb.append("  ^").append(printer.blockName(block)).append(": ");
                if (!tree.isReachable(block)) {
                    sb.append("unreachable");
                } else {
                    sb.append("idom ").append(tree.immediateDominator(block).map(b -> "^" + printer.blockName(b)).orElse("-"));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static Result verifyDiagnostics(String text, Verifier.Stage stage) {
        record Expected(int line, String text) {
        }
        var expected = new ArrayList<Expected>();
        var lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            var m = EXPECTED.matcher(lines[i]);
            while (m.find()) {
                if ("*".equals(m.group(1))) {
                    expected.add(new Expected(ANY_LINE, m.group(2)));
                } else {
                    int offset = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
                    expected.add(new Expected(i + 1 + offset, m.group(2)));
                }
            }
        }
        List<Diagnostic> actual;
        try {
            actual = Verifier.verify(IrParser.parseModule(text), stage);
        } catch (IrParseException e) {
            actual = List.of(e.diagnostic());
        }
        var err = new StringBuilder();
        var unmatched = new ArrayList<>(actual);
        for (var e : expected) {
            var hit = unmatched.stream().filter(d -> (e.line == ANY_LINE || d.line() == e.line) && (d.code() + ": " + d.message()).contains(e.text)).findFirst();
            if (hit.isPresent()) {
                unmatched.remove(hit.get());
            } else {
                err.append(e.line == ANY_LINE ? "any line" : "line " + e.line).append(": expected error '").append(e.text).append("' was not produced\n");
            }
        }
        for (var d : unmatched) {
            err.append("unexpected ").append(d.render()).append('\n');
        }
        return err.isEmpty() ? new Result(0, "", "") : new Result(1, "", err.toString());
    }
}
