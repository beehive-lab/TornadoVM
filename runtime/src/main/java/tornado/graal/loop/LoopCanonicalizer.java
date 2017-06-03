/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.graal.loop;

import com.oracle.graal.nodes.*;

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
