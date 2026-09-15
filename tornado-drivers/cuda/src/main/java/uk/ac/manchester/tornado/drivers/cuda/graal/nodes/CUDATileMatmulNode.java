/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.type.StampFactory;
import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.FixedWithNextNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.api.tile.DType;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDATileStmt;

/**
 * An accumulator-free tile product, {@code ct::matmul}.
 *
 * <p>
 * Separate from {@link CUDATileMmaNode} because the result type comes from a different place:
 * {@code mma} carries the accumulator's type and shape, whereas here both are derived from the
 * operands - the shape by contracting them and the element type through
 * {@link DType#matmulResultType()}.
 * </p>
 */
@NodeInfo
public class CUDATileMatmulNode extends FixedWithNextNode implements LIRLowerable, CUDATileNode {

    public static final NodeClass<CUDATileMatmulNode> TYPE = NodeClass.create(CUDATileMatmulNode.class);

    @Input
    protected ValueNode tileA;
    @Input
    protected ValueNode tileB;

    private final DType resultType;
    private final int[] resultShape;

    public CUDATileMatmulNode(ValueNode tileA, ValueNode tileB, DType resultType, int[] resultShape) {
        super(TYPE, StampFactory.forKind(JavaKind.Object));
        this.tileA = tileA;
        this.tileB = tileB;
        this.resultType = resultType;
        this.resultShape = resultShape;
    }

    @Override
    public String tileOperationName() {
        return "tile matmul";
    }

    @Override
    public DType tileDType() {
        return resultType;
    }

    @Override
    public int[] tileShape() {
        return resultShape;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(LIRKind.value(CUDAKind.TILE));
        tool.append(new CUDATileStmt.TileMatmulStmt(result, gen.operand(tileA), gen.operand(tileB), resultType, resultShape));
        gen.setResult(this, result);
    }
}
