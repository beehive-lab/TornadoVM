/*
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
 *
 */

package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.getCodeWithAttachedPTXHeader;
import static uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil.prependToTargetCode;

import java.util.HashSet;
import java.util.Set;

import jdk.graal.compiler.code.CompilationResult;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTXCompilationResult extends CompilationResult {

    private Set<ResolvedJavaMethod> nonInlinedMethods;
    private TaskDataContext taskMetaData;

    public PTXCompilationResult(String functionName, TaskDataContext meta) {
        super(functionName);
        this.taskMetaData = meta;
    }

    public void setNonInlinedMethods(Set<ResolvedJavaMethod> value) {
        nonInlinedMethods = value;
    }

    public Set<ResolvedJavaMethod> getNonInlinedMethods() {
        return (nonInlinedMethods != null) ? nonInlinedMethods : new HashSet<>();
    }

    public void addCompiledMethodCode(byte[] code) {
        byte[] newCode = prependToTargetCode(getTargetCode(), code);
        setTargetCode(newCode, newCode.length);
    }

    public void addPTXHeader(PTXBackend backend) {
        byte[] newCode = getCodeWithAttachedPTXHeader(getTargetCode(), backend);
        setTargetCode(newCode, newCode.length);
    }

    public TaskDataContext metaData() {
        return taskMetaData;
    }
}
