package tornado.runtime.graph;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import tornado.common.RuntimeUtilities;

public class GraphAssembler {
	public final static byte BEGIN = 11; 	  //
	public final static byte ALLOCATE = 19; // ALLOCATE(obj,dest)
	public final static byte COPY_IN = 20; // COPY(obj, src, dest)
	public final static byte STREAM_IN = 22; // STREAM_IN(obj, src, dest)
	public final static byte STREAM_OUT = 23; // STREAM_OUT(obj, src, dest)
	public final static byte CONTEXT = 21; // CONTEXT(ctx)
	public final static byte ADD_DEP = 24; // ADD_DEP(list index)
	public final static byte LAUNCH = 26; // LAUNCH(dep list index)
	public final static byte BARRIER = 27; 
	public final static byte SETUP = 10;  //BEGIN(num contexts, num stacks, num dep lists)
	public final static byte END = 0;
	
	public final static byte CONSTANT_ARG = 32;
	public final static byte REFERENCE_ARG = 33;

	private final ByteBuffer buffer;
	public GraphAssembler(byte[] code){
		 buffer = ByteBuffer.wrap(code);
		 buffer.order(ByteOrder.LITTLE_ENDIAN);
	}
	
	public void reset(){
		buffer.rewind();
	}
	
	public int position(){
		return buffer.position();
	}
	
	public void begin(){
		buffer.put(BEGIN);
	}
	
	public void end(){
		buffer.put(END);
	}
	
	public void setup(int numContexts, int numStacks, int numDeps){
		buffer.put(SETUP);
		buffer.putInt(numContexts);
		buffer.putInt(numStacks);
		buffer.putInt(numDeps);
	}
	
	public void addDependency(int index){
		buffer.put(ADD_DEP);
		buffer.putInt(index);
	}
	
	public void context(int index){
		buffer.put(CONTEXT);
		buffer.putInt(index);
	}
	
	public void allocate(int object, int ctx){
		buffer.put(ALLOCATE);
		buffer.putInt(object);
		buffer.putInt(ctx);
	}
	
	public void copyToContext(int obj, int ctx, int dep){
		buffer.put(COPY_IN);
		buffer.putInt(obj);
		buffer.putInt(ctx);
		buffer.putInt(dep);
	}
	
	public void streamInToContext(int obj, int ctx, int dep){
		buffer.put(STREAM_IN);
		buffer.putInt(obj);
		buffer.putInt(ctx);
		buffer.putInt(dep);
	}
	
	public void streamOutOfContext(int obj, int ctx, int dep){
		buffer.put(STREAM_OUT);
		buffer.putInt(obj);
		buffer.putInt(ctx);
		buffer.putInt(dep);
	}
	
	public void launch(int gtid, int ctx, int task, int numParameters,int dep){
		buffer.put(LAUNCH);
		buffer.putInt(gtid);
		buffer.putInt(ctx);
		buffer.putInt(task);
		buffer.putInt(numParameters);
		buffer.putInt(dep);
	}
	
	public void barrier(int dep){
		buffer.put(BARRIER);
		buffer.putInt(dep);
	}
	
	public void constantArg(int index){
		buffer.put(CONSTANT_ARG);
		buffer.putInt(index);
	}
	
	public void referenceArg(int index){
		buffer.put(REFERENCE_ARG);
		buffer.putInt(index);
	}
	
	public void dump() {
		final int width = 16;
		System.out.printf("code  : capacity = %s, in use = %s \n",
				RuntimeUtilities.humanReadableByteCount(buffer.capacity(),
						true), RuntimeUtilities.humanReadableByteCount(
						buffer.position(), true));
		for (int i = 0; i < buffer.position(); i += width) {
			System.out.printf("[0x%04x]: ", i);
			for (int j = 0; j < Math.min(buffer.capacity() - i, width); j++) {
				if (j % 2 == 0) {
					System.out.printf(" ");
				}
				if (j < buffer.position() - i) {
					System.out.printf("%02x", buffer.get(i + j));
				} else {
					System.out.printf("..");
				}
			}
			System.out.println();
		}
	}
	
}
