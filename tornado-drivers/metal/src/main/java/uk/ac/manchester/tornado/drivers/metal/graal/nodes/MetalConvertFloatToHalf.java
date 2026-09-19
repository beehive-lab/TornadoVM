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
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;

/**
 * Rounds a float to half precision, giving the value {@code new HalfFloat(float)} wraps a place
 * to live: a half register rather than the float the constructor argument arrived in.
 *
 * <p>Metal Shading Language emitted (via MetalLIRStmt.ConvertFloatToHalfStmt):
 * <pre>half_value = (half) float_value;</pre>
 */
@NodeInfo
public class MetalConvertFloatToHalf extends ValueNode implements LIRLowerable {

    public static final NodeClass<MetalConvertFloatToHalf> TYPE = NodeClass.create(MetalConvertFloatToHalf.class);

    @Input
    private ValueNode floatValueNode;

    public MetalConvertFloatToHalf(ValueNode floatValueNode) {
        super(TYPE, StampFactory.forKind(JavaKind.Short));
        this.floatValueNode = floatValueNode;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool tool = generator.getLIRGeneratorTool();
        Variable halfValue = tool.newVariable(LIRKind.value(MetalKind.HALF));
        Value floatValue = generator.operand(floatValueNode);
        tool.append(new MetalLIRStmt.ConvertFloatToHalfStmt(floatValue, halfValue));
        generator.setResult(this, halfValue);
    }
}
