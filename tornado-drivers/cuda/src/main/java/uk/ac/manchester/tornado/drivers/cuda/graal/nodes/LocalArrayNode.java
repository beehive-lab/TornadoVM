/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * Authors: Juan Fumero, Michalis Padimitriou
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDABinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkLocalArray;

@NodeInfo
public class LocalArrayNode extends FixedNode implements LIRLowerable, MarkLocalArray, MemoryKill {

    public static final NodeClass<LocalArrayNode> TYPE = NodeClass.create(LocalArrayNode.class);

    @Input
    protected ValueNode length;
    protected CUDAArchitecture.CUDAMemoryBase memoryRegister;
    protected CUDAAssembler.CUDABinaryTemplate arrayTemplate;
    private CUDAKind kind;

    public LocalArrayNode(CUDAArchitecture.CUDAMemoryBase memoryRegister, ResolvedJavaType elementType, ValueNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.kind = CUDAKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = CUDAKind.resolveTemplateType(elementType, kind);
    }

    public LocalArrayNode(CUDAArchitecture.CUDAMemoryBase memoryRegister, ResolvedJavaType elementType, ValueNode length, CUDAKind kind) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.kind = kind;
        this.arrayTemplate = CUDAKind.resolveTemplateType(elementType, this.kind);
    }

    public LocalArrayNode(CUDAArchitecture.CUDAMemoryBase memoryRegister, JavaKind elementKind, ValueNode length) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.kind = CUDAKind.fromResolvedJavaKind(elementKind);
        this.arrayTemplate = CUDAKind.resolveTemplateType(elementKind, kind);
    }

    public CUDAArchitecture.CUDAMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    public ValueNode getLength() {
        return length;
    }

    public CUDAKind getCUDAKind() {
        return kind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(kind);
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        final CUDABinary.Expr declaration = new CUDABinary.Expr(arrayTemplate, lirKind, variable, lengthValue);

        final CUDALIRStmt.ExprStmt expr = new CUDALIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(expr);
        gen.setResult(this, variable);
    }

    @Override
    public boolean killsInit() {
        return false;
    }
}
