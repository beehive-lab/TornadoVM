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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler.plugins;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.types.utils.FloatOps;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.AtomicAddNode;

public class AtomicPlugins {

    public static void registerPlugins(InvocationPlugins plugins) {
        registerAtomicPlugins(plugins);
    }

    private static void registerAtomicPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, FloatOps.class);

        r.register(new InvocationPlugin("atomicAdd", float[].class, int.class, float.class) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode array, ValueNode index, ValueNode value) {

                final AtomicAddNode atomicAddNode = new AtomicAddNode(array, index, JavaKind.Float, value);
                b.append(atomicAddNode);
                return true;
            }

        });
    }
}
