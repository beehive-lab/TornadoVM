package tornado.runtime.api;

import java.util.HashMap;
import java.util.Map;
import tornado.common.DeviceObjectState;
import tornado.common.TornadoDevice;

public class GlobalObjectState {

    private boolean shared;
    private boolean exclusive;

    private TornadoDevice owner;

    private final Map<TornadoDevice, DeviceObjectState> deviceStates;

    public GlobalObjectState() {
        shared = false;
        exclusive = false;
        owner = null;
        deviceStates = new HashMap<>();
    }

    public boolean isShared() {
        return shared;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public TornadoDevice getOwner() {
        return owner;
    }

    public DeviceObjectState getDeviceState() {
        return getDeviceState(getOwner());
    }

    public DeviceObjectState getDeviceState(TornadoDevice device) {
        if (!deviceStates.containsKey(device)) {
            deviceStates.put(device, new DeviceObjectState());
        }
        return deviceStates.get(device);
    }

    public void setOwner(TornadoDevice device) {
        owner = device;
        if (!deviceStates.containsKey(owner)) {
            deviceStates.put(device, new DeviceObjectState());
        }
    }

    public void invalidate() {
        for (TornadoDevice device : deviceStates.keySet()) {
            final DeviceObjectState deviceState = deviceStates.get(device);
            deviceState.invalidate();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append((isExclusive()) ? "X" : "-");
        sb.append((isShared()) ? "S" : "-");
        sb.append(" ");

        if (owner != null) {
            sb.append("owner=").append(owner.toString()).append(", devices=[");
        }

        for (TornadoDevice device : deviceStates.keySet()) {
            if (device != owner) {
                sb.append(device.toString()).append(" ");
            }
        }

        sb.append("]");

        return sb.toString();
    }

}
