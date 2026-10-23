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
package uk.ac.manchester.tornado.tc.opt;

import java.util.Comparator;
import java.util.List;

import uk.ac.manchester.tornado.tc.ir.BlockArg;
import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;
import uk.ac.manchester.tornado.tc.ir.analysis.LoopForest;

/**
 * Loop-invariant code motion (pure instructions only): pure instructions whose
 * operands are all defined outside a loop move to the end of the loop's unique entry block. Pure
 * operations have no side effects and cannot trap (division is not pure), so executing them on the
 * entry path is always safe. Memory reads are not moved yet: that needs the memory-effect analysis.
 * Innermost loops are processed first, so values can hoist out of a whole nest.
 */
public final class LoopInvariantCodeMotion implements Pass {

    @Override
    public String name() {
        return "licm";
    }

    @Override
    public boolean run(Function function) {
        var tree = Analyses.dominators(function);
        var forest = Analyses.loops(function);
        var preds = function.predecessors();
        boolean changed = false;
        var loops = forest.loops().stream().sorted(Comparator.comparingInt((LoopForest.Loop l) -> l.depth()).reversed()).toList();
        for (var loop : loops) {
            var outside = preds.get(loop.header()).stream().filter(p -> !loop.contains(p)).toList();
            if (outside.size() != 1) {
                continue;
            }
            var anchor = outside.get(0).terminator().orElse(null);
            if (anchor == null) {
                continue;
            }
            boolean progress = true;
            while (progress) {
                progress = false;
                for (var block : tree.reversePostorder()) {
                    if (!loop.contains(block)) {
                        continue;
                    }
                    for (var inst : List.copyOf(block.insts())) {
                        if (inst.producesValue() && inst.opcode().isPure() && !(inst.opcode() instanceof Opcode.ConstInt) && !(inst.opcode() instanceof Opcode.ConstFloat)
                                && inst.operands().stream().allMatch(v -> definedOutside(v, loop))) {
                            // Constants count as invariant wherever they are: bring along those defined in the loop.
                            for (var operand : inst.operands()) {
                                if (operand instanceof Inst c && (c.opcode() instanceof Opcode.ConstInt || c.opcode() instanceof Opcode.ConstFloat) && c.block().map(loop::contains).orElse(false)) {
                                    c.moveBefore(anchor);
                                }
                            }
                            inst.moveBefore(anchor);
                            progress = true;
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean definedOutside(Value v, LoopForest.Loop loop) {
        return switch (v) {
            case Inst i -> i.opcode() instanceof Opcode.ConstInt || i.opcode() instanceof Opcode.ConstFloat || i.block().map(b -> !loop.contains(b)).orElse(false);
            case BlockArg a -> !loop.contains(a.block());
        };
    }
}
