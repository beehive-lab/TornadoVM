/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
