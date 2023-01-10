/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * GNU Classpath is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * GNU Classpath is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GNU Classpath; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 *
 */
package uk.ac.manchester.tornado.api.enums;

/**
 * Enumerate to specify the mode in which data will be copied to/from the host
 * to/from the device.
 */
public class DataTransferMode {

    /**
     * Flag to copy data between host <-> device only during the first execution. If
     * the task-graph is executed multiple times, all data set with this flag will
     * remain on the device as a Read-Only data.
     */
    public static final int FIRST_EXECUTION = 0;

    /**
     * Flag to copy data between host <-> device every time the execute method of a
     * task-graph ({@link uk.ac.manchester.tornado.api.TaskGraph}) is invoked.
     */
    public static final int EVERY_EXECUTION = 1;

    /**
     * Flag to indicate that copy out of buffers (device -> host) are handled by the
     * programmer rather than the TornadoVM runtime system. If this flag is used,
     * developers must manually transfer the data from the device to the host by
     * invoking
     * {@link uk.ac.manchester.tornado.api.TornadoExecutionResult#transferToHost(Object...)}
     * method.
     */
    public static final int USER_DEFINED = 2;
}
