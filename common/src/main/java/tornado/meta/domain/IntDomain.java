package tornado.meta.domain;

public class IntDomain implements Domain {

	private final int offset;
	private final int step;
	private final int length;
	
	public IntDomain(int offset, int step, int length){
		this.offset = offset;
		this.step = step;
		this.length = length;
	}
	
	public IntDomain(int length){
		this(0,1,length);
	}
	
	@Override
	public int cardinality() {
		return length;
	}

	@Override
	public int map(int index) {
		return (index * step) + offset;
	}
	
	public String toString(){
		return String.format("IntDomain: [%d, %d, %d]",offset,length,step);
	}

}
