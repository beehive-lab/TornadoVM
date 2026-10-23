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

import java.util.Optional;

/**
 * A frontend from a code model rather than from class files, such as Babylon's (jdk.incubator.code),
 * found with {@link java.util.ServiceLoader}. {@link Frontend#translateWithCallees} asks each one
 * first; empty means "not mine": the bytecode frontend translates the method.
 *
 * <p>A model frontend produces the same Java-level IR as the bytecode frontend (parameters typed as
 * {@link Frontend#typeOf}, the kernel named {@link Frontend#kernelName}, callees {@link Frontend#calleeName}),
 * so everything after the frontend is shared. Callees it has no model for it may translate from
 * bytecode with {@link Frontend#translateCallee}.</p>
 */
public interface ModelFrontend {

    /** The kernel's module (the kernel first), or empty to leave the method to the bytecode frontend. */
    Optional<Frontend.ModuleResult> translate(Frontend.BytecodeSource source, String className, String methodName, String descriptor);
}
