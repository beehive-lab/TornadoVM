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
 * Native bfloat16 (raw bits in a {@code short}) to {@code float} conversion: bf16 is the
 * high half of the f32 bit pattern, so this lowers to a single
 * {@code __int_as_float(bits << 16)} - a core CUDA builtin, no header dependency. Replaces
 * the software arithmetic decode of {@code BFloat16#bf16ToFloat} inside CUDA kernels;
 * other backends keep inlining the Java decoder. Mirrors {@link CUDAConvertFP8ToFloat}.
 */
@NodeInfo
public class CUDAConvertBF16ToFloat extends ValueNode implements LIRLowerable {

    public static final NodeClass<CUDAConvertBF16ToFloat> TYPE = NodeClass.create(CUDAConvertBF16ToFloat.class);

    @Input
    private ValueNode bf16BitsNode;

    public CUDAConvertBF16ToFloat(ValueNode bf16BitsNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.bf16BitsNode = bf16BitsNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable floatValue = tool.newVariable(LIRKind.value(CUDAKind.FLOAT));
        Value bits = generator.operand(bf16BitsNode);
        tool.append(new CUDALIRStmt.ConvertBF16ToFloatStmt(floatValue, bits));
        generator.setResult(this, floatValue);
    }
}
