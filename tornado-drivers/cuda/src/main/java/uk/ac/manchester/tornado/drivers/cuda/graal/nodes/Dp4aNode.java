/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Computes a {@code __dp4a} dot-product-accumulate over four signed int8 values loaded
 * from each of two operands. Maps to the CUDA {@code __dp4a} device builtin (compute
 * capability >= 6.1), the CUDA-C counterpart of the PTX {@code dp4a.s32.s32}.
 * <p>
 * Operand A is always a global {@code Int8Array} (its buffer carries the standard array
 * header). Operand B may be either a global {@code Int8Array} or a local (shared-memory)
 * byte array; in the latter case there is no array header to skip.
 */
@NodeInfo
public class Dp4aNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<Dp4aNode> TYPE = NodeClass.create(Dp4aNode.class);

    @Input
    private ValueNode a;

    @Input
    private ValueNode b;

    @Input
    private ValueNode c;

    @Input
    private ValueNode offset_a;

    @Input
    private ValueNode offset_b;

    private static final long HEADER_SIZE = TornadoNativeArray.ARRAY_HEADER;

    public Dp4aNode(ValueNode a, ValueNode offset_a, ValueNode b, ValueNode offset_b, ValueNode c) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.a = a;
        this.b = b;
        this.c = c;
        this.offset_a = offset_a;
        this.offset_b = offset_b;
    }

    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));

        Value baseA = generator.operand(a);
        Value offsetAValue = generator.operand(offset_a);
        Value baseB = generator.operand(b);
        Value offsetBValue = generator.operand(offset_b);
        Value accumulator = generator.operand(c);

        // Local (shared-memory) arrays have no Panama array header; global Int8Arrays do.
        boolean bHasHeader = !(b instanceof LocalArrayNode);

        tool.append(new CUDALIRStmt.Dp4aStmt(result, baseA, offsetAValue, baseB, offsetBValue, accumulator, bHasHeader, HEADER_SIZE));
        generator.setResult(this, result);
    }

}
