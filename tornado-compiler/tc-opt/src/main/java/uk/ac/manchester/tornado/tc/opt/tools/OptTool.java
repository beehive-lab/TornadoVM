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
package uk.ac.manchester.tornado.tc.opt.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.IrParseException;
import uk.ac.manchester.tornado.tc.ir.IrParser;
import uk.ac.manchester.tornado.tc.ir.IrPrinter;
import uk.ac.manchester.tornado.tc.ir.Verifier;
import uk.ac.manchester.tornado.tc.opt.Canonicalizer;
import uk.ac.manchester.tornado.tc.opt.CfgSimplify;
import uk.ac.manchester.tornado.tc.opt.DeadCodeElimination;
import uk.ac.manchester.tornado.tc.opt.GlobalValueNumbering;
import uk.ac.manchester.tornado.tc.opt.LoopInvariantCodeMotion;
import uk.ac.manchester.tornado.tc.opt.Pass;
import uk.ac.manchester.tornado.tc.opt.PassManager;

/**
 * The {@code tc-opt} tool: runs passes on TC-SSA and prints the result, verifying after every pass.
 * {@code tc-opt [--passes=canonicalize,cfg-simplify,dce,gvn,licm | -O] [--stage=post-abi]
 * [--pass-limit=N] [--no-loc] <file | ->}.
 */
public final class OptTool {

    public record Result(int exitCode, String stdout, String stderr) {
    }

    private OptTool() {
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
        var stage = Verifier.Stage.PRE_ABI;
        String passes = null;
        boolean standard = false;
        boolean locations = true;
        int limit = Integer.MAX_VALUE;
        String file = null;
        for (var arg : args) {
            if (arg.startsWith("--passes=")) {
                passes = arg.substring("--passes=".length());
            } else if (arg.equals("-O")) {
                standard = true;
            } else if (arg.equals("--stage=post-abi")) {
                stage = Verifier.Stage.POST_ABI;
            } else if (arg.startsWith("--pass-limit=")) {
                limit = Integer.parseInt(arg.substring("--pass-limit=".length()));
            } else if (arg.equals("--no-loc")) {
                locations = false;
            } else if (arg.startsWith("-") && !arg.equals("-")) {
                return new Result(2, "", "tc-opt: unknown option " + arg + "\n");
            } else {
                file = arg;
            }
        }
        try {
            var text = file == null || file.equals("-") ? stdin : Files.readString(Path.of(file));
            var module = IrParser.parseModule(text);
            var err = new StringBuilder();
            Verifier.verify(module, stage).forEach(d -> err.append(d.render()).append('\n'));
            if (!err.isEmpty()) {
                return new Result(1, "", err.toString());
            }
            for (var function : module.functions()) {
                PassManager pm;
                if (standard || passes == null) {
                    pm = PassManager.standard(stage);
                } else {
                    pm = new PassManager(stage);
                    for (var name : passes.split(",")) {
                        pm.add(pass(name));
                    }
                }
                pm.passLimit(limit).run(function);
            }
            return new Result(0, new IrPrinter(locations).printModule(module), "");
        } catch (IOException e) {
            return new Result(2, "", "tc-opt: cannot read " + file + ": " + e.getMessage() + "\n");
        } catch (IrParseException e) {
            return new Result(1, "", e.diagnostic().render() + "\n");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return new Result(1, "", "error " + e.getMessage() + "\n");
        }
    }

    private static Pass pass(String name) {
        return switch (name) {
            case "canonicalize" -> new Canonicalizer();
            case "cfg-simplify" -> new CfgSimplify();
            case "dce" -> new DeadCodeElimination();
            case "gvn" -> new GlobalValueNumbering();
            case "licm" -> new LoopInvariantCodeMotion();
            default -> throw new IllegalArgumentException("unknown pass '" + name + "'");
        };
    }
}
