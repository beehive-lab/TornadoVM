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
package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.lir.Opcode;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

@Opcode("VSEL")
public class MetalVectorElementSelect extends MetalLIROp {

    final Value vector;
    private final Value selection;

    public MetalVectorElementSelect(LIRKind lirKind, Value vector, Value selection) {
        super(lirKind);
        this.vector = vector;
        this.selection = selection;
    }

    private static final String[] XYZW = { ".x", ".y", ".z", ".w" };

    /**
     * Returns the Metal-compatible vector component accessor string for the given
     * element index and vector type.
     * <p>
     * Metal uses {@code .x/.y/.z/.w} for native vector types (length ≤ 4).
     * For TornadoVM's custom struct-based extended vectors:
     * <ul>
     *   <li>length 8  → {@code struct { T4 lo, hi; }} → {@code .lo.x} … {@code .hi.w}</li>
     *   <li>length 16 → {@code struct { T8 lo, hi; }} where T8 = {@code struct { T4 lo, hi; }}
     *       → {@code .lo.lo.x} … {@code .hi.hi.w}</li>
     * </ul>
     *
     * @param vector the vector value whose kind determines the vector length
     * @param idx    zero-based element index
     * @return the Metal component accessor string (e.g. {@code ".x"}, {@code ".lo.y"})
     */
    private static String metalVectorComponent(Value vector, int idx) {
        int vectorLen = 1;
        if (vector.getPlatformKind() instanceof MetalKind metalKind) {
            vectorLen = metalKind.getVectorLength();
        }
        if (vectorLen <= 4) {
            // Native Metal vector type: int2, float3, float4, etc.
            return XYZW[idx & 3];
        } else if (vectorLen == 8) {
            // Custom struct: { T4 lo, hi }
            // indices 0-3 → .lo.x/.lo.y/.lo.z/.lo.w
            // indices 4-7 → .hi.x/.hi.y/.hi.z/.hi.w
            String half = (idx < 4) ? ".lo" : ".hi";
            return half + XYZW[idx & 3];
        } else if (vectorLen == 16) {
            // Custom struct: { T8 lo, hi } where T8 = { T4 lo, hi }
            // indices  0- 3 → .lo.lo.x/.lo.lo.y/.lo.lo.z/.lo.lo.w
            // indices  4- 7 → .lo.hi.x/.lo.hi.y/.lo.hi.z/.lo.hi.w
            // indices  8-11 → .hi.lo.x/.hi.lo.y/.hi.lo.z/.hi.lo.w
            // indices 12-15 → .hi.hi.x/.hi.hi.y/.hi.hi.z/.hi.hi.w
            String outer = (idx < 8) ? ".lo" : ".hi";
            String inner = ((idx & 7) < 4) ? ".lo" : ".hi";
            return outer + inner + XYZW[idx & 3];
        } else {
            return "." + idx;
        }
    }

    @Override
    public void emit(MetalCompilationResultBuilder crb, MetalAssembler asm) {
        asm.emitValueOrOp(crb, vector);
        int idx = Integer.parseInt(MetalAssembler.getAbsoluteIndexFromValue(selection));
        asm.emitSymbol(metalVectorComponent(vector, idx));
    }

    @Override
    public String toString() {
        return String.format("vselect(%s, %s)", vector, selection);
    }

}
