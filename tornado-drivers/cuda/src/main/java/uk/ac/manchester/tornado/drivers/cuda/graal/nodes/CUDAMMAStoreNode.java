/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.common.Access;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.MarkArrayParameterAccess;

@NodeInfo
public class CUDAMMAStoreNode extends FixedWithNextNode implements LIRLowerable, MarkArrayParameterAccess {

    public static final NodeClass<CUDAMMAStoreNode> TYPE = NodeClass.create(CUDAMMAStoreNode.class);

    @Input private ValueNode fragD, target, tileRow, tileCol, dimN;
    private final int headerElements;
    private final boolean isInt8;

    public CUDAMMAStoreNode(ValueNode fragD, ValueNode target, ValueNode tileRow,
                            ValueNode tileCol, ValueNode dimN, int headerElements) {
        this(fragD, target, tileRow, tileCol, dimN, headerElements, false);
    }
    public CUDAMMAStoreNode(ValueNode fragD, ValueNode target, ValueNode tileRow,
                            ValueNode tileCol, ValueNode dimN, int headerElements, boolean i8) {
        super(TYPE, StampFactory.forVoid());
        this.fragD = fragD; this.target = target; this.tileRow = tileRow;
        this.tileCol = tileCol; this.dimN = dimN;
        this.headerElements = headerElements; this.isInt8 = i8;
    }

    @Override
    public Access getArrayParameterAccess(ValueNode parameter) {
        // Unwrap Pi on both sides: on the reflection path the stored `target` input is a PiNode wrapping the
        // parameter, so comparing the raw parameter against a Pi-wrapped target would spuriously report NONE and
        // the output array would never be copied device-to-host (result reads 0).
        return MarkArrayParameterAccess.unwrapPi(parameter) == MarkArrayParameterAccess.unwrapPi(target)
                ? Access.READ_WRITE
                : Access.NONE;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        tool.append(new CUDALIRStmt.MMAStoreStmt(
                gen.operand(fragD), gen.operand(target), gen.operand(tileRow),
                gen.operand(tileCol), gen.operand(dimN), headerElements, isInt8));
    }
}
