package cp2023.solution;

import cp2023.base.ComponentId;
import cp2023.base.DeviceId;

import java.util.ArrayList;

public class Device {

    private final DeviceId deviceId;
    private Integer currentSlots;
    // Components which transfers are allowed but their place has not been reserved.
    private final ArrayList<ComponentId> freeTransferredComponents;
    // Components which transfers are not allowed.
    private final ArrayList<ComponentId> waitingComponents;


    public Device(DeviceId deviceId, Integer totalSlots) {
        this.deviceId = deviceId;
        this.currentSlots = totalSlots;
        this.freeTransferredComponents = new ArrayList<>();
        this.waitingComponents = new ArrayList<>();
    }

    public Integer getCurrentSlots() {
        return currentSlots;
    }

    public void setCurrentSlots(Integer newValue) {
        currentSlots = newValue;
    }

    public DeviceId getDeviceId() {
        return deviceId;
    }

    @Override
    public boolean equals(Object obj) {
        if (! (obj instanceof Device)) {
            return false;
        }
        return this.deviceId.equals(((Device)obj).getDeviceId());
    }

    public ArrayList<ComponentId> getWaitingComponents() {
        return waitingComponents;
    }

    public void addToWaitingComponents(ComponentId componentId) {
        waitingComponents.add(componentId);
    }

    public void removeFromWaitingComponents(ComponentId componentId) {
        waitingComponents.remove(componentId);
    }

    public ComponentId removeFirstFromWaitingComponents() {
        if (!waitingComponents.isEmpty()) {
            return waitingComponents.remove(0);
        }
        else {
            return null;
        }
    }

    public void addToFreeTransferredComponents(ComponentId componentId) {
        freeTransferredComponents.add(componentId);
    }

    public boolean freeTransferredContains(ComponentId componentId) {
        return freeTransferredComponents.contains(componentId);
    }

    public void removeFromFreeTransferredComponents(ComponentId componentId) {
        freeTransferredComponents.remove(componentId);
    }

    public ComponentId removeFirstFromFreeTransferredComponents() {
        if (!freeTransferredComponents.isEmpty()) {
            return freeTransferredComponents.remove(0);
        }
        else {
            return null;
        }
    }
}
