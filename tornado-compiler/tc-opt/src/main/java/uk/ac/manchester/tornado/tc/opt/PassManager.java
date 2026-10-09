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
package uk.ac.manchester.tornado.tc.opt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Verifier;

/**
 * Runs passes in order with the debugging controls of the plan: verification after every pass
 * ({@code verifyEach}), a limit on the number of pass runs to bisect a miscompilation to the first
 * bad pass ({@code passLimit}, like LLVM's opt-bisect-limit), and an observer called after each pass
 * (dumps, JFR events, reports).
 */
public final class PassManager {

    /** One executed pass: whether it changed the function and how long it took. */
    public record Run(String pass, boolean changed, long nanos) {
    }

    private final List<Pass> passes = new ArrayList<>();
    private final Verifier.Stage stage;
    private boolean verifyEach = true;
    private int passLimit = Integer.MAX_VALUE;
    private BiConsumer<Run, Function> observer = (run, function) -> {
    };

    public PassManager(Verifier.Stage stage) {
        this.stage = stage;
    }

    public PassManager add(Pass pass) {
        passes.add(pass);
        return this;
    }

    public PassManager verifyEach(boolean verify) {
        this.verifyEach = verify;
        return this;
    }

    /** Runs at most {@code limit} passes over the whole pipeline, then stops (bisection). */
    public PassManager passLimit(int limit) {
        this.passLimit = limit;
        return this;
    }

    public PassManager observe(BiConsumer<Run, Function> observer) {
        this.observer = observer;
        return this;
    }

    public List<Run> run(Function function) {
        var runs = new ArrayList<Run>();
        for (var pass : passes) {
            if (runs.size() >= passLimit) {
                break;
            }
            long start = System.nanoTime();
            boolean changed = pass.run(function);
            var run = new Run(pass.name(), changed, System.nanoTime() - start);
            runs.add(run);
            if (verifyEach) {
                var problems = Verifier.verify(function, stage);
                if (!problems.isEmpty()) {
                    var sb = new StringBuilder("pass '" + pass.name() + "' produced invalid IR in @" + function.name() + " (TC-E9001):");
                    problems.forEach(d -> sb.append('\n').append(d.render()));
                    throw new IllegalStateException(sb.toString());
                }
            }
            observer.accept(run, function);
        }
        return runs;
    }

    /** The standard optimisation pipeline, run to a fixed point of the cheap passes. */
    public static PassManager standard(Verifier.Stage stage) {
        return new PassManager(stage)
                .add(new Canonicalizer()).add(new CfgSimplify()).add(new DeadCodeElimination())
                .add(new GlobalValueNumbering()).add(new LoopInvariantCodeMotion())
                .add(new Canonicalizer()).add(new GlobalValueNumbering()).add(new CfgSimplify()).add(new DeadCodeElimination());
    }
}
