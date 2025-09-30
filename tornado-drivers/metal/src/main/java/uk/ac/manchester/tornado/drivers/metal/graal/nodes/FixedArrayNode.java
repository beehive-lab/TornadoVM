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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture.MetalMemoryBase;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler.MetalBinaryTemplate;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalBinary;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;

@NodeInfo
public class FixedArrayNode extends FixedNode implements LIRLowerable {

    public static final NodeClass<FixedArrayNode> TYPE = NodeClass.create(FixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected MetalKind elementKind;
    protected MetalMemoryBase memoryRegister;
    protected ResolvedJavaType elementType;
    protected MetalBinaryTemplate arrayTemplate;
    protected MetalBinaryTemplate pointerTemplate;

    public FixedArrayNode(MetalMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elementType = elementType;
        this.elementKind = MetalKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = MetalKind.resolvePrivateTemplateType(elementType);
        this.pointerTemplate = MetalKind.resolvePrivatePointerTemplate(elementType);
    }

    public MetalMemoryBase getMemoryRegister() {
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
        final MetalBinary.Expr declaration = new MetalBinary.Expr(arrayTemplate, lirKind, variable, lengthValue);
        final MetalLIRStmt.ExprStmt arrayExpr = new MetalLIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(arrayExpr);
        // generate pointer to private array
        final Variable ptr = gen.getLIRGeneratorTool().newVariable(lirKind);
        final MetalBinary.Expr declarationPtr = new MetalBinary.Expr(pointerTemplate, lirKind, ptr, variable);
        final MetalLIRStmt.ExprStmt ptrExpr = new MetalLIRStmt.ExprStmt(declarationPtr);
        gen.getLIRGeneratorTool().append(ptrExpr);
        gen.setResult(this, ptr);
    }
}
