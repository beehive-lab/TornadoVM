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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.OrNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.extended.IntegerSwitchNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.replacements.nodes.WriteRegisterNode;

import uk.ac.manchester.tornado.runtime.profiler.FeatureExtractionUtilities;
import uk.ac.manchester.tornado.runtime.profiler.ProfilerCodeFeatures;

public class TornadoFeatureExtraction extends Phase {

    protected void run(StructuredGraph graph) {

        LinkedHashMap<ProfilerCodeFeatures, Integer> IRFeatures;

        IRFeatures = extractFeatures(graph, FeatureExtractionUtilities.initializeFeatureMap());

        FeatureExtractionUtilities.emitFeatureProfiletoJsonFile(IRFeatures, graph.name);

    }

    private LinkedHashMap<ProfilerCodeFeatures, Integer> extractFeatures(StructuredGraph graph, LinkedHashMap<ProfilerCodeFeatures, Integer> initMap) {
        LinkedHashMap<ProfilerCodeFeatures, Integer> irFeatures = initMap;
        Integer count;
        for (Node node : graph.getNodes().snapshot()) {
            if (node instanceof MulNode || node instanceof AddNode || node instanceof SubNode || node instanceof SignedDivNode || node instanceof org.graalvm.compiler.nodes.calc.AddNode) {
                count = irFeatures.get(ProfilerCodeFeatures.INTEGER);
                irFeatures.put(ProfilerCodeFeatures.INTEGER, (count + 1));
            } else if (node instanceof WriteRegisterNode || node instanceof MarkOCLWriteNode || node instanceof WriteNode) {
                for (Node nodee : node.inputs().snapshot()) {
                    if (nodee instanceof AddressNode) {
                        for (Node nodeee : nodee.inputs()) {
                            if (nodeee instanceof MarkLocalArray) {
                                count = irFeatures.get(ProfilerCodeFeatures.LOCAL_STORES);
                                irFeatures.put(ProfilerCodeFeatures.LOCAL_STORES, (count + 1));
                            } else if (nodeee instanceof ParameterNode) {
                                count = irFeatures.get(ProfilerCodeFeatures.GLOBAL_STORES);
                                irFeatures.put(ProfilerCodeFeatures.GLOBAL_STORES, (count + 1));
                            }
                        }
                    }
                }
            } else if (node instanceof FloatingReadNode || node instanceof ReadNode) {
                for (Node nodee : node.inputs().snapshot()) {
                    if (nodee instanceof AddressNode) {
                        for (Node nodeee : nodee.inputs()) {
                            if (nodeee instanceof MarkLocalArray) {
                                count = irFeatures.get(ProfilerCodeFeatures.LOCAL_LOADS);
                                irFeatures.put(ProfilerCodeFeatures.LOCAL_LOADS, (count + 1));
                            } else if (nodeee instanceof ParameterNode){
                                count = irFeatures.get(ProfilerCodeFeatures.GLOBAL_LOADS);
                                irFeatures.put(ProfilerCodeFeatures.GLOBAL_LOADS, (count + 1));
                            }
                        }
                    }
                }
            } else if (node instanceof LoopBeginNode) {
                count = irFeatures.get(ProfilerCodeFeatures.LOOPS);
                irFeatures.put(ProfilerCodeFeatures.LOOPS, (count + 1));
            } else if (node instanceof IfNode) {
                count = irFeatures.get(ProfilerCodeFeatures.IFS);
                irFeatures.put(ProfilerCodeFeatures.IFS, (count + 1));
            } else if (node instanceof IntegerSwitchNode) {
                count = irFeatures.get(ProfilerCodeFeatures.SWITCH);
                irFeatures.put(ProfilerCodeFeatures.SWITCH, (count + 1));
                int countCases = irFeatures.get(ProfilerCodeFeatures.CASE);
                irFeatures.put(ProfilerCodeFeatures.CASE, (countCases + ((IntegerSwitchNode) node).getSuccessorCount()));
            } else if (node instanceof MarkVectorLoad || node instanceof MarkVectorValueNode) {
                count = irFeatures.get(ProfilerCodeFeatures.VECTORS);
                irFeatures.put(ProfilerCodeFeatures.VECTORS, (count + 1));
            } else if (node instanceof IntegerLessThanNode) {
                count = irFeatures.get(ProfilerCodeFeatures.I_CMP);
                irFeatures.put(ProfilerCodeFeatures.I_CMP, (count + 1));
            } else if (node instanceof OrNode || node instanceof AndNode || node instanceof LeftShiftNode || node instanceof RightShiftNode || node instanceof ShiftNode || node instanceof XorNode) {
                count = irFeatures.get(ProfilerCodeFeatures.BINARY);
                irFeatures.put(ProfilerCodeFeatures.BINARY, (count + 1));
            } else if (node instanceof MarkGlobalThreadID) {
                count = irFeatures.get(ProfilerCodeFeatures.PARALLEL_LOOPS);
                irFeatures.put(ProfilerCodeFeatures.PARALLEL_LOOPS, (count + 1));
            } else if (node instanceof ConstantNode) {
                count = irFeatures.get(ProfilerCodeFeatures.PRIVATE_LOADS);
                irFeatures.put(ProfilerCodeFeatures.PRIVATE_LOADS, (count + 1));
                count = irFeatures.get(ProfilerCodeFeatures.PRIVATE_STORES);
                irFeatures.put(ProfilerCodeFeatures.PRIVATE_STORES, (count + 1));
            } else if (node instanceof MarkCastNode) {
                count = irFeatures.get(ProfilerCodeFeatures.CAST);
                irFeatures.put(ProfilerCodeFeatures.CAST, (count + 1));
            } else if (node instanceof MarkFPUnaryInstristicsNode) {
                count = irFeatures.get(ProfilerCodeFeatures.BINARY);
                irFeatures.put(ProfilerCodeFeatures.BINARY, (count + 1));
            } else if (node instanceof MarkOCLIntBinaryIntrinsicNode) {
                ;
            }
        }
        return irFeatures;
    }

}