/*
 * Copyright (c) 2018 - 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: Michalis Papadimitriou
 *
 */
package uk.ac.manchester.tornado.runtime.graal.phases;

import java.util.LinkedHashMap;
import java.util.Optional;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.FloatEqualsNode;
import org.graalvm.compiler.nodes.calc.FloatLessThanNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.RemNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.Phase;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.runtime.profiler.FeatureExtractionUtilities;
import uk.ac.manchester.tornado.runtime.profiler.ProfilerCodeFeatures;

public class TornadoFeatureExtraction extends Phase {
    private TornadoDeviceContext tornadoDeviceContext;

    public TornadoFeatureExtraction(TornadoDeviceContext tornadoDeviceContext) {
        this.tornadoDeviceContext = tornadoDeviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph) {
        LinkedHashMap<ProfilerCodeFeatures, Integer> IRFeatures;

        IRFeatures = extractFeatures(graph, FeatureExtractionUtilities.initializeFeatureMap());

        FeatureExtractionUtilities.emitFeatureProfileJsonFile(IRFeatures, graph, tornadoDeviceContext);
    }

    private LinkedHashMap<ProfilerCodeFeatures, Integer> extractFeatures(StructuredGraph graph, LinkedHashMap<ProfilerCodeFeatures, Integer> initMap) {
        LinkedHashMap<ProfilerCodeFeatures, Integer> irFeatures = initMap;
        for (Node node : graph.getNodes().snapshot()) {
            if (node instanceof MulNode || node instanceof AddNode || node instanceof SubNode //
                    || node instanceof SignedDivNode || node instanceof org.graalvm.compiler.nodes.calc.AddNode || node instanceof IntegerDivRemNode //
                    || node instanceof RemNode || node instanceof SignedRemNode || node instanceof FloatEqualsNode || node instanceof IntegerEqualsNode //
            ) {
                updateWithType(irFeatures, node);
            } else if (node instanceof MarkOCLWriteNode || node instanceof WriteNode) {
                updateMemoryAccesses(irFeatures, node, false);
            } else if (node instanceof FloatingReadNode || node instanceof ReadNode) {
                updateMemoryAccesses(irFeatures, node, true);
            } else if (node instanceof LoopBeginNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.LOOPS);
            } else if (node instanceof IfNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.IFS);
            } else if (node instanceof IntegerSwitchNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.SWITCH);
                int countCases = irFeatures.get(ProfilerCodeFeatures.CASE);
                irFeatures.put(ProfilerCodeFeatures.CASE, (countCases + ((IntegerSwitchNode) node).getSuccessorCount()));
            } else if (node instanceof MarkVectorLoad || node instanceof MarkVectorValueNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.VECTORS);
            } else if (node instanceof IntegerLessThanNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.I_CMP);
            } else if (node instanceof OrNode || node instanceof AndNode || node instanceof LeftShiftNode //
                    || node instanceof RightShiftNode || node instanceof UnsignedRightShiftNode //
                    || node instanceof ShiftNode || node instanceof XorNode) {
                updateWithType(irFeatures, node);
            } else if (node instanceof MarkGlobalThreadID) {
                updateCounter(irFeatures, ProfilerCodeFeatures.PARALLEL_LOOPS);
            } else if (node instanceof ConstantNode || node instanceof ParameterNode || node instanceof SignExtendNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.PRIVATE_LOADS);
                updateCounter(irFeatures, ProfilerCodeFeatures.PRIVATE_STORES);
            } else if (node instanceof MarkCastNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.CAST);
            } else if (node instanceof FloatLessThanNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.F_CMP);
            } else if (node instanceof MarkFloatingPointIntrinsicsNode || node instanceof UnaryArithmeticNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.F_MATH);
            } else if (node instanceof MarkIntIntrinsicNode) {
                updateCounter(irFeatures, ProfilerCodeFeatures.I_MATH);
            }
        }
        return irFeatures;
    }

    private JavaKind getPrimitiveType(Node inputNode) {
        return ((ValueNode) inputNode).getStackKind();
    }

    private void updateCounter(LinkedHashMap<ProfilerCodeFeatures, Integer> irFeatures, ProfilerCodeFeatures feature) {
        irFeatures.put(feature, (irFeatures.get(feature) + 1));
    }

    private void updateWithType(LinkedHashMap<ProfilerCodeFeatures, Integer> irFeatures, Node node) {
        JavaKind opType = getPrimitiveType(node);
        if (opType == (JavaKind.Boolean) || (opType == JavaKind.Char) || (opType == JavaKind.Int) || (opType == JavaKind.Short) || (opType == JavaKind.Long)) {
            updateCounter(irFeatures, ProfilerCodeFeatures.INTEGER_OPS);
        } else if ((opType == (JavaKind.Double))) {
            updateCounter(irFeatures, ProfilerCodeFeatures.FLOAT_OPS);
            updateCounter(irFeatures, ProfilerCodeFeatures.DOUBLES);
        } else if ((opType == JavaKind.Float)) {
            updateCounter(irFeatures, ProfilerCodeFeatures.FLOAT_OPS);
            updateCounter(irFeatures, ProfilerCodeFeatures.FP32);
        }
    }

    private void updateMemoryAccesses(LinkedHashMap<ProfilerCodeFeatures, Integer> irFeatures, Node node, boolean isLoad) {
        for (Node memOpNode : node.inputs().filter(AddressNode.class)) {
            for (Node addressInput : memOpNode.inputs()) {
                if (addressInput instanceof MarkLocalArray) {
                    if (isLoad) {
                        updateCounter(irFeatures, ProfilerCodeFeatures.LOCAL_LOADS);
                    } else {
                        updateCounter(irFeatures, ProfilerCodeFeatures.LOCAL_STORES);
                    }
                } else if (addressInput instanceof ParameterNode) {
                    if (isLoad) {
                        updateCounter(irFeatures, ProfilerCodeFeatures.GLOBAL_LOADS);
                    } else {
                        updateCounter(irFeatures, ProfilerCodeFeatures.GLOBAL_STORES);
                    }
                } else if (addressInput instanceof FloatingReadNode && !isLoad) {
                    // This covers the case of storing to global from a vector type
                    updateCounter(irFeatures, ProfilerCodeFeatures.GLOBAL_STORES);
                }
            }
        }
    }
}