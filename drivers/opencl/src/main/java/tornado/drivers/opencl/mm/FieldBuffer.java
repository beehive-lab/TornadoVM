package tornado.drivers.opencl.mm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import tornado.api.Event;
import tornado.common.ObjectBuffer;
import tornado.common.Tornado;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.runtime.TornadoRuntime;

public class FieldBuffer<T> {

	private final Field				field;

	private final ObjectBuffer<T>	objectBuffer;

	public FieldBuffer(final Field field, final ObjectBuffer<T> objectBuffer) {
		this.objectBuffer = objectBuffer;
		this.field = field;
	}
	
	public boolean isFinal(){
		return Modifier.isFinal(field.getModifiers());
	}

	public void allocate(final Object ref) throws TornadoOutOfMemoryException {
		objectBuffer.allocate(getFieldValue(ref));
	}

	public Event enqueueRead(final Object ref) {
		return objectBuffer.enqueueRead(getFieldValue(ref));
	}

	public Event enqueueReadAfter(final Object ref, final Event event) {
		return objectBuffer.enqueueReadAfter(getFieldValue(ref), event);
	}

	public Event enqueueReadAfterAll(final Object ref, final List<Event> events) {
		if(Tornado.DEBUG)
			Tornado.trace("fieldBuffer: enqueueRead* - field=%s, parent=0x%x, child=0x%x",field,ref.hashCode(),getFieldValue(ref).hashCode());
		return objectBuffer.enqueueReadAfterAll(getFieldValue(ref), events);
	}

	public Event enqueueWrite(final Object ref) {
		return objectBuffer.enqueueWrite(getFieldValue(ref));
	}

	public Event enqueueWriteAfter(final Object ref, final Event event) {
		return objectBuffer.enqueueWriteAfter(getFieldValue(ref), event);
	}

	public Event enqueueWriteAfterAll(final Object ref, final List<Event> events) {
		if(Tornado.DEBUG)
			Tornado.trace("fieldBuffer: enqueueWrite* - field=%s, parent=0x%x, child=0x%x",field,ref.hashCode(),getFieldValue(ref).hashCode());
		return objectBuffer.enqueueWriteAfterAll(getFieldValue(ref), events);
	}

	public Event enqueueZeroMemory() {
		return objectBuffer.enqueueZeroMemory();
	}

	public int getAlignment() {
		return objectBuffer.getAlignment();
	}

	public long getBufferOffset() {
		return objectBuffer.getBufferOffset();
	}

	private final T getFieldValue(final Object container) {
		T value = null;
		try {
			value = (T) field.get(container);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			Tornado.warn("Illegal access to field: name=%s, object=0x%x", field.getName(), container.hashCode());
		}
		return value;
	}

	public boolean onDevice() {
		return objectBuffer.isValid();
	}

	public void read(final Object ref) {
		if(Tornado.DEBUG)
			Tornado.debug("fieldBuffer: read - field=%s, parent=0x%x, child=0x%x",field,ref.hashCode(),getFieldValue(ref).hashCode());
		objectBuffer.read(getFieldValue(ref));
	}

	public long toAbsoluteAddress() {
		return objectBuffer.toAbsoluteAddress();
	}

	public long toBuffer() {
		return objectBuffer.toBuffer();
	}

	public long toRelativeAddress() {
		return objectBuffer.toRelativeAddress();
	}

	// private final static void setFieldValue(Field field, Object container, Object value){
	// try{
	// field.set(container, value);
	// }catch(IllegalArgumentException | IllegalAccessException e){
	// Tornado.warn("Illegal access to field: name=%s, object=%s",field.getName(),container);
	// }
	// }

	public void write(final Object ref) {
		if(Tornado.DEBUG)
			Tornado.trace("fieldBuffer: write - field=%s, parent=0x%x, child=0x%x",field,ref.hashCode(),getFieldValue(ref).hashCode());
		objectBuffer.write(getFieldValue(ref));
	}
	
	public String getFieldName(){
		return field.getName();
	}

}
