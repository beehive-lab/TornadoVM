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
package uk.ac.manchester.tornado.drivers.metal.graal.compiler.plugins;

import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.metal.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.MetalConvertHalfToFloat;
import uk.ac.manchester.tornado.runtime.graal.nodes.AddHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.DivHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.MultHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.SubHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewHalfFloatInstance;

public class MetalHalfFloatPlugins {

    public static void registerPlugins(final GraphBuilderConfiguration.Plugins ps, final InvocationPlugins plugins) {
        registerHalfFloatInit(ps, plugins);
    }

    private static void registerHalfFloatInit(GraphBuilderConfiguration.Plugins ps, InvocationPlugins plugins) {

        final InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, HalfFloat.class);

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("<init>") && method.toString().contains("HalfFloat.<init>")) {
                    NewHalfFloatInstance newHalfFloatInstance = new NewHalfFloatInstance(args[1]);
                    b.add(newHalfFloatInstance);
                    args[0].replaceAtUsages(newHalfFloatInstance);
                    return true;
                }
                return false;
            }
        });

        r.register(new InvocationPlugin("add", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                AddHalfFloatNode addNode = new AddHalfFloatNode(halfFloat1, halfFloat2);
                b.getGraph().addOrUnique(addNode);
                b.push(JavaKind.Object, addNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("sub", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                SubHalfFloatNode subNode = new SubHalfFloatNode(halfFloat1, halfFloat2);
                b.addPush(JavaKind.Object, subNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("mult", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                MultHalfFloatNode multNode = new MultHalfFloatNode(halfFloat1, halfFloat2);
                b.getGraph().addOrUnique(multNode);
                b.push(JavaKind.Object, multNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("div", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfFloat1, ValueNode halfFloat2) {
                DivHalfFloatNode divNode = new DivHalfFloatNode(halfFloat1, halfFloat2);
                b.getGraph().addOrUnique(divNode);
                b.push(JavaKind.Object, divNode);
                return true;
            }
        });

        r.register(new InvocationPlugin("getHalfFloatValue", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                // get(false): skip the null-check path (receiver.get(true) -> GraphBuilderContext
                // .nullCheckedValue() -> PiNode.create()/canonical() -> AbstractObjectStamp.improveWith()/
                // join()). That path assumes the receiver carries a real AbstractObjectStamp, but receivers
                // produced by e.g. ByteArray.getHalfFloat() carry the synthetic HalfFloatStamp (pushed as
                // JavaKind.Object since the declared type is HalfFloat), so the join's internal cast throws
                // a ClassCastException. These synthetic half-value nodes can never be null, so the null
                // check buys nothing and get(false) (plain unwrap, no PiNode) is safe here.
                //
                // Only intercept when the receiver actually carries that synthetic stamp. Receivers with
                // a real stamp (e.g. Half2.getX()/getY(), which return an already-properly-typed HalfFloat
                // field) don't have this problem, and forcing them through this synthetic path instead of
                // normal inlining changes the surrounding graph shape enough to trip an unrelated
                // FixedGuardNode/ValueAnchorNode canonicalization bug elsewhere in the sketcher. Falling
                // through (return false) lets the default bytecode-inlining path handle those untouched.
                // Mirrors OCLHalfFloatPlugins.
                ValueNode receiverValue = receiver.get(false);
                if (!(receiverValue.stamp(NodeView.DEFAULT) instanceof HalfFloatStamp)) {
                    return false;
                }
                HalfFloatPlaceholder placeholder = new HalfFloatPlaceholder(receiverValue);
                b.getGraph().addOrUnique(placeholder);
                b.push(JavaKind.Short, placeholder);
                return true;
            }
        });

        // Without this, the sketcher tries to inline HalfFloat.getFloat32()'s real bytecode against
        // the receiver, hitting the same object-vs-HalfFloatStamp mismatch described above. Intercepting
        // the call directly - mirroring getHalfFloatValue above - avoids inlining into that mismatch
        // altogether. Same HalfFloatStamp scoping as getHalfFloatValue above, for the same reason.
        // TornadoHalfFloatReplacement's existing leftover-placeholder cleanup already rewires any
        // remaining HalfFloatPlaceholder input to the real half value, so MetalConvertHalfToFloat needs
        // no extra handling there (unlike OpenCL, which needed that cleanup extended).
        r.register(new InvocationPlugin("getFloat32", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode receiverValue = receiver.get(false);
                if (!(receiverValue.stamp(NodeView.DEFAULT) instanceof HalfFloatStamp)) {
                    return false;
                }
                HalfFloatPlaceholder placeholder = new HalfFloatPlaceholder(receiverValue);
                b.getGraph().addOrUnique(placeholder);
                MetalConvertHalfToFloat convertHalfToFloat = new MetalConvertHalfToFloat(placeholder);
                b.getGraph().addOrUnique(convertHalfToFloat);
                b.push(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });

    }

}
