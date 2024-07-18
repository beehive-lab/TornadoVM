/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.runtime.graal.nodes.AddHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.DivHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;
import uk.ac.manchester.tornado.runtime.graal.nodes.MultHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewHalfFloatInstance;
import uk.ac.manchester.tornado.runtime.graal.nodes.SubHalfFloatNode;

public class OCLHalfFloatPlugins {

    public static void registerPlugins(final GraphBuilderConfiguration.Plugins ps, final InvocationPlugins plugins) {
        registerHalfFloatInit(ps, plugins);
    }

    private static void registerHalfFloatInit(GraphBuilderConfiguration.Plugins ps, InvocationPlugins plugins) {

        final InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, HalfFloat.class);

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("<init>") && (method.toString().contains("HalfFloat.<init>"))) {
                    NewHalfFloatInstance newHalfFloatInstance = b.append(new NewHalfFloatInstance(args[1]));
                    b.add(newHalfFloatInstance);
                    return true;
                }
                return false;
            }
        });

        r.register(new InvocationPlugin("add", InvocationPlugin.Receiver.class, HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                AddHalfFloatNode addNode = b.append(new AddHalfFloatNode(halfFloat1, halfFloat2));
                b.push(JavaKind.Object, addNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", InvocationPlugin.Receiver.class, HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                SubHalfFloatNode subNode = b.append(new SubHalfFloatNode(halfFloat1, halfFloat2));
                b.push(JavaKind.Object, subNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("mult", InvocationPlugin.Receiver.class, HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                MultHalfFloatNode multNode = b.append(new MultHalfFloatNode(halfFloat1, halfFloat2));
                b.push(JavaKind.Object, multNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("div", InvocationPlugin.Receiver.class, HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                DivHalfFloatNode divNode = b.append(new DivHalfFloatNode(halfFloat1, halfFloat2));
                b.push(JavaKind.Object, divNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("getHalfFloatValue", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.push(JavaKind.Short, b.append(new HalfFloatPlaceholder(receiver.get(true))));
                return true;
            }
        });

    }

}
