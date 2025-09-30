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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 */

package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.Opcode;

import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;

@Opcode("ThreadScheduling")
public class MetalThreadConfiguration extends MetalLIROp {
    private int x;
    private int y;
    private int z;

    public MetalThreadConfiguration(int oneD, int twoD, int threeD) {
        super(LIRKind.Illegal);
        this.x = oneD;
        this.y = twoD;
        this.z = threeD;
    }

    @Override
    public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
    }
}
