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

        // Each callee must appear AFTER the prologue (#include, typedef, helpers) and
        // BEFORE all previously-added callees, so that dependencies (inner callees) end
        // up first in the source file.  We find the prologue end by locating the last
        // "}\n\n" that occurs before the "kernel void" marker.  That sequence is emitted
        // exactly once by the Metal backend – after the atomicMul_Tornado_Int helper and
        // before the main kernel function – and it is not present inside any callee body.
        String oldStr = new String(oldCode);
        int kernelPos = oldStr.indexOf("kernel void ");
        int insertAt = -1;
        if (kernelPos > 0) {
            String beforeKernel = oldStr.substring(0, kernelPos);
            int prolEndPos = beforeKernel.lastIndexOf("}\n\n");
            if (prolEndPos >= 0) {
                insertAt = prolEndPos + 3; // right after "}\n\n"
            } else {
                insertAt = kernelPos; // fallback: just before kernel void
            }
        }

        if (insertAt >= 0) {
            buffer.put(oldCode, 0, insertAt);
            buffer.put(code);
            buffer.put((byte) '\n');
            buffer.put(oldCode, insertAt, oldCode.length - insertAt);
        } else {
            // Fallback: prepend (original behaviour)
            buffer.put(code);
            buffer.put((byte) '\n');
            buffer.put(oldCode);
        }
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
