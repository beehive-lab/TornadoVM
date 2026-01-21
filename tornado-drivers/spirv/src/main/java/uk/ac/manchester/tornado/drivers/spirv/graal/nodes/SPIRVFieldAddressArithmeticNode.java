/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2025, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * This node is the equivalent of the {@code TornadoAddressArithmeticNode}, but for decompressed fields.
 * The {@code TornadoAddressArithmeticNode} was adding the offset to the object's base address.
 * However, this is wrong when the access is associated with a {@SPIRVDecompressedReadFieldNode}, because this
 * node emits the absolute address, therefore, adding the offset was unnecessary.
 */
@NodeInfo
public class SPIRVFieldAddressArithmeticNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<SPIRVFieldAddressArithmeticNode> TYPE = NodeClass.create(SPIRVFieldAddressArithmeticNode.class);

    @Input
    protected SPIRVDecompressedReadFieldNode base;

    public SPIRVFieldAddressArithmeticNode(SPIRVDecompressedReadFieldNode base) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.base = base;
    }

    public void generate(NodeLIRBuilderTool generator) {
        Value decompressedAddress = generator.operand(base);
        generator.setResult(this, decompressedAddress);
    }

}
