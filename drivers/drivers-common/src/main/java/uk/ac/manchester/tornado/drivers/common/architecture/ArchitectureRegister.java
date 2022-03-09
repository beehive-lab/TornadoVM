/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.common.architecture;

import jdk.vm.ci.meta.PlatformKind;

/**
 * Base Class for holding a Register for each architecture in TornadoVM.
 */
public abstract class ArchitectureRegister {

    protected final int number;
    protected final String name;
    protected final PlatformKind lirKind;

    protected ArchitectureRegister(int number, String name, PlatformKind lirKind) {
        this.number = number;
        this.name = name;
        this.lirKind = lirKind;
    }

    public String getDeclaration() {
        return String.format("%s %s", lirKind.toString(), name);
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }

    public PlatformKind getLirKind() {
        return lirKind;
    }
}
