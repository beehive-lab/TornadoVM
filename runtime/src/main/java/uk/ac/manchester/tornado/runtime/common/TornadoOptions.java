/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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

    /**
     * Option to print TornadoVM Internal Bytecodes.
     * 
     */
    public static boolean printBytecodes = getBooleanValue("tornado.print.bytecodes", "False");

    /**
     * Option to debug dynamic reconfiguration policies.
     * 
     * Use `-Dtornado.dynamic.verbose=True`.
     * 
     */
    public static final boolean DEBUG_POLICY = getBooleanValue("tornado.dynamic.verbose", "False");

    /**
     * Option to enable experimental and new option for performing automatic full
     * reductions.
     */
    public static final boolean EXPERIMENTAL_REDUCE = getBooleanValue("tornado.experimental.reduce", "True");

    /**
     * Stream-In All variables when using automatic reductions. This option is
     * considered experimental and it might be removed in future versions.
     */
    public static final boolean EXPERIMENTAL_REDUCE_STREAM_ALL_IN = getBooleanValue("tornado.experimental.reduce.stream.all.in", "False");

    /**
     * Option to load FPGA pre-compiled binaries.
     */
    public static StringBuffer FPGA_BINARIES = System.getProperty("tornado.precompiled.binary", null) != null ? new StringBuffer(System.getProperty("tornado.precompiled.binary", null)) : null;

    /**
     * Temporal option for disabling null checks for Apache-Flink.
     */
    public static final boolean IGNORE_NULL_CHECKS = getBooleanValue("tornado.ignore.nullchecks", "False");

    /**
     * Option for enabling saving the profiler into a file.
     */
    public static final boolean PROFILER_LOGS_ACCUMULATE = getBooleanValue("tornado.log.profiler", "False");

    /**
     * Option to enable profiler-feature extractions.
     */
    public final static boolean FEATURE_EXTRACTION = getBooleanValue("tornado.feature.extraction", "False");

    /**
     * Enable/Disable FMA Optimizations. True by default.
     */
    public static final boolean ENABLE_FMA = getBooleanValue("tornado.enable.fma", "True");

    /**
     * Option to enable profiler. It can be disabled at any point during runtime.
     * 
     * @return boolean.
     */
    public static boolean isProfilerEnabled() {
        return getBooleanValue("tornado.profiler", "False");
    }

    /**
     * Option for saving the profiler between different runs. It can be disabled at
     * any point during runtime.
     * 
     * @return boolean.
     */
    public static boolean isSaveProfilerEnabled() {
        return getBooleanValue("tornado.profiler.save", "False");
    }

    public static boolean PARTIAL_UNROLL() {
        return getBooleanValue("tornado.experimental.partial.unroll", "False");
    }

    /**
     * User scheduling. It does not specialize the code
     */
    public static final boolean USER_SCHEDULING = getBooleanValue("tornado.user.scheduling", "False");

    private static boolean getBooleanValue(String property, String defaultValue) {
        return Boolean.parseBoolean(Tornado.getProperty(property, defaultValue));
    }

}
