/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.address.AddressNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAStampFactory;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary;

/**
 * Reads a packed {@code __half2} element out of a local/shared-memory array.
 *
 * <p>Bypasses Graal's generic {@link tornado.graal.compiler.nodes.memory.ReadNode} lowering:
 * {@code ReadNode.generate()} derives its LIR result kind from {@code getAccessStamp()}, which -
 * unlike the equivalent global-memory {@code HalfFloatArray.getHalf2(index)} read - resolves to a
 * raw word-sized (ULONG) kind instead of HALF2 for a local array's plain
 * {@code OffsetAddressNode(array, index)} address, producing a destination variable CUDA cannot
 * implicitly convert to/from {@code __half2} ("no suitable conversion function from __half2 to
 * unsigned long long"). A dedicated node with a hard-coded HALF2 result kind sidesteps that
 * inference entirely, mirroring the scalar-HALF equivalent, {@link ReadHalfFloatNode}.
 */
@NodeInfo
public class ReadHalf2Node extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<ReadHalf2Node> TYPE = NodeClass.create(ReadHalf2Node.class);

    @Input
    private AddressNode addressNode;
    @Input
    private ValueNode indexNode;

    public ReadHalf2Node(AddressNode addressNode, ValueNode indexNode) {
        super(TYPE, CUDAStampFactory.getStampFor(CUDAKind.HALF2));
        this.addressNode = addressNode;
        this.indexNode = indexNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(CUDAKind.HALF2));
        Value addressValue = generator.operand(addressNode);
        CUDAArchitecture.CUDAMemoryBase base = ((CUDAUnary.MemoryAccess) addressValue).getBase();
        CUDAUnary.CUDAAddressCast cast = new CUDAUnary.CUDAAddressCast(base, LIRKind.value(CUDAKind.HALF));
        Value index = generator.operand(indexNode);
        tool.append(new CUDALIRStmt.VectorLoadStmt(result, CUDABinaryIntrinsic.VLOAD2, index, cast, (CUDAUnary.MemoryAccess) addressValue));
        generator.setResult(this, result);
    }
}
