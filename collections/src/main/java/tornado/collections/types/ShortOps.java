package tornado.collections.types;

public class ShortOps {
	public static final String fmt = "%3d";
	public static final String fmt2 = "{%3d,%3d}";
	public static final String fmt3 = "{%3d,%3d,%.3d}";
	
	public static final boolean compare(short a, short b){
		return Short.compare(a, b) == 0;
	}
}
