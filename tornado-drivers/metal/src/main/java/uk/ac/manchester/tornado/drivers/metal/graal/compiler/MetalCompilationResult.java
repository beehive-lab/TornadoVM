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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

import org.graalvm.compiler.code.CompilationResult;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private TaskDataContext meta;
    private MetalBackend backend;
    private String id;

    public MetalCompilationResult(String id, String name, TaskDataContext meta, MetalBackend backend) {
        super(name);
        this.id = id;
        this.meta = meta;
        this.backend = backend;
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    public void addCompiledMethodCode(byte[] code) {
        final byte[] oldCode = getTargetCode();
        final int size = oldCode.length + code.length + 1;

        final byte[] newCode = new byte[size];
        Arrays.fill(newCode, (byte) 0);

        final ByteBuffer buffer = ByteBuffer.wrap(newCode);
        buffer.put(code);
        buffer.put((byte) '\n');
        buffer.put(oldCode);
        setTargetCode(newCode, size);
    }

    public TaskDataContext getMeta() {
        return meta;
    }

    // FIXME <REFACTOR> This method can be removed
    public MetalBackend getBackend() {
        return backend;
    }

    public String getId() {
        return id;
    }
}
