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
package uk.ac.manchester.tornado.tc.tornado;

import java.util.List;

import uk.ac.manchester.tornado.tc.ir.Function;
import uk.ac.manchester.tornado.tc.ir.Inst;
import uk.ac.manchester.tornado.tc.ir.IrBuilder;
import uk.ac.manchester.tornado.tc.ir.Opcode;
import uk.ac.manchester.tornado.tc.ir.Value;
import uk.ac.manchester.tornado.tc.ir.analysis.Analyses;
import uk.ac.manchester.tornado.tc.ir.analysis.CountedLoop;

/**
 * Batched execution: each device buffer holds one chunk of every array, starting at element
 * {@code offset}. In the Java program the {@code @Parallel} index is global, so: every use of the
 * index (other than the loop's own bound test and increment, which count over the chunk) becomes
 * {@code i + offset}, and every array index that depends on it is made chunk-local again by
 * subtracting {@code offset}. An index that is exactly the parallel index reads the chunk-local
 * {@code i} directly. Values and indices are handled per use, so an expression used as both
 * ({@code k = i + 1; a[k - 1] = k}) is right in both roles.
 */
public final class BatchOffset {

    private BatchOffset() {
    }

    public static void run(Function function, long offset) {
        if (offset == 0) {
            return;
        }
        var forest = Analyses.loops(function);
        for (var header : function.blocks()) {
            for (var a : header.attributes()) {
                if (!a.name().equals("parallel")) {
                    continue;
                }
                var iv = header.args().get(a.integer("iv"));
                // The loop's own compare and increment count chunk-local iterations; everything else sees the global index.
                var counted = forest.loopWithHeader(header).flatMap(loop -> CountedLoop.of(loop, a.integer("iv")));
                var first = header.insts().get(0);
                var b = IrBuilder.insertingBefore(first);
                // The global index of a batch element is within the task's int range: no signed wrap.
                var global = ParallelLoopLowering.noSignedWrap(b.binary(Opcode.BinaryOp.ADD, iv, b.constInt(iv.type(), offset)));
                global.setName(iv.name() + "_global");
                for (var user : List.copyOf(iv.users())) {
                    if (user == global || counted.map(c -> c.controls(user)).orElse(false)) {
                        continue;
                    }
                    for (int k = 0; k < user.operandCount(); k++) {
                        if (user.operand(k) == iv) {
                            user.setOperand(k, global);
                        }
                    }
                }
                localiseIndices(function, iv, global, offset);
                if (!global.hasUses()) {
                    global.erase();
                }
            }
        }
    }

    /** Array accesses whose index depends on the global index address the chunk: index - offset. */
    private static void localiseIndices(Function function, Value iv, Inst global, long offset) {
        for (var block : function.blocks()) {
            for (var inst : List.copyOf(block.insts())) {
                if (!(inst.opcode() instanceof Opcode.ArrayLoad || inst.opcode() instanceof Opcode.ArrayStore)) {
                    continue;
                }
                var index = inst.operand(1);
                if (index == global) {
                    inst.setOperand(1, iv);
                } else if (dependsOn(index, global, new java.util.HashSet<>())) {
                    var b = IrBuilder.insertingBefore(inst);
                    var local = b.binary(Opcode.BinaryOp.SUB, index, b.constInt(index.type(), offset));
                    inst.setOperand(1, local);
                }
            }
        }
    }

    private static boolean dependsOn(Value v, Value target, java.util.Set<Value> seen) {
        if (v == target) {
            return true;
        }
        if (!(v instanceof Inst inst) || !seen.add(v)) {
            return false;
        }
        for (var op : inst.operands()) {
            if (dependsOn(op, target, seen)) {
                return true;
            }
        }
        return false;
    }
}
