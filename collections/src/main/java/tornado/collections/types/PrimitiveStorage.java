package tornado.collections.types;

import java.nio.Buffer;

public interface PrimitiveStorage<T extends Buffer> {

	public void loadFromBuffer(T buffer);
	public T asBuffer();
	
	public int size();
	
}
