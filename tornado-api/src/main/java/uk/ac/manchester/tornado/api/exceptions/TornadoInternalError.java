/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.api.exceptions;

import java.util.ArrayList;

public class TornadoInternalError extends Error {

    private static final long serialVersionUID = 6639694094043791236L;

    private final ArrayList<String> context = new ArrayList<>();

    public static RuntimeException unimplemented() {
        throw new TornadoInternalError("unimplemented");
    }

    public static RuntimeException unimplemented(String msg) {
        throw new TornadoInternalError("unimplemented: %s", msg);
    }

    public static RuntimeException unimplemented(String msg, Object... args) {
        throw new TornadoInternalError("unimplemented: " + msg, args);
    }

    public static RuntimeException shouldNotReachHere() {
        throw new TornadoInternalError("should not reach here");
    }

    public static RuntimeException shouldNotReachHere(String msg) {
        throw new TornadoInternalError("should not reach here: %s", msg);
    }

    public static RuntimeException shouldNotReachHere(String msg, Object... args) {
        throw new TornadoInternalError("should not reach here: " + msg, args);
    }

    public static RuntimeException shouldNotReachHere(Throwable cause) {
        throw new TornadoInternalError(cause);
    }

    public static void guarantee(boolean condition, String msg, Object... args) {
        if (!condition) {
            throw new TornadoInternalError("failed guarantee: " + msg, args);
        }
    }

    public TornadoInternalError(String msg, Object... args) {
        super(String.format(msg, args));
    }

    public TornadoInternalError(Throwable cause) {
        super(cause);
    }

    public TornadoInternalError addContext(String newContext) {
        context.add(newContext);
        return this;
    }

    public TornadoInternalError addContext(String name, Object obj) {
        return addContext(String.format("%s: %s", name, obj));
    }

}
