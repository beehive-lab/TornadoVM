/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package tornado.graal.phases;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.BasePhase;
import java.util.Collections;
import java.util.List;
import tornado.common.Tornado;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.meta.domain.DomainTree;
import tornado.meta.domain.IntDomain;

public class TornadoShapeAnalysis extends BasePhase<TornadoHighTierContext> {

    private static final int resolveInt(ValueNode value) {
        if (value instanceof ConstantNode) {
            return ((ConstantNode) value).asJavaConstant().asInt();
        } else {
            //TornadoInternalError.shouldNotReachHere();
            return Integer.MIN_VALUE;
        }
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (!context.hasMeta()) {
            return;
        }

        final List<ParallelRangeNode> ranges = graph.getNodes().filter(ParallelRangeNode.class).snapshot();

        Collections.sort(ranges);

        final DomainTree domainTree = new DomainTree(ranges.size());

        int lastIndex = -1;
        boolean valid = true;
        for (int i = 0; i < ranges.size(); i++) {
            final ParallelRangeNode range = ranges.get(i);
            final int index = range.index();
            if (index != lastIndex && resolveInt(range.offset().value()) != Integer.MIN_VALUE && resolveInt(range.stride().value()) != Integer.MIN_VALUE && resolveInt(range.value()) != Integer.MIN_VALUE) {
                domainTree.set(index, new IntDomain(resolveInt(range.offset().value()), resolveInt(range.stride().value()), resolveInt(range.value())));
            } else {
                valid = false;
                Tornado.info("unsupported multiple parallel loops");
                break;
            }
            lastIndex = index;
        }

        if (valid) {
            Tornado.trace("loop nest depth = %d", domainTree.getDepth());

            Tornado.debug("discovered parallel domain: %s", domainTree);

            context.getMeta().setDomain(domainTree);
        }

    }

}
