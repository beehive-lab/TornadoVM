/*
 * Copyright 2012 James Clarkson.
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
 */
package tornado.graal.phases;

import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;

import static tornado.graal.loop.LoopCanonicalizer.canonicalizeLoop;

public class TornadoLoopCanonicalization extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);

            for (LoopEx loop : data.innerFirst()) {
                int numBackedges = loop.loopBegin().loopEnds().count();
                if (numBackedges > 1) {
                    final LoopBeginNode loopBegin = loop.loopBegin();
                    canonicalizeLoop(graph, loopBegin);
                }
            }

        }

    }

}
