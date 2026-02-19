/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXPrintf;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.drivers.ptx.graal.meta.PTXMemorySpace;

@NodeInfo(shortName = "printf")
public class PrintfNode extends FixedWithNextNode implements LIRLowerable, IterableNodeType {

    public static final NodeClass<PrintfNode> TYPE = NodeClass.create(PrintfNode.class);

    @Input
    private GlobalThreadIdNode xDim;
    @Input
    private GlobalThreadIdNode yDim;
    @Input
    private GlobalThreadIdNode zDim;
    @Input
    private FixedArrayNode argumentStack;
    @Input
    private PrintfStringNode inputString;

    public PrintfNode(ValueNode... values) {
        super(TYPE, StampFactory.forVoid());
        this.xDim = new GlobalThreadIdNode(ConstantNode.forInt(0));
        this.yDim = new GlobalThreadIdNode(ConstantNode.forInt(1));
        this.zDim = new GlobalThreadIdNode(ConstantNode.forInt(2));
        this.argumentStack = new FixedArrayNode(PTXArchitecture.localSpace, PTXKind.B32, PTXAssembler.PTXBinaryTemplate.NEW_LOCAL_BIT32_ARRAY, ConstantNode.forInt(3));
        this.inputString = new PrintfStringNode(values[0]);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitPrintf: xDim=%s, yDim=%s, zDim=%s", xDim, yDim, zDim);
        PTXLIRGenerator genTool = (PTXLIRGenerator) gen.getLIRGeneratorTool();

        Value stack = gen.operand(argumentStack);

        Value[] globalIDs = new Value[3];
        globalIDs[0] = gen.operand(xDim);
        globalIDs[1] = gen.operand(yDim);
        globalIDs[2] = gen.operand(zDim);

        Value format = gen.operand(inputString);

        genTool.append(new PTXLIRStmt.StoreStmt(
                new PTXUnary.MemoryAccess(PTXArchitecture.localSpace, stack, new ConstantValue(LIRKind.value(PTXKind.S32), JavaConstant.forInt(0 * PTXKind.S32.getSizeInBytes()))), globalIDs[0]));
        genTool.append(new PTXLIRStmt.StoreStmt(
                new PTXUnary.MemoryAccess(PTXArchitecture.localSpace, stack, new ConstantValue(LIRKind.value(PTXKind.S32), JavaConstant.forInt(1 * PTXKind.S32.getSizeInBytes()))), globalIDs[1]));
        genTool.append(new PTXLIRStmt.StoreStmt(
                new PTXUnary.MemoryAccess(PTXArchitecture.localSpace, stack, new ConstantValue(LIRKind.value(PTXKind.S32), JavaConstant.forInt(2 * PTXKind.S32.getSizeInBytes()))), globalIDs[2]));

        Variable globalAddrFormat = genTool.newVariable(LIRKind.value(PTXKind.B64));
        Variable globalAddrStack = genTool.newVariable(LIRKind.value(PTXKind.B64));
        genTool.append(new PTXLIRStmt.ConvertAddressStmt(globalAddrFormat, format, PTXMemorySpace.GLOBAL));

        genTool.append(new PTXLIRStmt.ConvertAddressStmt(globalAddrStack, stack, PTXMemorySpace.LOCAL));

        genTool.append(new PTXLIRStmt.ExprStmt(new PTXPrintf(globalAddrFormat, globalAddrStack)));
    }

}
