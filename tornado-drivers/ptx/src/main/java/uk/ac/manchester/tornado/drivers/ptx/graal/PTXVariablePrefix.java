/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, 2024, APT Group, Department of Computer Science, The University
 * of Manchester. All rights reserved. DO NOT ALTER OR REMOVE COPYRIGHT NOTICES
 * OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 only, as published by
 * the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License version 2 for more
 * details (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version 2
 * along with this work; if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.drivers.ptx.graal;

public enum PTXVariablePrefix {

    /**
     * Enum representing the type-prefix mappings used for codegen of OpenCL
     * variables.
     */
    // @formatter:off
        B8("b8", "rub"),
        B16("b16", "rbh"),
        B32("b32", "rui"),
        B64("b64", "rbd"),
        S8("s8", "rsb"),
        S16("s16", "rsh"),
        S32("s32", "rsi"),
        S64("s64", "rsd"),
        U32("u32", "rui"),
        U64("u64", "rud"),
        F16("f16", "rfh"),
        F32("f32", "rfi"),
        F64("f64", "rfd"),
        PRED("pred", "rpb"),
        MMA_FRAG_ACC_F32("mma_frag_acc_f32", "rmmaaccf32"),
        MMA_FRAG_A_F16("mma_frag_a_f16", "rmmaaf16"),
        MMA_FRAG_B_F16("mma_frag_b_f16", "rmmabf16"),
        MMA_FRAG_ACC_S32("mma_frag_acc_s32", "rmmaaccs32"),
        MMA_FRAG_A_S8("mma_frag_a_s8", "rmmaas8"),
        MMA_FRAG_B_S8("mma_frag_b_s8", "rmmabs8");
    // @formatter:on

    private final String type;
    private final String prefix;

    /**
     * It constructs a PTXVariablePrefix enum with the specified type and prefix.
     *
     * @param type
     *     The type string.
     * @param prefix
     *     The prefix string.
     */
    PTXVariablePrefix(String type, String prefix) {
        this.type = type;
        this.prefix = prefix;
    }

    public String getType() {
        return type;
    }

    public String getPrefix() {
        return prefix;
    }
}
