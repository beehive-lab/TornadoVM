/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.graal.phases;

import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.runtime.common.BatchCompilationConfig;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class TornadoHighTierContext extends HighTierContext {

    protected final ResolvedJavaMethod method;
    protected final Object[] args;
    protected final TaskMetaData meta;
    protected final boolean isKernel;
    private BatchCompilationConfig batchCompilationConfig;

    public TornadoHighTierContext(Providers providers, PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ResolvedJavaMethod method, Object[] args,
            TaskMetaData meta, boolean isKernel, BatchCompilationConfig batchCompilationConfig) {
        super(providers, graphBuilderSuite, optimisticOpts);
        this.method = method;
        this.args = args;
        this.meta = meta;
        this.isKernel = isKernel;
        this.batchCompilationConfig = batchCompilationConfig;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean hasArgs() {
        return args != null;
    }

    public Object getArg(int index) {
        return args[index];
    }

    public int getNumArgs() {
        return (hasArgs()) ? args.length : 0;
    }

    public TaskMetaData getMeta() {
        return meta;
    }

    public TornadoXPUDevice getDeviceMapping() {
        return meta.getLogicDevice();
    }

    public boolean hasMeta() {
        return meta != null;
    }

    public boolean isKernel() {
        return isKernel;
    }

    public BatchCompilationConfig getBatchCompilationConfig() {
        return batchCompilationConfig;
    }

    public boolean isGridSchedulerEnabled() {
        if (meta != null) {
            return meta.isGridSchedulerEnabled();
        }
        return false;
    }
}
