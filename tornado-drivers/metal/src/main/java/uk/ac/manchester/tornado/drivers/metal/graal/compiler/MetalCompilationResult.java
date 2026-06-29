/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
import uk.ac.manchester.tornado.drivers.metal.graal.backend.MetalPreamble;
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
        // up first in the source file.  We find the prologue end by locating the stable
        // MetalPreamble.PREAMBLE_END_MARKER, which the backend emits exactly once after
        // the trimmed preamble and before the main kernel function (it is not present in
        // any callee body).  The marker is followed by a blank line ("...marker\n\n").
        String oldStr = new String(oldCode);
        int kernelPos = oldStr.indexOf("kernel void ");
        int insertAt = -1;
        int markerPos = oldStr.indexOf(MetalPreamble.PREAMBLE_END_MARKER);
        if (markerPos >= 0) {
            // Insert after the marker line and its trailing blank line (marker\n\n),
            // so the blank line separating the preamble from callee definitions is preserved.
            int afterMarker = markerPos + MetalPreamble.PREAMBLE_END_MARKER.length();
            int newline = oldStr.indexOf('\n', afterMarker);
            if (newline >= 0 && newline + 1 < oldStr.length() && oldStr.charAt(newline + 1) == '\n') {
                insertAt = newline + 2; // skip marker-line \n plus blank \n
            } else {
                insertAt = (newline >= 0) ? newline + 1 : afterMarker;
            }
        } else if (kernelPos > 0) {
            // Fallback (older/foreign sources without the marker): just before kernel void.
            insertAt = kernelPos;
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

    /**
     * Rebuilds the minimal preamble by scanning the full combined source — kernel
     * body plus any callee methods inserted by {@link #addCompiledMethodCode}.
     * Must be called after the callee worklist is exhausted so that shims
     * referenced only from callee bodies are not omitted.
     */
    public void finalizePreamble() {
        String combined = new String(getTargetCode());
        int markerPos = combined.indexOf(MetalPreamble.PREAMBLE_END_MARKER);
        if (markerPos < 0) {
            return;
        }
        int afterMarker = markerPos + MetalPreamble.PREAMBLE_END_MARKER.length();
        // Skip the marker-line \n and the trailing blank \n emitted by MetalPreamble.buildFor.
        if (afterMarker < combined.length() && combined.charAt(afterMarker) == '\n') afterMarker++;
        if (afterMarker < combined.length() && combined.charAt(afterMarker) == '\n') afterMarker++;
        String nonPreamble = combined.substring(afterMarker);
        String newSource = MetalPreamble.buildFor(nonPreamble) + nonPreamble;
        byte[] newCode = newSource.getBytes();
        setTargetCode(newCode, newCode.length);
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
