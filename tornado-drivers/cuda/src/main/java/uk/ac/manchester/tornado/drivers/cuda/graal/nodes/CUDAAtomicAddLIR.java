/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.Stamp;
import tornado.graal.compiler.graph.Node.Input;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.address.AddressNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "atomic_add")
public class CUDAAtomicAddLIR extends FixedNode implements LIRLowerable {

    public static final NodeClass<CUDAAtomicAddLIR> TYPE = NodeClass.create(CUDAAtomicAddLIR.class);

    public static final String ATOMIC_ADD = "atomic_add";

    @Input protected AddressNode address;

    @Input protected ValueNode value;

    public CUDAAtomicAddLIR(AddressNode address, Stamp stamp, ValueNode value) {
        super(TYPE, stamp);
        this.address = address;
        this.value = value;
    }

    public AddressNode getAddress() {
        return address;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind lirKind = LIRKind.value(gen.getLIRGeneratorTool().target().arch.getWordKind());
        final Variable variable = gen.getLIRGeneratorTool().newVariable(lirKind);
        gen.setResult(this, variable);
    }
}
