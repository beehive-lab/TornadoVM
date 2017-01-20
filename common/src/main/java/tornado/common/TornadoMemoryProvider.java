package tornado.common;

public interface TornadoMemoryProvider {

    public long getCallStackSize();

    public long getCallStackAllocated();

    public long getCallStackRemaining();

    public long getHeapSize();

    public long getHeapRemaining();

    public long getHeapAllocated();

    public boolean isInitialised();

}
