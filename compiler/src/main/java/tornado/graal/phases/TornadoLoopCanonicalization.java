package tornado.graal.phases;

import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.EndNode;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.LoopEndNode;
import com.oracle.graal.nodes.MergeNode;
import com.oracle.graal.nodes.PhiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.Phase;

public class TornadoLoopCanonicalization extends Phase {

	@Override
	protected void run(StructuredGraph graph) {
		
		
		if(graph.hasLoops()){
		final LoopsData data = new LoopsData(graph);

		for (LoopEx loop : data.innerFirst()) {
			int numBackedges = loop.loopBegin().loopEnds().count();
			if(numBackedges > 1){
				final LoopBeginNode loopBegin = loop.loopBegin();
				
				final LoopEndNode[] oldLoopEndNodes = new LoopEndNode[numBackedges];
				final EndNode[] replacementEndNodes = new EndNode[numBackedges];
				final PhiNode[] oldPhiNodes = new PhiNode[loopBegin.phis().count()];
				final PhiNode[] newPhiNodes = new PhiNode[loopBegin.phis().count()];
				
				int index = 0;
				for(final PhiNode phi : loopBegin.phis()){
					oldPhiNodes[index] = phi;
					index++;
				}
				
				index = 0;
				for(final LoopEndNode oldLoopEnd : loopBegin.loopEnds().distinct()){
					oldLoopEndNodes[index] = oldLoopEnd;
					index++;
				}
				
				final MergeNode mergeNode = graph.addWithoutUnique(new MergeNode());
				final LoopEndNode newLoopEnd = graph.addWithoutUnique(new LoopEndNode(loop.loopBegin()));
				mergeNode.setNext(newLoopEnd);
				
				index = 0;
				for(index=0;index<numBackedges;index++){
					replacementEndNodes[index] = graph.addWithoutUnique(new EndNode());
					mergeNode.addForwardEnd(replacementEndNodes[index]);
				}
				
				index = 0;
				for(final PhiNode oldPhi : oldPhiNodes){
					PhiNode newPhi =  (PhiNode) oldPhi.copyWithInputs(true);
					newPhi.clearValues();
					newPhi.setMerge(mergeNode);
					
					for(int i=0;i<numBackedges;i++){
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
				
				
				for(index=0;index<numBackedges;index++){
					loopBegin.removeEnd(oldLoopEndNodes[index]);
					oldLoopEndNodes[index].replaceAndDelete(replacementEndNodes[index]);
				}
				
				index=0;
				for(final PhiNode oldPhi : oldPhiNodes){
					oldPhi.initializeValueAt(1, newPhiNodes[index]);
					index++;
				}
				
				
				
				
				
				
				
				
				
			}
		}
		
		}
		
	}

}
