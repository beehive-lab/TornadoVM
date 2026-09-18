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
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import tornado.graal.compiler.graph.Node;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.ValuePhiNode;
import tornado.graal.compiler.nodes.ValueProxyNode;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import tornado.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import tornado.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import tornado.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import tornado.graal.compiler.nodes.java.NewInstanceNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.internal.annotations.HalfType;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.cuda.graal.HalfFloatStamp;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAConvertHalfToFloat;
import uk.ac.manchester.tornado.runtime.graal.nodes.AddHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.DivHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.HalfFloatPlaceholder;
import uk.ac.manchester.tornado.runtime.graal.nodes.MultHalfFloatNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.NewHalfFloatInstance;
import uk.ac.manchester.tornado.runtime.graal.nodes.SubHalfFloatNode;

public class CUDAHalfFloatPlugins {

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
     * read reaches codegen as a bogus *(short *)(half + 8) dereference of a half register.
     * Genuinely well-typed object receivers (e.g. Half2's getX()/getY() field loads) still return
     * false so normal inlining handles them - see the scoping note in the plugins below.
     */
    private static boolean isSyntheticHalfReceiver(ValueNode receiverValue) {
        if (receiverValue.stamp(NodeView.DEFAULT) instanceof HalfFloatStamp) {
            return true;
        }
        ValueNode unproxified = receiverValue instanceof ValueProxyNode proxy ? proxy.value() : receiverValue;
        // A freshly constructed `new HalfFloat(x)` - either the allocation as the parser still has it on
        // the stack, or the NewHalfFloatInstance the <init> node plugin substitutes for it.
        // TornadoHalfFloatReplacement dissolves that allocation into the half value it wraps, so a field
        // read inlined against it ends up addressing a half register rather than an object: the
        // "address origin unimplemented" refusal that made `new HalfFloat(f).getHalfFloatValue()`
        // uncompilable while the same expression stored straight into a HalfFloatArray worked.
        if (unproxified instanceof NewHalfFloatInstance || isHalfFloatAllocation(unproxified)) {
            return true;
        }
        return unproxified instanceof ValuePhiNode || unproxified instanceof AddHalfFloatNode || unproxified instanceof SubHalfFloatNode || unproxified instanceof MultHalfFloatNode
                || unproxified instanceof DivHalfFloatNode;
    }

    /**
     * True for the {@code NewInstanceNode} of a {@code new HalfFloat(float)}.
     *
     * <p>Scoped to the float constructor, because that is the one
     * {@code TornadoHalfFloatReplacement} turns into a real half value (a
     * {@code __float2half} of the argument). {@code new HalfFloat(short)} takes a raw bit
     * pattern, which the backend has no reinterpretation for, so intercepting it here would
     * hand the half-bits reader a plain short and silently produce a numerically converted
     * value instead of the bits. Those receivers are left to inline exactly as before.
     */
    private static boolean isHalfFloatAllocation(ValueNode node) {
        if (!(node instanceof NewInstanceNode newInstance) || newInstance.instanceClass() == null //
                || newInstance.instanceClass().getAnnotation(HalfType.class) == null) {
            return false;
        }
        NewHalfFloatInstance instance = succeedingHalfFloatInstance(newInstance);
        return instance != null && instance.getValue() != null && instance.getValue().getStackKind() == JavaKind.Float;
    }

    /**
     * The {@link NewHalfFloatInstance} the {@code HalfFloat.<init>} node plugin above appended for
     * {@code allocation}. It is not always the allocation's immediate successor - a computed
     * constructor argument emits its own fixed nodes in between - so walk the straight-line fixed
     * chain forward, bounded, the way TornadoHalfFloatReplacement does when it looks for the same
     * node. A different allocation on the way means this one's {@code <init>} has not been parsed
     * yet, which cannot be the case for a receiver, but stops the walk safely either way.
     */
    private static NewHalfFloatInstance succeedingHalfFloatInstance(NewInstanceNode allocation) {
        Node cursor = allocation.successors().isNotEmpty() ? allocation.successors().first() : null;
        for (int hops = 0; cursor != null && !(cursor instanceof NewHalfFloatInstance) && !(cursor instanceof NewInstanceNode) && hops < 16; hops++) {
            cursor = cursor.successors().isNotEmpty() ? cursor.successors().first() : null;
        }
        return cursor instanceof NewHalfFloatInstance instance ? instance : null;
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
                // normal inlining changes the surrounding graph shape enough to risk tripping the unguarded
                // FixedGuardNode casts in TornadoHalfFloatFixedGuardElimination/TornadoHalfFloatReplacement
                // if Graal's canonicalizer simplifies a nearby guard into a ValueAnchorNode (confirmed to
                // happen on the OpenCL backend for this exact receiver shape; mirrored here defensively
                // since the same unguarded casts exist in this backend's half-float machinery too).
                // Falling through (return false) lets the default bytecode-inlining path handle those
                // untouched, exactly as before this plugin intercepted getFloat32 at all.
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
                CUDAConvertHalfToFloat convertHalfToFloat = new CUDAConvertHalfToFloat(placeholder);
                b.getGraph().addOrUnique(convertHalfToFloat);
                b.push(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });

    }

}