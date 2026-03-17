/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.phases;

import java.util.Optional;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.phases.Phase;

import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXAddressNode;

/**
 * Graal HIR phase that identifies array-indexing address patterns and annotates
 * the {@link PTXAddressNode} with the original integer index, enabling the
 * CUDA C code generator to emit {@code ((TYPE*)base)[idx]} instead of the
 * equivalent but harder-to-optimize 4-step pointer-arithmetic chain:
 *
 * <pre>
 *   rsd0 = (long long)(int)rsi5;    // sign-extend
 *   rsd1 = rsd0 << 2;               // scale by element size
 *   rud4 = rud3 + rsd1;             // base + byte-offset
 *   rfi1 = *((float*)(rud4));       // dereference
 * </pre>
 *
 * <p>The phase detects the pattern:
 * <pre>
 *   ReadNode / WriteNode
 *     └─ OffsetAddressNode (or PTXAddressNode)
 *          ├─ base  : pointer to array data
 *          └─ offset: LeftShiftNode(SignExtendNode(intIdx, 64), N)
 *                     or LeftShiftNode(ZeroExtendNode(intIdx, 64), N)
 * </pre>
 *
 * <p>When found, calls {@link PTXAddressNode#setIntIndex(ValueNode)} so
 * that {@code PTXAddressNode.generate()} can pass the integer index to the
 * assembler.  For the PTX backend the emitted code is identical; for the
 * CUDA C backend {@link uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXToCUDACTranslator}
 * post-processes the output and collapses the chain.
 *
 * <p>This phase must run <em>after</em>
 * {@link org.graalvm.compiler.phases.common.AddressLoweringByNodePhase}
 * (when {@link OffsetAddressNode} has already been replaced by
 * {@link PTXAddressNode}) but <em>before</em> LIR generation.
 */
public class PTXIndexedAccessPhase extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph) {
        // Process ReadNode accesses
        graph.getNodes().filter(ReadNode.class).forEach(read -> {
            if (read.getAddress() instanceof PTXAddressNode addr) {
                tryAnnotateIndex(addr);
            }
        });
        // Process WriteNode accesses
        graph.getNodes().filter(WriteNode.class).forEach(write -> {
            if (write.getAddress() instanceof PTXAddressNode addr) {
                tryAnnotateIndex(addr);
            }
        });
    }

    /**
     * Checks whether {@code addr}'s offset matches the pattern
     * {@code shl(signExtend/zeroExtend(intIdx), N)} and, if so,
     * annotates {@code addr} with the integer index node.
     */
    private static void tryAnnotateIndex(PTXAddressNode addr) {
        if (addr.getIntIndex() != null) {
            return; // already annotated
        }
        ValueNode offset = addr.getIndex();
        if (offset == null) {
            return;
        }

        // Pattern: LeftShiftNode(extend(intIdx), N)
        if (offset instanceof LeftShiftNode shl) {
            ValueNode shifted = shl.getX();
            ValueNode intIdx = extractIntIndex(shifted);
            if (intIdx != null) {
                addr.setIntIndex(intIdx);
            }
        }
    }

    /**
     * If {@code node} is a sign- or zero-extension of an int (32-bit) value,
     * returns the 32-bit input; otherwise returns {@code null}.
     */
    private static ValueNode extractIntIndex(ValueNode node) {
        if (node instanceof SignExtendNode se && se.getInputBits() == 32) {
            return se.getValue();
        }
        if (node instanceof ZeroExtendNode ze && ze.getInputBits() == 32) {
            return ze.getValue();
        }
        // Handle NarrowNode wrapping a wider extension (rare)
        if (node instanceof NarrowNode narrow) {
            return extractIntIndex(narrow.getValue());
        }
        return null;
    }
}
