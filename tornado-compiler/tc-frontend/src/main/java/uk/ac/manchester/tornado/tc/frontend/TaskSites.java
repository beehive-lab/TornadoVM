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
package uk.ac.manchester.tornado.tc.frontend;

import java.util.LinkedHashSet;
import java.util.Set;

import uk.ac.manchester.tornado.tc.frontend.classfile.Bytecode;
import uk.ac.manchester.tornado.tc.frontend.classfile.ClassFile;
import uk.ac.manchester.tornado.tc.frontend.classfile.ConstantPool.MemberRef;

/**
 * Finds the methods a class registers as TornadoVM tasks: every {@code invokedynamic} that creates a
 * {@code TornadoFunctions.Task*} functional object names its implementation method as the second
 * bootstrap argument (a method handle). This is exactly the set of kernels the Graal path compiles,
 * found without running the program.
 */
public final class TaskSites {

    private static final String TASK_INTERFACE = "Luk/ac/manchester/tornado/api/common/TornadoFunctions$Task";

    private TaskSites() {
    }

    public static Set<MemberRef> kernels(ClassFile classFile) {
        var kernels = new LinkedHashSet<MemberRef>();
        for (var method : classFile.methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            for (var insn : Bytecode.decode(method.code().get().bytes())) {
                if (insn.opcode() != Bytecode.INVOKEDYNAMIC) {
                    continue;
                }
                var site = classFile.constants().dynamicSite(insn.index());
                if (!site.descriptor().contains(")" + TASK_INTERFACE)) {
                    continue;
                }
                var bootstrap = classFile.bootstrapMethods().get(site.bootstrap());
                if (bootstrap.arguments().size() > 1) {
                    classFile.constants().methodHandle(bootstrap.arguments().get(1)).ifPresent(kernels::add);
                }
            }
        }
        return kernels;
    }
}
