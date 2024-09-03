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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBinary;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;

/**
 * This node generates a pointer copy between two arrays in private memory.
 */
@NodeInfo
public class FixedArrayCopyNode extends FloatingNode implements LIRLowerable  {

    public static final NodeClass<FixedArrayCopyNode> TYPE = NodeClass.create(FixedArrayCopyNode.class);

    @Input
    protected ValuePhiNode conditionalPhiNode;

    protected ResolvedJavaType elementType;
    protected OCLAssembler.OCLBinaryTemplate pointerCopyTemplate;
    protected OCLArchitecture.OCLMemoryBase memoryRegister;

    public FixedArrayCopyNode(ValuePhiNode conditionalPhiNode, ResolvedJavaType elementType, OCLArchitecture.OCLMemoryBase memoryRegister) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.conditionalPhiNode = conditionalPhiNode;
        this.elementType = elementType;
        this.memoryRegister = memoryRegister;
        this.pointerCopyTemplate = OCLKind.resolvePrivatePointerCopyTemplate(elementType);
    }

    public OCLArchitecture.OCLMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable ptr = gen.getLIRGeneratorTool().newVariable(lirKind);
        Value fixedArrayValue = gen.operand(conditionalPhiNode);
        final OCLBinary.Expr declarationPtr = new OCLBinary.Expr(pointerCopyTemplate, lirKind, ptr, fixedArrayValue);
        final OCLLIRStmt.ExprStmt ptrExpr = new OCLLIRStmt.ExprStmt(declarationPtr);
        gen.getLIRGeneratorTool().append(ptrExpr);
        gen.setResult(this, ptr);
    }
}
