/*
 * Copyright (c) 2013-2021, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.snippets;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;

import jdk.vm.ci.meta.JavaKind;

/// FIXME: <Refactor> across 3 backends
public interface TornadoSnippetTypeInference {

    SnippetInfo inferIntSnippet(ValueNode value, ValueNode extra);

    SnippetInfo inferLongSnippet(ValueNode value, ValueNode extra);

    SnippetInfo inferFloatSnippet(ValueNode value, ValueNode extra);

    SnippetInfo inferDoubleSnippet(ValueNode value, ValueNode extra);

    SnippetInfo getSnippetInstance(JavaKind elementKind, ValueNode value, ValueNode extra);

}
