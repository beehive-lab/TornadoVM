/*
 * Copyright (c) 2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import java.util.Optional;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.phases.Phase;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;

public class OCLFP64SupportPhase extends Phase {

    private TornadoDeviceContext deviceContext;

    public OCLFP64SupportPhase(TornadoDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    /**
     * This method evaluates if a stamp is of type that requires double floating
     * point precision, or not.
     *
     * @param stamp
     * @return returns true if stamp is f64 or double
     */
    private boolean isStampFP64Type(Stamp stamp) {
        return stamp.toString().contains("f64") || stamp.toString().toLowerCase().contains("double");
    }

    /**
     * This method checks if a stamp requires double floating point precision or
     * not. Additionally, the deviceContext is used to check if double precision is
     * supported by the target device. In OpenCL, a device supports double precision
     * if the cl_khr_fp64 attribute is enabled.
     * 
     * If the input stamp requires double precision and the target device does not
     * support this feature, a {@link TornadoDeviceFP64NotSupported} exception is
     * thrown.
     *
     * @param stamp
     *            a stamp of a node
     */
    private void checkStampForFP64Support(Stamp stamp) {
        boolean isStampFP64Type = isStampFP64Type(stamp);
        if (isStampFP64Type && !deviceContext.isFP64Supported()) {
            throw new TornadoDeviceFP64NotSupported("The current OpenCL device (" + deviceContext.getDeviceName() + ") does not support FP64");
        }
    }

    @Override
    protected void run(StructuredGraph graph) {

        graph.getNodes().filter(WriteNode.class).forEach(writeNode -> checkStampForFP64Support(writeNode.getAccessStamp(NodeView.DEFAULT)));

        graph.getNodes().filter(ReadNode.class).forEach(readNode -> checkStampForFP64Support(readNode.getAccessStamp(NodeView.DEFAULT)));

        graph.getNodes().filter(OCLFPUnaryIntrinsicNode.class).forEach(node -> checkStampForFP64Support(node.stamp(NodeView.DEFAULT)));

        graph.getNodes().filter(OCLFPBinaryIntrinsicNode.class).forEach(node -> checkStampForFP64Support(node.stamp(NodeView.DEFAULT)));

        graph.getNodes().filter(SqrtNode.class).forEach(node -> checkStampForFP64Support(node.stamp(NodeView.DEFAULT)));

    }
}
