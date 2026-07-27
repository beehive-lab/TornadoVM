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
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Native FP8 (E4M3 / E5M2 storage byte) to {@code float} conversion.
 *
 * <p>Replaces the software arithmetic decode of {@code FP8#e4m3ToFloat} /
 * {@code FP8#e5m2ToFloat} inside CUDA kernels with the {@code cuda_fp8.h}
 * intrinsic path ({@code __nv_cvt_fp8_to_halfraw} + {@code __half2float}),
 * which compiles to the hardware {@code cvt} instructions on sm_89+ and to
 * the header's own emulation below that. The software decoders remain the
 * path for every other backend (OpenCL/PTX/SPIR-V/Metal), which simply
 * inline the Java bytecode. Mirrors {@link CUDAConvertHalfToFloat}.
 */
@NodeInfo
public class CUDAConvertFP8ToFloat extends ValueNode implements LIRLowerable {

    public static final NodeClass<CUDAConvertFP8ToFloat> TYPE = NodeClass.create(CUDAConvertFP8ToFloat.class);

    /** The two OCP FP8 interpretations of the storage byte. */
    public enum FP8Format {
        E4M3, E5M2
    }

    @Input
    private ValueNode fp8ByteNode;
    private final FP8Format format;

    public CUDAConvertFP8ToFloat(ValueNode fp8ByteNode, FP8Format format) {
        super(TYPE, StampFactory.forKind(JavaKind.Float));
        this.fp8ByteNode = fp8ByteNode;
        this.format = format;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable floatValue = tool.newVariable(LIRKind.value(CUDAKind.FLOAT));
        Value fp8Value = generator.operand(fp8ByteNode);
        tool.append(new CUDALIRStmt.ConvertFP8ToFloatStmt(floatValue, fp8Value, format == FP8Format.E4M3));
        generator.setResult(this, floatValue);
    }
}
