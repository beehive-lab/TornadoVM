/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2019, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.runtime.tasks;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.HotSpotGraalOptionValues;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.BinaryArithmeticNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.StoreIndexedNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import uk.ac.manchester.tornado.api.annotations.Reduce;
import uk.ac.manchester.tornado.api.common.TaskPackage;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.runtime.tasks.meta.MetaReduceCodeAnalysis;

/**
 * Code analysis class for Tornado
 *
 */
public class CodeAnalysis {

    // @formatter:off
    public enum REDUCE_OPERATION {
        ADD, 
        MUL, 
        MIN, 
        MAX;
    };
    // @formatter:on

    public static ArrayList<REDUCE_OPERATION> getReduceOperation(StructuredGraph graph, ArrayList<Integer> reduceIndexes) {
        ArrayList<ValueNode> reduceOperation = new ArrayList<>();
        for (Integer paramIndex : reduceIndexes) {

            ParameterNode parameterNode = graph.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();
            Iterator<Node> iterator = usages.iterator();
            // Get Input-Range for the reduction loop
            while (iterator.hasNext()) {
                Node node = iterator.next();
                if (node instanceof StoreIndexedNode) {
                    StoreIndexedNode store = (StoreIndexedNode) node;
                    if (store.value() instanceof BinaryNode || store.value() instanceof BinaryArithmeticNode) {
                        ValueNode value = store.value();
                        reduceOperation.add(value);
                    } else if (store.value() instanceof InvokeNode) {
                        InvokeNode invoke = (InvokeNode) store.value();
                        invoke.callTarget().targetName().startsWith("Math");
                        reduceOperation.add(invoke);
                    }
                }
            }
        }

        // Match VALUE_NODE with OPERATION
        ArrayList<REDUCE_OPERATION> operations = new ArrayList<>();
        for (ValueNode operation : reduceOperation) {
            if (operation instanceof AddNode) {
                operations.add(REDUCE_OPERATION.ADD);
            } else if (operation instanceof MulNode) {
                operations.add(REDUCE_OPERATION.MUL);
            } else if (operation instanceof InvokeNode) {
                InvokeNode invoke = (InvokeNode) operation;
                if (invoke.callTarget().targetName().equals("Math.max")) {
                    operations.add(REDUCE_OPERATION.MAX);
                } else if (invoke.callTarget().targetName().equals("Math.min")) {
                    operations.add(REDUCE_OPERATION.MIN);
                } else {
                    throw new TornadoRuntimeException("[ERROR] Automatic reduce operation not supported yet: " + operation);
                }
            } else {
                throw new TornadoRuntimeException("[ERROR] Automatic reduce operation not supported yet: " + operation);
            }
        }
        return operations;
    }

    /**
     * A method can apply multiple reduction variables. We return a list of all
     * its loop bounds.
     * 
     * @param graph
     * @param reduceIndexes
     * @return ArrayList<ValueNode>
     */
    public static ArrayList<ValueNode> findLoopUpperBoundNode(StructuredGraph graph, ArrayList<Integer> reduceIndexes) {
        ArrayList<ValueNode> loopBound = new ArrayList<>();
        for (Integer paramIndex : reduceIndexes) {
            ParameterNode parameterNode = graph.getParameter(paramIndex);
            NodeIterable<Node> usages = parameterNode.usages();
            Iterator<Node> iterator = usages.iterator();

            // Get Input-Range for the reduction loop
            while (iterator.hasNext()) {
                Node node = iterator.next();
                if (node instanceof StoreIndexedNode) {
                    StoreIndexedNode store = (StoreIndexedNode) node;

                    Node aux = store;
                    LoopBeginNode loopBegin = null;
                    ArrayLengthNode arrayLength = null;

                    while (!(aux instanceof LoopBeginNode)) {
                        aux = aux.predecessor();
                        if (aux instanceof StartNode) {
                            break;
                        } else if (aux instanceof LoopBeginNode) {
                            loopBegin = (LoopBeginNode) aux;
                        } else if (aux instanceof ArrayLengthNode) {
                            arrayLength = (ArrayLengthNode) aux;
                        }
                    }

                    if (loopBegin != null) {
                        loopBound.add(arrayLength.array());
                    }
                }
            }
        }
        return loopBound;
    }

    /**
     * It obtains a list of reduce parameters for each task.
     * 
     * @return {@link MetaReduceTasks}
     */
    public static MetaReduceCodeAnalysis analysisTaskSchedule(String taskScheduleID, ArrayList<TaskPackage> taskPackages, ArrayList<Object> streamInObjects, ArrayList<Object> streamOutObjects) {
        int taskIndex = 0;
        int inputSize = 0;

        HashMap<Integer, MetaReduceTasks> tableMetaReduce = new HashMap<>();

        for (TaskPackage tpackage : taskPackages) {

            Object taskCode = tpackage.getTaskParameters()[0];
            StructuredGraph graph = CodeAnalysis.buildHighLevelGraalGraph(taskCode);

            Annotation[][] annotations = graph.method().getParameterAnnotations();
            ArrayList<Integer> reduceIndexes = new ArrayList<>();
            for (int paramIndex = 0; paramIndex < annotations.length; paramIndex++) {
                for (Annotation annotation : annotations[paramIndex]) {
                    if (annotation instanceof Reduce) {
                        reduceIndexes.add(paramIndex);
                    }
                }
            }

            if (reduceIndexes.isEmpty()) {
                taskIndex++;
                continue;
            }

            // Perform PE to obtain the value of the upper-bound loop
            ArrayList<ValueNode> loopBound = findLoopUpperBoundNode(graph, reduceIndexes);
            for (int i = 0; i < graph.method().getParameters().length; i++) {
                for (int k = 0; k < loopBound.size(); k++) {
                    if (loopBound.get(k).equals(graph.getParameter(i))) {
                        Object object = taskPackages.get(taskIndex).getTaskParameters()[i + 1];
                        inputSize = Array.getLength(object);
                    }
                }
            }

            if (!reduceIndexes.isEmpty()) {
                MetaReduceTasks reduceTasks = new MetaReduceTasks(taskIndex, graph, reduceIndexes, inputSize);
                tableMetaReduce.put(taskIndex, reduceTasks);
            }
            taskIndex++;
        }

        return (tableMetaReduce.isEmpty() ? null : new MetaReduceCodeAnalysis(tableMetaReduce));

    }

    /**
     * Build Graal-IR for an input Java method
     * 
     * @param taskInputCode
     *            Input Java method to be compiled by Graal
     * @return {@link StructuredGraph} Control Flow and DataFlow Graphs for the
     *         input method in the Graal-IR format,
     */
    public static StructuredGraph buildHighLevelGraalGraph(Object taskInputCode) {
        Method methodToCompile = TaskUtils.resolveMethodHandle(taskInputCode);
        GraalJVMCICompiler graalCompiler = (GraalJVMCICompiler) JVMCI.getRuntime().getCompiler();
        RuntimeProvider capability = graalCompiler.getGraalRuntime().getCapability(RuntimeProvider.class);
        Backend backend = capability.getHostBackend();
        Providers providers = backend.getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod resolvedJavaMethod = metaAccess.lookupJavaMethod(methodToCompile);
        CompilationIdentifier compilationIdentifier = backend.getCompilationIdentifier(resolvedJavaMethod);
        EconomicMap<OptionKey<?>, Object> opts = OptionValues.newOptionMap();
        opts.putAll(HotSpotGraalOptionValues.HOTSPOT_OPTIONS.getMap());
        OptionValues options = new OptionValues(opts);
        StructuredGraph graph = new StructuredGraph.Builder(options, AllowAssumptions.YES).method(resolvedJavaMethod).compilationId(compilationIdentifier).build();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getDefault(new Plugins(new InvocationPlugins()))));
        graphBuilderSuite.apply(graph, new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL));
        return graph;
    }
}
