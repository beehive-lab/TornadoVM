/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
import tornado.graal.compiler.core.common.type.Stamp;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;

/**
 * Reads one element of an {@code mma.sync} accumulator fragment: the {@code acc[i]} of a
 * kernel that post-processes the result of {@link CUDAMMAComputeNode} in registers instead
 * of writing the whole fragment out with {@code mmaStore}.
 *
 * <p>The fragment is a C array local to the thread ({@code float d[4]}), so the access is
 * emitted as a plain element read. It cannot go through the normal array path: that lowers
 * to a read of {@code base + offset}, and the fragment has no base to offset from - which is
 * what made {@code acc[0]} fail in address lowering with "address origin unimplemented".
 *
 * <p>Which value of the tile an element holds is fixed by the PTX m16n8 C/D layout. For lane
 * {@code L} and element {@code i}, the element is row {@code L / 4 + 8 * (i / 2)}, column
 * {@code (L % 4) * 2 + i % 2} of the tile - the same mapping {@code mmaStore} uses to scatter
 * the fragment to memory. Every lane of the warp holds four elements, so a kernel reading them
 * has to keep the warp uniform exactly as the {@code mma.sync} that produced them does.
 */
@NodeInfo
public class CUDAMMAFragmentElementNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<CUDAMMAFragmentElementNode> TYPE = NodeClass.create(CUDAMMAFragmentElementNode.class);

    @Input
    private ValueNode fragment;

    private final int index;

    public CUDAMMAFragmentElementNode(Stamp stamp, ValueNode fragment, int index) {
        super(TYPE, stamp);
        this.fragment = fragment;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value fragmentValue = gen.operand(fragment);
        if (!(fragmentValue.getPlatformKind() instanceof CUDAKind fragmentKind) || !fragmentKind.isMMAFragment()) {
            throw new TornadoBailoutRuntimeException(
                    "MMA fragment element read applied to a non-fragment value: " + fragmentValue);
        }
        Variable result = tool.newVariable(LIRKind.value(fragmentKind.getElementKind()));
        tool.append(new CUDALIRStmt.MMAFragmentElementStmt(result, fragmentValue, index));
        gen.setResult(this, result);
    }
}
