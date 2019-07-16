/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: Juan Fumero
 *
 */
package uk.ac.manchester.tornado.runtime.common;

public class TornadoOptions {

    public static boolean printBytecodes = Boolean.parseBoolean(Tornado.getProperty("tornado.print.bytecodes", "False"));

    public static final boolean DEBUG_POLICY = Boolean.parseBoolean(System.getProperty("tornado.dynamic.verbose", "False"));

    public static final boolean EXPERIMENTAL_REDUCE = Boolean.parseBoolean(System.getProperty("tornado.experimental.reduce", "True"));

    public static StringBuffer FPGA_BINARIES = System.getProperty("tornado.precompiled.binary", null) != null ? new StringBuffer(System.getProperty("tornado.precompiled.binary", null)) : null;

    // Temporal option
    public static final boolean IGNORE_NULL_CHECKS = Boolean.parseBoolean(System.getProperty("tornado.ignore.nullchecks", "False"));

}
