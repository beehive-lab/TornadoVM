/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.SingleMemoryKill;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Graal IR node for the read-modify-write atomics on {@link uk.ac.manchester.tornado.api.KernelContext}:
 * {@code atomicCAS}, {@code atomicExchange}, {@code atomicMin} and {@code atomicMax} over an element of a
 * local (shared) array. Every one of them returns the element's previous value, which is what the CUDA
 * intrinsics return.
 *
 * <p>Extends {@link FixedWithNextNode} because these have a side effect on memory: they must keep their
 * position relative to other accesses and must not be duplicated or folded away. It is also a
 * {@link SingleMemoryKill} over {@link LocationIdentity#any()}: without that the compiler treats an
 * earlier load of the same element as still valid and reuses it, so a value the atomic (or another
 * thread) wrote is never read back - the store after the atomic ends up writing the pre-atomic register.
 */
@NodeInfo(shortName = "CUDAAtomicRmw")
public class CUDAAtomicRmwNode extends FixedWithNextNode implements LIRLowerable, SingleMemoryKill {

    public static final NodeClass<CUDAAtomicRmwNode> TYPE = NodeClass.create(CUDAAtomicRmwNode.class);

    private final CUDALIRStmt.AtomicRmwStmt.Mode mode;

    @Input
    private ValueNode array;
    @Input
    private ValueNode index;
    @Input
    private ValueNode value;
    /** Only used by {@link CUDALIRStmt.AtomicRmwStmt.Mode#CAS}; null otherwise. */
    @OptionalInput
    private ValueNode expected;

    public CUDAAtomicRmwNode(CUDALIRStmt.AtomicRmwStmt.Mode mode, ValueNode array, ValueNode index, ValueNode expected, ValueNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.mode = mode;
        this.array = array;
        this.index = index;
        this.expected = expected;
        this.value = value;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        Value expectedOperand = (expected == null) ? null : gen.operand(expected);
        tool.append(new CUDALIRStmt.AtomicRmwStmt(mode, result, gen.operand(array), gen.operand(index), expectedOperand, gen.operand(value)));
        gen.setResult(this, result);
    }
}
