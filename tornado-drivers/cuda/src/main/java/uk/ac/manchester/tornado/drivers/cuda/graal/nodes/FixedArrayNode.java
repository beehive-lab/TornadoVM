/*
 * Copyright (c) 2018, 2020, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.core.common.type.TypeReference;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ConstantNode;
import tornado.graal.compiler.nodes.FixedNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture.CUDAMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler.CUDABinaryTemplate;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDABinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

@NodeInfo
public class FixedArrayNode extends FixedNode implements LIRLowerable {

    public static final NodeClass<FixedArrayNode> TYPE = NodeClass.create(FixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected CUDAKind elementKind;
    protected CUDAMemoryBase memoryRegister;
    protected ResolvedJavaType elementType;
    protected CUDABinaryTemplate arrayTemplate;
    protected CUDABinaryTemplate pointerTemplate;

    public FixedArrayNode(CUDAMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elementType = elementType;
        this.elementKind = CUDAKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = CUDAKind.resolvePrivateTemplateType(elementType);
        this.pointerTemplate = CUDAKind.resolvePrivatePointerTemplate(elementType);
    }

    public CUDAMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    public ConstantNode getLength() {
        return length;
    }

    public ResolvedJavaType getElementType() {
        return elementType;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // generate declaration of private array
        final Value lengthValue = gen.operand(length);
        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        final CUDABinary.Expr declaration = new CUDABinary.Expr(arrayTemplate, lirKind, variable, lengthValue);
        final CUDALIRStmt.ExprStmt arrayExpr = new CUDALIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(arrayExpr);
        // generate pointer to private array
        final Variable ptr = gen.getLIRGeneratorTool().newVariable(lirKind);
        final CUDABinary.Expr declarationPtr = new CUDABinary.Expr(pointerTemplate, lirKind, ptr, variable);
        final CUDALIRStmt.ExprStmt ptrExpr = new CUDALIRStmt.ExprStmt(declarationPtr);
        gen.getLIRGeneratorTool().append(ptrExpr);
        gen.setResult(this, ptr);
    }
}
