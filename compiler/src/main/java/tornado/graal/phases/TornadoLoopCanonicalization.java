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
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.Phase;

public class TornadoLoopCanonicalization extends Phase {

    @Override
    protected void run(StructuredGraph graph) {

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);

            for (LoopEx loop : data.innerFirst()) {
                int numBackedges = loop.loopBegin().loopEnds().count();
                if (numBackedges > 1) {
                    final LoopBeginNode loopBegin = loop.loopBegin();

                    final LoopEndNode[] oldLoopEndNodes = new LoopEndNode[numBackedges];
                    final EndNode[] replacementEndNodes = new EndNode[numBackedges];
                    final PhiNode[] oldPhiNodes = new PhiNode[loopBegin.phis().count()];
                    final PhiNode[] newPhiNodes = new PhiNode[loopBegin.phis().count()];

                    int index = 0;
                    for (final PhiNode phi : loopBegin.phis()) {
                        oldPhiNodes[index] = phi;
                        index++;
                    }

                    index = 0;
                    // assumes loopEnds.distinct()
                    for (final LoopEndNode oldLoopEnd : loopBegin.loopEnds()) {
                        oldLoopEndNodes[index] = oldLoopEnd;
                        index++;
                    }

                    final MergeNode mergeNode = graph.addWithoutUnique(new MergeNode());
                    final LoopEndNode newLoopEnd = graph.addWithoutUnique(new LoopEndNode(loop.loopBegin()));
                    mergeNode.setNext(newLoopEnd);

                    index = 0;
                    for (index = 0; index < numBackedges; index++) {
                        replacementEndNodes[index] = graph.addWithoutUnique(new EndNode());
                        mergeNode.addForwardEnd(replacementEndNodes[index]);
                    }

                    index = 0;
                    for (final PhiNode oldPhi : oldPhiNodes) {
                        PhiNode newPhi = (PhiNode) oldPhi.copyWithInputs(true);
                        newPhi.clearValues();
                        newPhi.setMerge(mergeNode);

                        for (int i = 0; i < numBackedges; i++) {
                            newPhi.initializeValueAt(i, oldPhi.valueAt(oldLoopEndNodes[i]));
                            //oldPhi.setValueAt(oldLoopEndNodes[i], null);
                        }

//					PhiNode replacementPhi = (PhiNode) oldPhi.copyWithInputs(true);
//					replacementPhi.clearValues();
//					replacementPhi.initializeValueAt(0, oldPhi.valueAt(0));
//					replacementPhi.initializeValueAt(1, newPhi);
//
//					oldPhi.replaceAndDelete(replacementPhi);
                        newPhiNodes[index] = newPhi;
                        index++;
                    }

                    for (index = 0; index < numBackedges; index++) {
                        loopBegin.removeEnd(oldLoopEndNodes[index]);
                        oldLoopEndNodes[index].replaceAndDelete(replacementEndNodes[index]);
                    }

                    index = 0;
                    for (final PhiNode oldPhi : oldPhiNodes) {
                        oldPhi.initializeValueAt(1, newPhiNodes[index]);
                        index++;
                    }

                }
            }

        }

    }

}
