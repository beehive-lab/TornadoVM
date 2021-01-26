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
 */
package uk.ac.manchester.tornado.runtime.tasks;

import java.util.concurrent.CompletableFuture;

import uk.ac.manchester.tornado.api.AbstractTaskGraph;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

class TornadoExecutionFuture extends CompletableFuture<AbstractTaskGraph> {
    @Override 
    public boolean complete(AbstractTaskGraph result) {
        throw new UnsupportedOperationException("Explicit completion of " + getClass().getName() + " is forbidden");
    }

    @Override 
    public boolean completeExceptionally(Throwable error) {
        throw new UnsupportedOperationException("Explicit completion of " + getClass().getName() + " is forbidden");
    }

    boolean success(AbstractTaskGraph result) {
        return super.complete(result);
    }

    boolean failure(Throwable error) {
        return super.completeExceptionally(null == error ? new TornadoRuntimeException("Error executing task graph") : error);
    }

}
