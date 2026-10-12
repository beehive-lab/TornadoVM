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
