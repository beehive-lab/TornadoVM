/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx.graal.nodes;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXLIRStmt;

@NodeInfo(shortName = "printfString")
public class PrintfStringNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<PrintfStringNode> TYPE = NodeClass.create(PrintfStringNode.class);

    @Input
    private ValueNode inputString;

    public PrintfStringNode(ValueNode inputString) {
        super(TYPE, StampFactory.forVoid());
        this.inputString = inputString;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitPrintfString: inputString=%s", inputString);
        PTXLIRGenerator genTool = (PTXLIRGenerator) gen.getLIRGeneratorTool();
        Value inpString = gen.operand(inputString);

        Variable destArr = genTool.newVariable(LIRKind.value(PTXKind.B8), true);
        genTool.append(new PTXLIRStmt.PrintfStringDeclarationStmt(destArr, inpString));
        gen.setResult(this, destArr);
    }
}
