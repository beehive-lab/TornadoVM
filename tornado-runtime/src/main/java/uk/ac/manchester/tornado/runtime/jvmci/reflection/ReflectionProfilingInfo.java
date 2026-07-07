/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.jvmci.reflection;

import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.TriState;

/**
 * An "unprofiled" {@link ProfilingInfo} - reports no runtime profile data. This
 * is the JDK-neutral counterpart to HotSpot's profiling info: TornadoVM does
 * not use profile-guided optimisation for kernel compilation (branch/type
 * profiles are absent), so the compiler simply proceeds without speculation.
 */
final class ReflectionProfilingInfo implements ProfilingInfo {

    private final int codeSize;

    ReflectionProfilingInfo(int codeSize) {
        this.codeSize = codeSize;
    }

    @Override
    public int getCodeSize() {
        return codeSize;
    }

    @Override
    public JavaTypeProfile getTypeProfile(int bci) {
        return null;
    }

    @Override
    public JavaMethodProfile getMethodProfile(int bci) {
        return null;
    }

    @Override
    public double getBranchTakenProbability(int bci) {
        return -1D;
    }

    @Override
    public double[] getSwitchProbabilities(int bci) {
        return null;
    }

    @Override
    public TriState getExceptionSeen(int bci) {
        return TriState.UNKNOWN;
    }

    @Override
    public TriState getNullSeen(int bci) {
        return TriState.UNKNOWN;
    }

    @Override
    public int getExecutionCount(int bci) {
        return -1;
    }

    @Override
    public int getDeoptimizationCount(DeoptimizationReason reason) {
        return 0;
    }

    @Override
    public boolean setCompilerIRSize(Class<?> irType, int size) {
        return false;
    }

    @Override
    public int getCompilerIRSize(Class<?> irType) {
        return -1;
    }

    @Override
    public boolean isMature() {
        return false;
    }

    @Override
    public void setMature() {
        // no-op
    }
}
