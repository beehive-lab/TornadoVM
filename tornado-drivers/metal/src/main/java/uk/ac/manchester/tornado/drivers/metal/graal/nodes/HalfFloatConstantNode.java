/*
 * Copyright (c) 2025, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.metal.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

@NodeInfo
public class HalfFloatConstantNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<HalfFloatConstantNode> TYPE = NodeClass.create(HalfFloatConstantNode.class);

    @Input
    private ConstantNode halfFloatValue;

    public HalfFloatConstantNode(ConstantNode halfFloatValue) {
        super(TYPE, new HalfFloatStamp());
        this.halfFloatValue = halfFloatValue;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, new ConstantValue(LIRKind.value(MetalKind.HALF), halfFloatValue.asConstant()));
    }
}
