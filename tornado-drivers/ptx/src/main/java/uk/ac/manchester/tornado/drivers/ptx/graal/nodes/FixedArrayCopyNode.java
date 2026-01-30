/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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

import jdk.vm.ci.meta.RawConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXUnary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;

/**
 * This node generates a pointer copy between two arrays in private memory.
 */
@NodeInfo
public class FixedArrayCopyNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<FixedArrayCopyNode> TYPE = NodeClass.create(FixedArrayCopyNode.class);

    @Input
    protected ValuePhiNode conditionalPhiNode;
    @Input
    protected ValueNode address;

    protected ResolvedJavaType elementType;
    protected PTXAssembler.PTXBinaryTemplate pointerCopyTemplate;
    protected PTXArchitecture.PTXMemoryBase memoryRegister;

    public FixedArrayCopyNode(ValuePhiNode conditionalPhiNode, ResolvedJavaType elementType, PTXArchitecture.PTXMemoryBase memoryRegister, ValueNode address) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.conditionalPhiNode = conditionalPhiNode;
        this.elementType = elementType;
        this.memoryRegister = memoryRegister;
        this.pointerCopyTemplate = PTXKind.resolvePrivatePointerCopyTemplate(elementType);
        this.address = address;
    }

    public PTXArchitecture.PTXMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    private int getOffset() {
        PTXKind kind = PTXKind.fromResolvedJavaType(elementType);
        return switch (kind) {
            case F64, S64 -> 8;
            case F32, S32 -> 4;
            case S16 -> 2;
            case U16, S8 -> 1;
            default -> -1;
        };
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        final Value index = gen.operand(address);
        final Value arrayToBeCopied = gen.operand(conditionalPhiNode);
        // the kind of the fixed array
        PTXKind ptxKind = PTXKind.fromResolvedJavaType(elementType);
        LIRKind lirKind = LIRKind.value(ptxKind);
        // the kind of the variables that will store the base index and address
        LIRKind indexKind = LIRKind.value(PTXKind.S32);
        LIRKind addressBaseKind = LIRKind.value(PTXKind.U32);

        final Value offsetedIndex = tool.newVariable(indexKind);
        final Value baseAddress = tool.newVariable(addressBaseKind);
        int offset = getOffset();
        final Value offsetValue = tool.emitConstant(lirKind, new RawConstant(offset));
        tool.append(new PTXLIRStmt.PrivateArrayCopyStmt(index, arrayToBeCopied, offsetedIndex, baseAddress, offsetValue));

        final PTXBinary.Expr declarationPtr;
        final Value result;
        if (ptxKind.isF32() || ptxKind.isF64()) {
            result = tool.newVariable(lirKind);
            tool.append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(PTXAssembler.PTXUnaryOp.CVT_FLOAT_RNE, lirKind, baseAddress)));
            declarationPtr = new PTXBinary.Expr(pointerCopyTemplate, lirKind, result, baseAddress);
        } else if (ptxKind.isS64()) {
            result = tool.newVariable(lirKind);
            tool.append(new PTXLIRStmt.AssignStmt(result, new PTXUnary.Expr(PTXAssembler.PTXUnaryOp.CVT_FLOAT, lirKind, baseAddress)));
            declarationPtr = new PTXBinary.Expr(pointerCopyTemplate, lirKind, result, baseAddress);
        } else {
            declarationPtr = new PTXBinary.Expr(pointerCopyTemplate, lirKind, offsetedIndex, baseAddress);
            result = offsetedIndex;
        }

        final PTXLIRStmt.ExprStmt ptrExpr = new PTXLIRStmt.ExprStmt(declarationPtr);
        tool.append(ptrExpr);
        gen.setResult(this, result);
    }
}
