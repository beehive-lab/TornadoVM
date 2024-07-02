/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.runtime.graal.compiler;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;

import jdk.vm.ci.meta.JavaKind;

public class TornadoForeignCallsProvider implements ForeignCallsProvider {

    @Override
    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        unimplemented();
        return null;
    }

    @Override
    public ForeignCallLinkage lookupForeignCall(ForeignCallSignature signature) {
        unimplemented();
        return null;
    }

    @Override
    public ForeignCallDescriptor getDescriptor(ForeignCallSignature signature) {
        unimplemented();
        return null;
    }

    @Override
    public LIRKind getValueKind(JavaKind jk) {
        unimplemented();
        return LIRKind.Illegal;
    }

}
