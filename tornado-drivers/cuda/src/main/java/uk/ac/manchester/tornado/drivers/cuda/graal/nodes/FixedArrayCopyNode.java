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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDABinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * This node generates a pointer copy between two arrays in private memory.
 */
@NodeInfo
public class FixedArrayCopyNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<FixedArrayCopyNode> TYPE = NodeClass.create(FixedArrayCopyNode.class);

    @Input
    protected ValuePhiNode conditionalPhiNode;

    protected ResolvedJavaType elementType;
    protected CUDAAssembler.CUDABinaryTemplate pointerCopyTemplate;
    protected CUDAArchitecture.CUDAMemoryBase memoryRegister;

    public FixedArrayCopyNode(ValuePhiNode conditionalPhiNode, ResolvedJavaType elementType, CUDAArchitecture.CUDAMemoryBase memoryRegister) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.conditionalPhiNode = conditionalPhiNode;
        this.elementType = elementType;
        this.memoryRegister = memoryRegister;
        this.pointerCopyTemplate = CUDAKind.resolvePrivatePointerCopyTemplate(elementType);
    }

    public CUDAArchitecture.CUDAMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable ptr = gen.getLIRGeneratorTool().newVariable(lirKind);
        Value fixedArrayValue = gen.operand(conditionalPhiNode);
        final CUDABinary.Expr declarationPtr = new CUDABinary.Expr(pointerCopyTemplate, lirKind, ptr, fixedArrayValue);
        final CUDALIRStmt.ExprStmt ptrExpr = new CUDALIRStmt.ExprStmt(declarationPtr);
        gen.getLIRGeneratorTool().append(ptrExpr);
        gen.setResult(this, ptr);
    }
}
