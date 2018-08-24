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
package uk.ac.manchester.tornado.runtime.sketcher;

import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SketchRequest implements Future<Sketch>, Runnable {

    public final TaskMetaData meta;
    public final ResolvedJavaMethod resolvedMethod;
    public final Providers providers;
    public final PhaseSuite<HighTierContext> graphBuilderSuite;
    public final TornadoSketchTier sketchTier;
    public Sketch result;

    public SketchRequest(TaskMetaData meta, ResolvedJavaMethod resolvedMethod, Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, TornadoSketchTier sketchTier) {
        this.resolvedMethod = resolvedMethod;
        this.providers = providers;
        this.graphBuilderSuite = graphBuilderSuite;
        this.sketchTier = sketchTier;
        this.meta = meta;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public void run() {
        TornadoSketcher.buildSketch(this);
    }

    @Override
    public Sketch get() throws InterruptedException, ExecutionException {
        while (!isDone()) {
            Thread.sleep(100);
        }
        return result;
    }

    @Override
    public Sketch get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        unimplemented();
        return null;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return result != null;
    }
}
