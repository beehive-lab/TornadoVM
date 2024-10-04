/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

import jdk.graal.compiler.code.CompilationResult;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

/**
 * Object that represents the result of a SPIRV compilation (from GraalIR to
 * SPIR-V binary) after all optimizations phases.
 * 
 * This object stores the set of methods that were compiled as well as the
 * compilation ID that TornadoVM sets for each task within the task-schedule.
 */
public class SPIRVCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private TaskDataContext taskMetaData;
    private String compilationId;
    private ByteBuffer spirvBinary;
    private SPIRVAssembler spirvAssembler;

    public SPIRVCompilationResult(String compilationId, String methodName, TaskDataContext taskMetaData) {
        super(methodName);
        this.compilationId = compilationId;
        this.taskMetaData = taskMetaData;
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return nonInlinedMethods;
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    private byte[] prependToTargetCode(byte[] targetCode, byte[] codeToPrepend) {
        final int size = targetCode.length + codeToPrepend.length + 1;

        final byte[] newCode = new byte[size];
        Arrays.fill(newCode, (byte) 0);

        final ByteBuffer buffer = ByteBuffer.wrap(newCode);
        buffer.put(codeToPrepend);
        buffer.put((byte) '\n');
        buffer.put(targetCode);
        return newCode;
    }

    public void addCompiledMethodCode(byte[] code) {
        byte[] newCode = prependToTargetCode(getTargetCode(), code);
        setTargetCode(newCode, newCode.length);
    }

    public TaskDataContext getTaskMetaData() {
        return this.taskMetaData;
    }

    public TaskDataContext getMeta() {
        return taskMetaData;
    }

    public String getId() {
        return compilationId;
    }

    public byte[] getSPIRVBinary() {
        return spirvBinary.array();
    }

    public void setSPIRVBinary(ByteBuffer spirvByteBuffer) {
        this.spirvBinary = spirvByteBuffer;
    }

    public SPIRVAssembler getAssembler() {
        return spirvAssembler;
    }

    public void setAssembler(SPIRVAssembler assembler) {
        spirvAssembler = assembler;
    }
}
