package tornado.drivers.opencl.graal.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.nodes.AbstractMergeNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.cfg.Block;

@Deprecated
public class OCLSourceEmitter {
	
	 /**
     * The initial capacities of the worklists used for iteratively finding the block order.
     */
    private static final int INITIAL_WORKLIST_CAPACITY = 10;

    
	 /**
     * Initializes the priority queue used for the work list of blocks and adds the start block.
     */
    private static <T extends AbstractBlockBase<T>> Deque<T> initializeWorklist(T startBlock, BitSet visitedBlocks) {
    	Deque<T> result = new ArrayDeque<>(INITIAL_WORKLIST_CAPACITY);
        result.push(startBlock);
        visitedBlocks.set(startBlock.getId());
        return result;
    }
    
	 /**
     * Computes the block order used for code emission.
     *
     * @return sorted list of blocks
     */
    public static <T extends AbstractBlockBase<T>> List<T> computeCodeEmittingOrder(int blockCount, T startBlock) {
        List<T> order = new ArrayList<>();
        BitSet visitedBlocks = new BitSet(blockCount);
        Deque<T> worklist = initializeWorklist(startBlock, visitedBlocks);
        computeCodeEmittingOrder(order, worklist, visitedBlocks);
      //  assert checkOrder(order, blockCount);
        return order;
    }
    
    /**
     * Iteratively adds paths to the code emission block order.
     */
    private static <T extends AbstractBlockBase<T>> void computeCodeEmittingOrder(List<T> order, Deque<T> worklist, BitSet visitedBlocks) {
        while (!worklist.isEmpty()) {
            T nextImportantPath = worklist.pop();
            System.out.printf("pop  : block=%s\n",nextImportantPath.toString());
            addPathToCodeEmittingOrder(nextImportantPath, order, worklist, visitedBlocks);
        }
    }

    
    /**
     * Add a linear path to the code emission order greedily following the most likely successor.
     */
    private static <T extends AbstractBlockBase<T>> void addPathToCodeEmittingOrder(T initialBlock, List<T> order, Deque<T> worklist, BitSet visitedBlocks) {
        T block = initialBlock;
        while (block != null) {
        	final List<T> dominates = block.getDominated();
        	
        	System.out.printf("visit: block=%s, dominates=%d, isIf=%s, isLoopHeader=%s, isMerge=%s, isLoopEnd=%s\n",block.toString(),dominates.size(),isIfBlock((Block) block),block.isLoopHeader(),isMergeBlock((Block) block),block.isLoopEnd());
        	
        	addBlock(block, order);
        	
        	
        	T successor = null;
        	if(block.isLoopHeader()){
        		final T exit = block.getLoop().getExits().get(0);
        		final boolean inverted = (dominates.get(0).equals(exit));
    			successor = (inverted) ? dominates.get(1) : dominates.get(0);
    			pushSuccessor(exit,worklist,visitedBlocks);
        	} else if(isIfBlock((Block) block)){
        		successor = dominates.get(0);
        		System.out.printf("     : \tenqueue false=%s",dominates.get(1));
        		
        		if(dominates.size() == 3 && !dominates.get(2).isLoopEnd()){
        			pushSuccessor(dominates.get(2),worklist,visitedBlocks);	
        			System.out.printf(", merge=%s",dominates.get(2));
        		}
        		System.out.println();
        		
        		pushSuccessor(dominates.get(1),worklist,visitedBlocks);
        	} else if (dominates.size() == 1 ){
        		successor = dominates.get(0);
        	} else if (block.getSuccessorCount() == 1){
            	successor = block.getSuccessors().get(0);
            	if(!(isMergeBlock((Block) successor) && successor.isLoopEnd())){
            		System.out.printf("bad successor: %s\n",successor);
            		//enqueueSuccessor(successor, worklist,visitedBlocks);	
            		successor = null;
            	}
        	}
            block = successor;
        }
        
        
    }
    
    private static boolean isIfBlock(Block block) {
       return block.getEndNode() instanceof IfNode;
    }
    
    private static boolean isMergeBlock(Block block) {
        return block.getBeginNode() instanceof AbstractMergeNode;
     }
    
    /**
     * Adds a block to the ordering.
     */
    private static <T extends AbstractBlockBase<T>> void addBlock(T header, List<T> order) {
       // assert !order.contains(header) : "Cannot insert block twice";
        order.add(header);
    }
    
    
    private static <T extends AbstractBlockBase<T>> void pushSuccessor(T successor, Deque<T> worklist, BitSet visitedBlocks) {
            if (!visitedBlocks.get(successor.getId())) {
                visitedBlocks.set(successor.getId());
                worklist.push(successor);
            }
    }
    
    /**
     * Checks that the ordering contains the expected number of blocks.
     */
    private static boolean checkOrder(List<? extends AbstractBlockBase<?>> order, int expectedBlockCount) {
        assert order.size() == expectedBlockCount : String.format("Number of blocks in ordering (%d) does not match expected block count (%d)", order.size(), expectedBlockCount);
        return true;
    }

}
