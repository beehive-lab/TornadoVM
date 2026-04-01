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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.MoveFactory;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;

public class MetalMoveFactory extends MoveFactory {

    @Override
    public boolean canInlineConstant(Constant jc) {
        return true;
    }

    @Override
    public boolean allowConstantToStackMove(Constant cnstnt) {
        unimplemented();
        return false;
    }

    @Override
    public LIRInstruction createMove(AllocatableValue av, Value value) {
        AssignStmt assignStmt = new AssignStmt(av, value);
        return assignStmt;
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue av, AllocatableValue av1) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue result, Constant input) {
        unimplemented();
        return null;
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        unimplemented();
        return null;
    }

}
