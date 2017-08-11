/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.mm;

import java.lang.reflect.Array;
import java.util.function.Function;
import jdk.vm.ci.meta.JavaKind;
import tornado.common.exceptions.TornadoOutOfMemoryException;
import tornado.drivers.opencl.OCLDeviceContext;

import static tornado.common.Tornado.OPENCL_USE_RELATIVE_ADDRESSES;
import static tornado.common.Tornado.fatal;

/**
 *
 * @author James Clarkson
 */
public class OCLMultiDimArrayWrapper<T, E> extends OCLArrayWrapper<T> {

    private Function<OCLDeviceContext, ? extends OCLArrayWrapper<E>> innerWrapperFactory;
    private OCLLongArrayWrapper tableWrapper;
    private long[] addresses;
    private OCLArrayWrapper<E>[] wrappers;
    private boolean allocated;

    public OCLMultiDimArrayWrapper(OCLDeviceContext device, Function<OCLDeviceContext, ? extends OCLArrayWrapper<E>> factory) {
        this(device, factory, false);
    }

    public OCLMultiDimArrayWrapper(OCLDeviceContext device, Function<OCLDeviceContext, ? extends OCLArrayWrapper<E>> factory, boolean isFinal) {
        super(device, JavaKind.Object, isFinal);
        innerWrapperFactory = factory;
        tableWrapper = new OCLLongArrayWrapper(device, false);
        allocated = false;
    }

    @Override
    public long toRelativeAddress() {
        return tableWrapper.toRelativeAddress();
    }

    @Override
    public long toBuffer() {
        return tableWrapper.toBuffer();
    }

    @Override
    public long toAbsoluteAddress() {
        return tableWrapper.toAbsoluteAddress();
    }

    @Override
    public void invalidate() {
        tableWrapper.invalidate();
    }

    @Override
    public boolean isValid() {
        return tableWrapper.isValid();
    }

    @Override
    public long getBufferOffset() {
        return tableWrapper.getBufferOffset();
    }

    @Override
    public long size() {
        return tableWrapper.size();
    }

    @Override
    public void allocate(Object value) throws TornadoOutOfMemoryException {
        addresses = new long[Array.getLength(value)];
        wrappers = new OCLArrayWrapper[Array.getLength(value)];
        tableWrapper.allocate(addresses);
        allocateElements((T) value);
    }

    private void allocateElements(T values) {
        final E[] elements = innerCast(values);
        try {
            for (int i = 0; i < elements.length; i++) {
                wrappers[i] = innerWrapperFactory.apply(deviceContext);
                wrappers[i].allocate(elements[i]);
                addresses[i] = (OPENCL_USE_RELATIVE_ADDRESSES)
                        ? wrappers[i].toRelativeAddress()
                        : wrappers[i].toAbsoluteAddress();
            }
            allocated = true;
        } catch (TornadoOutOfMemoryException e) {
            fatal("OOM: multi-dim array: %s", e.getMessage());
            System.exit(-1);
        }
    }

    private int writeElements(T values) {
        final E[] elements = innerCast(values);
        for (int i = 0; i < elements.length; i++) {
            wrappers[i].enqueueWrite(elements[i], null, false);
        }
        return deviceContext.enqueueBarrier();
    }

    private int readElements(T values) {
        final E[] elements = innerCast(values);
        for (int i = 0; i < elements.length; i++) {
            wrappers[i].enqueueRead(elements[i], null, false);
        }
        return deviceContext.enqueueBarrier();
    }

    private E[] innerCast(T value) {
        return (E[]) value;
    }

    @Override
    protected int enqueueReadArrayData(long bufferId, long offset, long bytes, T value, int[] waitEvents) {
        return readElements(value);
    }

    @Override
    protected int enqueueWriteArrayData(long bufferId, long offset, long bytes, T value, int[] waitEvents) {
        tableWrapper.enqueueWrite(addresses, null, false);
        return writeElements(value);
    }

    @Override
    protected void readArrayData(long bufferId, long offset, long bytes, T value, int[] waitEvents) {
        readElements(value);
        //tableWrapper.writeArrayData(bufferId, offset, bytes, addresses, waitEvents);
    }

    @Override
    protected void writeArrayData(long bufferId, long offset, long bytes, T value, int[] waitEvents) {
        tableWrapper.enqueueWrite(addresses, null, false);
        writeElements(value);
    }

}
