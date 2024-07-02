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

import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryTemplate;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXMemoryBase;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXStampFactory;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo
public class FixedArrayNode extends FixedNode implements LIRLowerable {

    public static final NodeClass<FixedArrayNode> TYPE = NodeClass.create(FixedArrayNode.class);

    @Input
    protected ConstantNode length;

    protected PTXKind elementKind;
    protected PTXMemoryBase memoryRegister;
    protected ResolvedJavaType elemenType;
    protected PTXBinaryTemplate arrayTemplate;

    public FixedArrayNode(PTXMemoryBase memoryRegister, ResolvedJavaType elementType, ConstantNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elemenType = elementType;
        this.elementKind = PTXKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = PTXKind.resolvePrivateTemplateType(elementType);
    }

    public FixedArrayNode(PTXMemoryBase memoryRegister, PTXKind ptxKind, PTXBinaryTemplate arrayTemplate, ConstantNode length) {
        super(TYPE, PTXStampFactory.getStampFor(ptxKind));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.elementKind = ptxKind;
        this.arrayTemplate = arrayTemplate;
    }

    public PTXMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    public void setMemoryLocation(PTXMemoryBase memoryRegister) {
        this.memoryRegister = memoryRegister;
    }

    public ResolvedJavaType getElementType() {
        return elemenType;
    }

    public ConstantNode getLength() {
        return length;
    }

    public void setElementKind(PTXKind kind) {
        this.elementKind = kind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitFixedArray: elementKind=%s length=%s", elementKind, length);
        /*
         * using as_T reinterprets the data as type T - consider: float x = (float) 1;
         * and int value = 1, float x = &(value);
         */
        final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(elementKind);
        final Variable variable = ((PTXLIRGenerator) gen.getLIRGeneratorTool()).newVariable(lirKind, true);
        final PTXBinary.Expr declaration = new PTXBinary.Expr(arrayTemplate, lirKind, variable, lengthValue);

        final PTXLIRStmt.ExprStmt expr = new PTXLIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(expr);
        gen.setResult(this, variable);
    }

}
