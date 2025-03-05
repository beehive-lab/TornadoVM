/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2021, 2025, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.asm;

public class OCLAssemblerConstants {

    public static final String KERNEL_MODIFIER = "__kernel";
    public static final String EOL = "\n";
    public static final String GLOBAL_MEM_MODIFIER = "__global";
    public static final String SHARED_MEM_MODIFIER = "__shared";
    public static final String LOCAL_MEM_MODIFIER = "__local";
    public static final String PRIVATE_MEM_MODIFIER = "__private";
    public static final String CONSTANT_MEM_MODIFIER = "__constant";

    public static final String GLOBAL_REGION_NAME = "_global_region";
    public static final String LOCAL_REGION_NAME = "_local_region";
    public static final String PRIVATE_REGION_NAME = "_private_region";
    public static final String ATOMICS_REGION_NAME = "_atomics";
    public static final String CONSTANT_REGION_NAME = "_constant_region";
    public static final String KERNEL_CONTEXT = "_kernel_context";
    public static final String FRAME_REF_NAME = "_frame";
    public static final String VOLATILE = "volatile";

    public static final String STMT_DELIMITER = ";";
    public static final String EXPR_DELIMITER = ",";
    public static final String COLON = ":";
    public static final String FOR_LOOP = "for";
    public static final String IF_STMT = "if";
    public static final String SWITCH = "switch";
    public static final String CASE = "case";
    public static final String DEFAULT_CASE = "default";
    public static final String BREAK = "break";
    public static final String TAB = "  ";
    public static final String ASSIGN = " = ";
    public static final String CURLY_BRACKET_OPEN = "{";
    public static final String CURLY_BRACKET_CLOSE = "}";
    public static final String ADDRESS_OF = "&";

    public static final String OPEN_PARENTHESIS = "(";
    public static final String CLOSE_PARENTHESIS = ")";

    public static final String SQUARE_BRACKETS_OPEN = "[";
    public static final String SQUARE_BRACKETS_CLOSE = "]";

    public static final String ADD = "+";
    public static final String SUB = "-";
    public static final String MULT = "*";
    public static final String DIV = "/";
    public static final String MOD = "%";

    public static final String COMPARE = "==";
    public static final String LT = "<";
    public static final String LE = "=<";
    public static final String GT = ">";
    public static final String GE = ">=";

    public static final String NOT = "!";

    public static final String OR = "|";
    public static final String AND = "&";
    public static final String XOR = "^";

    public static final String SHL = "<<";
    public static final String SHR = ">>";
    public static final String ABS = "abs";
    public static final String SQRT = "sqrt";
    public static final String POP_COUNT = "popcount";

    public static final String FMIN = "fmin";
    public static final String FMAX = "fmax";
    public static final String MIN = "min";
    public static final String MAX = "max";
    public static final String ELSE = "else";

    public static final String PRAGMA = "#pragma";
    public static final String UNROLL = "unroll";

    public static final int STACK_BASE_OFFSET = 3;
}
