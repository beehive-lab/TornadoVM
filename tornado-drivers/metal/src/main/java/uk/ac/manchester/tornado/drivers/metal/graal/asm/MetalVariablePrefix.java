/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2023, 2024, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.metal.graal.asm;

/**
 * Enum representing the type-prefix mappings used for codegen of Metal
 * variables.
 */
public enum MetalVariablePrefix {
    // @formatter:off
    ULONG("ulong", "ul_"),
    UINT("uint", "ui_"),
    USHORT("ushort", "us_"),
    UCHAR("uchar", "uc_"),
    INT("int", "i_"),
    LONG("long", "l_"),
    BOOL("bool", "b_"),
    DOUBLE("double", "d_"),
    BYTE("byte", "bt_"),
    FLOAT("float", "f_"),
    CHAR("char", "ch_"),
    ATOMIC_ADD_FLOAT("atomic_add_float", "adf_"),
    ATOMIC_ADD_LONG("atomic_add_long", "adl_"),
    ATOMIC_ADD_DOUBLE("atomic_add_double", "addo_"),
    ATOMIC_ADD_INT("atomic_add_int", "adi_"),
    FLOAT2("float2", "v2f_"),
    FLOAT3("float3", "v3f_"),
    FLOAT4("float4", "v4f_"),
    FLOAT8("float8", "v8f_"),
    FLOAT16("float16", "v16f_"),
    INT2("int2", "v2int_"),
    INT3("int3", "v3int_"),
    INT4("int4", "v4int_"),
    INT8("int8", "v8int_"),
    INT16("int16", "v16int_"),
    DOUBLE2("double2", "v2d_"),
    DOUBLE3("double3", "v3d_"),
    DOUBLE4("double4", "v4d_"),
    DOUBLE8("double8", "v8d_"),
    DOUBLE16("double16", "v16d_"),
    CHAR2("char2", "ch2_"),
    CHAR3("char3", "ch3_"),
    CHAR4("char4", "ch4_"),
    CHAR16("char16", "ch16_"),
    CHAR8("char8", "ch8_"),
    BYTE2("byte2", "b2_"),
    BYTE3("byte3", "b3_"),
    BYTE4("byte4", "b4_"),
    BYTE8("byte8", "b8_"),
    BYTE16("byte16", "b16_"),
    SHORT("short", "sh_"),
    SHORT2("short2", "sh2_"),
    SHORT3("short3", "sh3_"),
    HALF("half", "half_"),
    HALF2("half2", "v2hf_"),
    HALF3("half3", "v3hf_"),
    HALF4("half4", "v4hf_"),
    HALF8("half8", "v8hf_"),
    HALF16("half16", "v16hf_");

    // @formatter:on

    private final String type;
    private final String prefix;

    /**
     * It constructs an MetalVariablePrefix enum with the specified type and prefix.
     *
     * @param type
     *     The type string.
     * @param prefix
     *     The prefix string.
     */
    MetalVariablePrefix(String type, String prefix) {
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
