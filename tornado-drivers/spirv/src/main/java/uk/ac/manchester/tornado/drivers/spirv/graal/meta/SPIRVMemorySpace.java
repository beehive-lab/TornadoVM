/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.meta;

import jdk.graal.compiler.core.common.LIRKind;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssemblerConstants;

public class SPIRVMemorySpace extends Value {

    public static final SPIRVMemorySpace GLOBAL = new SPIRVMemorySpace(SPIRVAssemblerConstants.GLOBAL_MEM_MODIFIER);
    public static final SPIRVMemorySpace CONSTANT = new SPIRVMemorySpace(SPIRVAssemblerConstants.CONSTANT_MEM_MODIFIER);
    public static final SPIRVMemorySpace SHARED = new SPIRVMemorySpace(OCLAssemblerConstants.SHARED_MEM_MODIFIER);
    public static final SPIRVMemorySpace LOCAL = new SPIRVMemorySpace(OCLAssemblerConstants.LOCAL_MEM_MODIFIER);
    public static final SPIRVMemorySpace PRIVATE = new SPIRVMemorySpace(OCLAssemblerConstants.PRIVATE_REGION_NAME);

    private String name;

    public SPIRVMemorySpace(String name) {
        super(LIRKind.Illegal);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
