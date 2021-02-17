package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class ZeGroupDispatch {

    private long groupCountX;
    private long groupCountY;
    private long groupCountZ;

    public ZeGroupDispatch() {

    }

    public void setGroupCountX(long groupCountX) {
        this.groupCountX = groupCountX;
    }

    public void setGroupCountY(long groupCountY) {
        this.groupCountY = groupCountY;
    }

    public void setGroupCountZ(long groupCountZ) {
        this.groupCountZ = groupCountZ;
    }

    public long getGroupCountX() {
        return groupCountX;
    }

    public long getGroupCountY() {
        return groupCountY;
    }

    public long getGroupCountZ() {
        return groupCountZ;
    }
}
