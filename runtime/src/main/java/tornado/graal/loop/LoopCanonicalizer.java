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
package tornado.graal.loop;

import org.graalvm.compiler.nodes.*;

/**
 *
 * @author James Clarkson
 */
public class LoopCanonicalizer {

    public static void canonicalizeLoop(StructuredGraph graph, LoopBeginNode loopBegin) {
        int numBackedges = loopBegin.loopEnds().count();
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
        final LoopEndNode newLoopEnd = graph.addWithoutUnique(new LoopEndNode(loopBegin));
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
            }

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
