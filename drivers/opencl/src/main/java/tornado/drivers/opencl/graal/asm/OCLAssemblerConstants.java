/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.asm;

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
    public static final String CONSTANT_REGION_NAME = "_constant_region";

    public static final String HEAP_REF_NAME = "_heap_base";
    public static final String FRAME_BASE_NAME = "_frame_base";
    public static final String FRAME_REF_NAME = "_frame";

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

    public static final String BRACKET_OPEN = "(";
    public static final String BRACKET_CLOSE = ")";

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

    public static final int STACK_BASE_OFFSET = 6;

}
