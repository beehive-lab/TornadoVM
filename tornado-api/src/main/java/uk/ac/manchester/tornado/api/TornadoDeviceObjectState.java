package uk.ac.manchester.tornado.api;

public interface TornadoDeviceObjectState {

    public void setBuffer(ObjectBuffer value);

    public boolean hasBuffer();

    public ObjectBuffer getBuffer();

    public boolean isValid();

    public boolean isModified();

    public void invalidate();

    public boolean hasContents();

    public void setContents(boolean value);

    public void setModified(boolean value);

    public void setValid(boolean value);

    public long getAddress();

    public long getOffset();
}
