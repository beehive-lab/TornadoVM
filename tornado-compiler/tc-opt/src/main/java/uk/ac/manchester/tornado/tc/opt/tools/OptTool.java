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
import uk.ac.manchester.tornado.tc.opt.FullLoopUnroll;
import uk.ac.manchester.tornado.tc.opt.GlobalValueNumbering;
import uk.ac.manchester.tornado.tc.opt.LoopInvariantCodeMotion;
import uk.ac.manchester.tornado.tc.opt.FloatNarrowing;
import uk.ac.manchester.tornado.tc.opt.FmaRematerialization;
import uk.ac.manchester.tornado.tc.opt.NativeHalfArithmetic;
import uk.ac.manchester.tornado.tc.opt.NoWrapInference;
import uk.ac.manchester.tornado.tc.opt.MathPeepholes;
import uk.ac.manchester.tornado.tc.opt.Pass;
import uk.ac.manchester.tornado.tc.opt.PassManager;

/**
 * The {@code tc-opt} tool: runs passes on TC-SSA and prints the result, verifying after every pass.
 * {@code tc-opt [--passes=canonicalize,cfg-simplify,dce,gvn,licm,unroll | -O] [--stage=post-abi]
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
            case "unroll" -> new FullLoopUnroll();
            case "math-peepholes" -> new MathPeepholes();
            case "fma-remat" -> new FmaRematerialization();
            case "float-narrowing" -> new FloatNarrowing();
            case "no-wrap" -> new NoWrapInference();
            case "native-half" -> new NativeHalfArithmetic();
            default -> throw new IllegalArgumentException("unknown pass '" + name + "'");
        };
    }
}
