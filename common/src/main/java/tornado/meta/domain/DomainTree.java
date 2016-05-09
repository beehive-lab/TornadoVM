package tornado.meta.domain;

public class DomainTree {
	
	private final Domain[] domains;
	
	public DomainTree(final int depth){
		this.domains = new Domain[depth];
	}
	
	public void set(int index,final Domain domain){
		domains[index] = domain;
	}
	
	public Domain get(int index){
		return domains[index];
	}
	
	public int getDepth(){
		return domains.length;
	}
	
	public String toString(){
		final StringBuilder sb = new StringBuilder();
		sb.append(String.format("num domains=%d :",domains.length));
		sb.append("{ ");
		for(Domain dom : domains){
			sb.append(String.format("%s, ",dom.toString()));
		}
		sb.setLength(sb.length()-1);
		sb.append(" }");
		
		
		return sb.toString().trim();
	}

}
