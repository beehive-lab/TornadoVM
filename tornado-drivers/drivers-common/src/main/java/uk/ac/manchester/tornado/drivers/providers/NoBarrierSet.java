/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.providers;

import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import tornado.graal.compiler.core.common.memory.BarrierType;
import tornado.graal.compiler.core.common.type.Stamp;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.extended.RawStoreNode;
import tornado.graal.compiler.nodes.gc.BarrierSet;
import tornado.graal.compiler.nodes.memory.FixedAccessNode;

/**
 * No-op {@link BarrierSet} for the accelerator backends. TornadoVM generates GPU code with no managed
 * heap or garbage collector, so no GC read/write barriers are ever required. Every query returns
 * {@link BarrierType#NONE}. This replaces the previous {@code null} barrier set, which caused a
 * {@code NullPointerException} in {@code fieldWriteBarrierType} on the JVMCI-absent (JDK 27+) path where
 * field stores route through the platform configuration provider's barrier set rather than a HotSpot one.
 */
public class NoBarrierSet implements BarrierSet {

    @Override
    public boolean hasWriteBarrier() {
        return false;
    }

    @Override
    public boolean hasReadBarrier() {
        return false;
    }

    @Override
    public void addBarriers(FixedAccessNode access) {
        // no barriers on the accelerator
    }

    @Override
    public BarrierType fieldReadBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType readBarrierType(LocationIdentity location, ValueNode address, Stamp loadStamp) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType writeBarrierType(RawStoreNode store) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType arrayWriteBarrierType(JavaKind storageKind) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType guessReadWriteBarrier(ValueNode object, ValueNode value) {
        return BarrierType.NONE;
    }

    @Override
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return false;
    }
}
