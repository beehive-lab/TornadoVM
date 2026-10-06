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
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.graph.NodeInputList;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.MemoryKill;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import tornado.meta.JavaKind;
import tornado.meta.ResolvedJavaMethod;
import tornado.meta.Value;
import uk.ac.manchester.tornado.api.enums.DeviceLaunchMode;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.DeviceKernelLaunch;

/**
 * Launches a kernel from device code (CUDA Dynamic Parallelism), created from
 * {@code KernelContext.launch}. The child is compiled into the parent's compilation unit as a
 * {@code __global__} entry point. It kills all memory: the child may write any array the parent can
 * reach.
 */
@NodeInfo
public class CUDADeviceLaunchNode extends FixedWithNextNode implements LIRLowerable, MemoryKill, DeviceKernelLaunch {

    public static final NodeClass<CUDADeviceLaunchNode> TYPE = NodeClass.create(CUDADeviceLaunchNode.class);

    /** globalSizeX/Y/Z followed by localSizeX/Y/Z. */
    @Input protected NodeInputList<ValueNode> sizes;
    /** The values for the child's parameters, in order, without its KernelContext. */
    @Input protected NodeInputList<ValueNode> arguments;

    private final ResolvedJavaMethod target;
    private final DeviceLaunchMode mode;
    /** Position of the child's KernelContext parameter, or -1 if it has none. */
    private final int kernelContextIndex;

    public CUDADeviceLaunchNode(ResolvedJavaMethod target, DeviceLaunchMode mode, ValueNode[] sizes, ValueNode[] arguments, int kernelContextIndex) {
        super(TYPE, StampFactory.forVoid());
        this.target = target;
        this.mode = mode;
        this.sizes = new NodeInputList<>(this, sizes);
        this.arguments = new NodeInputList<>(this, arguments);
        this.kernelContextIndex = kernelContextIndex;
    }

    @Override
    public ResolvedJavaMethod getTargetMethod() {
        return target;
    }

    @Override
    public ValueNode[] getChildParameterValues() {
        int count = arguments.size() + (kernelContextIndex >= 0 ? 1 : 0);
        ValueNode[] values = new ValueNode[count];
        int next = 0;
        for (int i = 0; i < count; i++) {
            values[i] = i == kernelContextIndex ? null : arguments.get(next++);
        }
        return values;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] globalSizes = new Value[3];
        Value[] localSizes = new Value[3];
        for (int i = 0; i < 3; i++) {
            globalSizes[i] = gen.operand(sizes.get(i));
            localSizes[i] = gen.operand(sizes.get(i + 3));
        }
        Value[] values = new Value[arguments.size()];
        boolean[] pointers = new boolean[arguments.size()];
        for (int i = 0; i < values.length; i++) {
            ValueNode argument = arguments.get(i);
            values[i] = gen.operand(argument);
            pointers[i] = argument.getStackKind() == JavaKind.Object;
        }
        gen.getLIRGeneratorTool().append(new CUDALIRStmt.DeviceLaunchStmt(target, mode, globalSizes, localSizes, values, pointers));
    }
}
