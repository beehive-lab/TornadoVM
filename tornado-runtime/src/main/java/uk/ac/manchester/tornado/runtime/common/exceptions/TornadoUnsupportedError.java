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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime.common.exceptions;

import java.util.ArrayList;

public class TornadoUnsupportedError extends Error {

    private static final long serialVersionUID = 6639694094043791236L;

    private final ArrayList<String> context = new ArrayList<>();

    public static RuntimeException unsupported(String reason) {
        throw new TornadoUnsupportedError("unsupported: %s", reason);
    }

    public static RuntimeException unsupported(String reason, Object... args) {
        throw new TornadoUnsupportedError("unsupported: " + reason, args);
    }

    public TornadoUnsupportedError(String msg, Object... args) {
        super(String.format(msg, args));
    }

    public TornadoUnsupportedError(Throwable cause) {
        super(cause);
    }

    public TornadoUnsupportedError addContext(String newContext) {
        context.add(newContext);
        return this;
    }

    public TornadoUnsupportedError addContext(String name, Object obj) {
        return addContext(String.format("%s: %s", name, obj));
    }

}
