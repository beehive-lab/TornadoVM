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

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Native {@code float} to bfloat16 (raw bits in a {@code short}) conversion: bf16 is the high
 * half of the f32 bit pattern, so the encode is a round-to-nearest-even of the low 16 bits
 * followed by a truncation - emitted as one header-free expression over the {@code __float_as_uint}
 * core builtin (no cuda_bf16.h). Replaces the software arithmetic encode of
 * {@code BFloat16#bf16FromFloat} inside CUDA kernels (which inlines two 160-iteration
 * normalisation loops per element); other backends keep inlining the Java encoder. Mirrors
 * {@link CUDAConvertBF16ToFloat}.
 *
 * <p>The hardware path rounds ties to even, whereas the software {@code BFloat16} encoder rounds
 * ties half away from zero, so the two can differ by one ULP on an exact tie.</p>
 */
@NodeInfo
public class CUDAConvertFloatToBF16 extends ValueNode implements LIRLowerable {

    public static final NodeClass<CUDAConvertFloatToBF16> TYPE = NodeClass.create(CUDAConvertFloatToBF16.class);

    @Input
    private ValueNode floatNode;

    public CUDAConvertFloatToBF16(ValueNode floatNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.floatNode = floatNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable bf16Bits = tool.newVariable(LIRKind.value(CUDAKind.SHORT));
        Value floatValue = generator.operand(floatNode);
        tool.append(new CUDALIRStmt.ConvertFloatToBF16Stmt(bf16Bits, floatValue));
        generator.setResult(this, bf16Bits);
    }
}
