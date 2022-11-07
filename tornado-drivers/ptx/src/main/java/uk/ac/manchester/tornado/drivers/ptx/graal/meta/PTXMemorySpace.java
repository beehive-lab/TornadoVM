/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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

package uk.ac.manchester.tornado.drivers.ptx.graal.meta;

import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;

public enum PTXMemorySpace {
    GLOBAL(0, PTXAssemblerConstants.GLOBAL_MEM_MODIFIER), //
    PARAM(1, PTXAssemblerConstants.PARAM_MEM_MODIFIER), //
    SHARED(2, PTXAssemblerConstants.SHARED_MEM_MODIFIER), //
    LOCAL(3, PTXAssemblerConstants.LOCAL_MEM_MODIFIER); //

    private final int index;
    private final String name;

    PTXMemorySpace(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public int index() {
        return index;
    }

    public String getName() {
        return name;
    }
}
