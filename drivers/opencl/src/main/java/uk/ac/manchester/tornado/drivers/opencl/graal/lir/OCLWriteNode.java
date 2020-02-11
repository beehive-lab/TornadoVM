/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;

@NodeInfo(nameTemplate = "OCLWrite#{p#location/s}")
public class OCLWriteNode extends AbstractWriteNode implements LIRLowerableAccess {

    private JavaKind type;

    public static final NodeClass<OCLWriteNode> TYPE = NodeClass.create(OCLWriteNode.class);

    public OCLWriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, JavaKind type) {
        super(TYPE, address, location, value, barrierType);
        assert (type == JavaKind.Char || type == JavaKind.Short);
        this.type = type;
    }

    protected OCLWriteNode(NodeClass<? extends OCLWriteNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(c, address, location, value, barrierType);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        OCLStamp oclStamp = null;
        if (type == JavaKind.Char) {
            oclStamp = new OCLStamp(OCLKind.CHAR);
        } else if (type == JavaKind.Short) {
            oclStamp = new OCLStamp(OCLKind.SHORT);
        }

        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(oclStamp);
        AddressNode address = super.getAddress();
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public Stamp getAccessStamp() {
        return value().stamp(NodeView.DEFAULT);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }
}