/*
 * Copyright (c) 2022, 2025, 2026 APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.ValuePhiNode;
import tornado.graal.compiler.nodes.ValueProxyNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.opencl.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLConvertHalfToFloat;
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

    /**
     * True for getHalfFloatValue()/getFloat32() receivers that TornadoHalfFloatReplacement will
     * strip down to a raw half value, so the call MUST be intercepted here rather than inlined:
     * either the receiver already carries the synthetic {@link HalfFloatStamp} (e.g. the value
     * ByteArray.getHalfFloat() pushes), or it is an object-typed node the replacement phase
     * rewrites to a HalfFloatStamp value later - a loop-carried HalfFloat accumulator phi (looked
     * through its loop-exit proxy) or a HalfFloat arithmetic node. Falling through to bytecode
     * inlining for these materializes a LoadField(halfFloatValue) plus null-check PiNode over what
     * later becomes a HalfFloatStamp value, which no later pass repairs: the PiNode's retained
     * AbstractObjectStamp crashes CanonicalizerPhase joining the two stamp families, and the field
     * read reaches codegen as a bogus *((short *)(half + 8)) dereference of a half register.
     * Genuinely well-typed object receivers (e.g. Half2's getX()/getY() field loads) still return
     * false so normal inlining handles them - see the scoping note in the plugins below.
     */
    private static boolean isSyntheticHalfReceiver(ValueNode receiverValue) {
        if (receiverValue.stamp(NodeView.DEFAULT) instanceof HalfFloatStamp) {
            return true;
        }
        ValueNode unproxified = receiverValue instanceof ValueProxyNode proxy ? proxy.value() : receiverValue;
        return unproxified instanceof ValuePhiNode || unproxified instanceof AddHalfFloatNode || unproxified instanceof SubHalfFloatNode || unproxified instanceof MultHalfFloatNode
                || unproxified instanceof DivHalfFloatNode;
    }

    private static void registerHalfFloatInit(GraphBuilderConfiguration.Plugins ps, InvocationPlugins plugins) {

        final InvocationPlugins.Registration r = new InvocationPlugins.Registration(plugins, HalfFloat.class);

        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("<init>") && method.toString().contains("HalfFloat.<init>")) {
                    NewHalfFloatInstance newHalfFloatInstance = new NewHalfFloatInstance(args[1]);

                    // Use b.add() to properly insert this FixedWithNextNode into the control flow
                    b.add(newHalfFloatInstance);

                    // Replace usages of the NewInstanceNode (args[0]) with our node
                    args[0].replaceAtUsages(newHalfFloatInstance);

                    // Return false to let normal <init> processing continue
                    // This avoids frame state issues
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
                //
                // Besides HalfFloatStamp receivers, isSyntheticHalfReceiver also intercepts HalfFloat
                // accumulator phis and arithmetic nodes: those are object-stamped at parse time (the
                // replacement phase only half-ifies them later), but inlining against them is just as
                // broken as against a HalfFloatStamp receiver - see the helper's javadoc.
                ValueNode receiverValue = receiver.get(false);
                if (!isSyntheticHalfReceiver(receiverValue)) {
                    return false;
                }
                HalfFloatPlaceholder placeholder = new HalfFloatPlaceholder(receiverValue);
                b.getGraph().addOrUnique(placeholder);
                b.push(JavaKind.Short, placeholder);
                return true;
            }
        });

        // Without this, the sketcher tries to inline HalfFloat.getFloat32()'s real bytecode against
        // the receiver, hitting the same object-vs-HalfFloatStamp mismatch described above (either via
        // the generic inliner's stamp join, or - once intercepted here - via receiver.get(true)'s
        // null-check path, hence get(false) below). Intercepting the call directly - mirroring
        // getHalfFloatValue above - avoids inlining into that mismatch altogether. Same HalfFloatStamp
        // scoping as getHalfFloatValue above, for the same reason.
        r.register(new InvocationPlugin("getFloat32", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode receiverValue = receiver.get(false);
                if (!isSyntheticHalfReceiver(receiverValue)) {
                    return false;
                }
                HalfFloatPlaceholder placeholder = new HalfFloatPlaceholder(receiverValue);
                b.getGraph().addOrUnique(placeholder);
                OCLConvertHalfToFloat convertHalfToFloat = new OCLConvertHalfToFloat(placeholder);
                b.getGraph().addOrUnique(convertHalfToFloat);
                b.push(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });

    }

}