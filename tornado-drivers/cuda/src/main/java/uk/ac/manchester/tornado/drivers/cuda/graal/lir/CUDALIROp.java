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
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.core.common.LIRKind;

import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;

public abstract class CUDALIROp extends Value {

    public CUDALIROp(LIRKind lirKind) {
        super(lirKind);
    }

    public final void emit(CUDACompilationResultBuilder crb) {
        emit(crb, crb.getAssembler());
    }

    public abstract void emit(CUDACompilationResultBuilder crb, CUDAAssembler asm);

    public LIRKind getLIRKind() {
        return (LIRKind) this.getValueKind();
    }

    public CUDAKind getCUDAPlatformKind() {
        // Some scalar LIR ops carry no value kind (valueKind == null); treat those as
        // ILLEGAL (non-vector) so callers fall back to the scalar emission path rather
        // than NPE in getPlatformKind().
        if (getValueKind() == null) {
            return CUDAKind.ILLEGAL;
        }
        PlatformKind platformKind = getPlatformKind();
        return (platformKind instanceof CUDAKind) ? (CUDAKind) platformKind : CUDAKind.ILLEGAL;
    }

}
