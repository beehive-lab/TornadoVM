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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture.PTXMemoryBase;
import static uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler.PTXBinaryTemplate;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkLocalArray;

/**
 * Generates the LIR for declaring a CUDA shared (local in OpenCL) memory array.
 * The terminology used in this class refers to the OpenCL Programming Model.
 */
@NodeInfo
public class LocalArrayNode extends FixedNode implements LIRLowerable, MarkLocalArray, MemoryKill {

    public static final NodeClass<LocalArrayNode> TYPE = NodeClass.create(LocalArrayNode.class);

    @Input
    protected ValueNode length;

    protected PTXMemoryBase memoryRegister;
    protected PTXBinaryTemplate arrayTemplate;
    private PTXKind kind;

    public LocalArrayNode(PTXMemoryBase memoryRegister, ResolvedJavaType elementType, ValueNode length) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createTrustedWithoutAssumptions(elementType.getArrayClass())));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.kind = PTXKind.fromResolvedJavaType(elementType);
        this.arrayTemplate = PTXKind.resolveTemplateType(elementType);
    }

    public LocalArrayNode(PTXMemoryBase memoryRegister, JavaKind elementKind, ValueNode length) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.memoryRegister = memoryRegister;
        this.length = length;
        this.kind = PTXKind.fromResolvedJavaKind(elementKind);
        this.arrayTemplate = PTXKind.resolveTemplateType(elementKind);
    }

    public PTXMemoryBase getMemoryRegister() {
        return memoryRegister;
    }

    public ValueNode getLength() {
        return length;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitLocalArray length=%s kind=%s", length, kind);
        final Value lengthValue = gen.operand(length);

        LIRKind lirKind = LIRKind.value(kind);
        final Variable variable = ((PTXLIRGenerator) gen.getLIRGeneratorTool()).newVariable(lirKind, true);
        final PTXBinary.Expr declaration = new PTXBinary.Expr(arrayTemplate, lirKind, variable, lengthValue);

        final PTXLIRStmt.ExprStmt expr = new PTXLIRStmt.ExprStmt(declaration);
        gen.getLIRGeneratorTool().append(expr);
        gen.setResult(this, variable);
    }
}
