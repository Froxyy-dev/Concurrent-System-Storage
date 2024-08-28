package cp2023.solution;

import cp2023.base.*;
import cp2023.exceptions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class ConcurrentSystemStorage implements StorageSystem {
    private final Semaphore mutex = new Semaphore(1, true);
    private final Map<ComponentId, Device> componentPlacementMap;
    private final Map<ComponentId, Boolean> isBeingTransferred;
    private final Map<ComponentId, Boolean> isNotPrepared;
    private final Map<DeviceId, Device> devices;
    private final Map<ComponentId, Semaphore> transferNotAllowed; // Wait if transfer not allowed.
    private final Map<ComponentId, Semaphore> waitForPrepare; // Wait for other transfer's prepare.
    private final Map<ComponentId, ComponentId> reservedPlace; // Component which place we reserved.
    private final Map<ComponentId, ComponentId> componentWhichReserved;
    private final Map<ComponentId, Boolean> awakenByCycle;

    public ConcurrentSystemStorage(Map<DeviceId, Integer> deviceTotalSlots,
                                   Map<ComponentId, DeviceId> componentPlacement) {
        if (deviceTotalSlots == null || deviceTotalSlots.isEmpty()) {
            throw new IllegalArgumentException("DeviceTotalSlots map is null.");
        }

        if (componentPlacement == null) {
            throw new IllegalArgumentException("ComponentPlacement map is null.");
        }

        this.componentPlacementMap = new HashMap<>();
        this.devices = new HashMap<>();

        for (DeviceId deviceId : deviceTotalSlots.keySet()) {
            if (deviceId == null) {
                throw new IllegalArgumentException("DeviceId is null.");
            }

            if (deviceTotalSlots.get(deviceId) == null || deviceTotalSlots.get(deviceId) <= 0) {
                throw new IllegalArgumentException("The device has zero capacity.");
            }

            final Device device = new Device(deviceId, deviceTotalSlots.get(deviceId));
            devices.put(deviceId, device);
        }

        for (ComponentId componentId : componentPlacement.keySet()) {
            if (componentId == null) {
                throw new IllegalArgumentException("ComponentId is null.");
            }

            final DeviceId deviceId = componentPlacement.get(componentId);
            if (deviceId == null || !devices.containsKey(deviceId)) {
                throw new IllegalArgumentException("The component is assigned to the device without a specified capacity.");
            }

            final Device device = devices.get(deviceId);
            componentPlacementMap.put(componentId, device);
            final Integer currentSlots = device.getCurrentSlots();

            if (currentSlots <= 0) {
                throw new IllegalArgumentException("The number of components assigned to a device exceeds its capacity.");
            }

            device.setCurrentSlots(currentSlots - 1);
        }

        this.isBeingTransferred = new HashMap<>();
        this.transferNotAllowed = new HashMap<>();
        this.waitForPrepare = new HashMap<>();
        this.reservedPlace = new HashMap<>();
        this.componentWhichReserved = new HashMap<>();
        this.isNotPrepared = new HashMap<>();
        this.awakenByCycle = new HashMap<>();
    }

    boolean findCycle(ComponentId componentId, Device device, Map<Device, Boolean> visited,
                      ArrayList<ComponentId> components, ComponentId start) {
        if (visited.getOrDefault(device, false) && componentId.equals(start)) {
            return true;
        }
        else if (visited.getOrDefault(device, false)) {
            return false;
        }

        visited.put(device, true);
        final ArrayList<ComponentId> waiting = device.getWaitingComponents();

        for (ComponentId waitingComponent : waiting) {
            final Device waitingPlacement = componentPlacementMap.getOrDefault(waitingComponent, null);

            if (waitingPlacement != null) {
                if (findCycle(waitingComponent, waitingPlacement, visited, components, start)) {
                    components.add(componentId);
                    return true;
                }
            }
        }

        return false;
    }

    public void handleErrors(ComponentId componentId, DeviceId sourceDeviceId,
                             DeviceId destinationDeviceId) throws TransferException {
        if (sourceDeviceId == null && destinationDeviceId == null) {
            throw new IllegalTransferType(componentId);
        }

        if (sourceDeviceId != null && !devices.containsKey(sourceDeviceId)) {
            throw new DeviceDoesNotExist(sourceDeviceId);
        }

        if (destinationDeviceId != null && !devices.containsKey(destinationDeviceId)) {
            throw new DeviceDoesNotExist(destinationDeviceId);
        }

        if (isBeingTransferred.containsKey(componentId)) {
            throw new ComponentIsBeingOperatedOn(componentId);
        }
    }

    @Override
    public void execute(ComponentTransfer transfer) throws TransferException {
        try {
            mutex.acquire();

            final DeviceId sourceDeviceId = transfer.getSourceDeviceId();
            final DeviceId destinationDeviceId = transfer.getDestinationDeviceId();
            final ComponentId componentId = transfer.getComponentId();

            handleErrors(componentId, sourceDeviceId, destinationDeviceId);

            final Device sourceDevice = devices.getOrDefault(sourceDeviceId, null);
            final Device destinationDevice = devices.getOrDefault(destinationDeviceId, null);

            isBeingTransferred.put(componentId, true);

            if (sourceDevice == null) {
                executeAdding(transfer, componentId, destinationDevice);
            }
            else {
                if (!componentPlacementMap.containsKey(componentId) ||
                        !componentPlacementMap.get(componentId).equals(sourceDevice)) {
                    throw new ComponentDoesNotExist(componentId, sourceDeviceId);
                }

                if (destinationDevice == null) {
                    executeRemoving(transfer, componentId, sourceDevice);
                }
                else {
                    executeMoving(transfer, componentId, sourceDevice, destinationDevice);
                }
            }

            mutex.release();
        }
        catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public void executeAdding(ComponentTransfer transfer, ComponentId componentId,
                              Device destinationDevice) throws TransferException, InterruptedException {
        if (componentPlacementMap.containsKey(componentId)) {
            throw new ComponentAlreadyExists(componentId, componentPlacementMap.get(componentId).getDeviceId());
        }

        isNotPrepared.put(componentId, true);
        handleTransferNotAllowed(componentId, destinationDevice, null, false);
        executePrepare(transfer, componentId);
        performTransfer(transfer);
        finishTransfer(componentId, destinationDevice);
    }

    public void executeRemoving(ComponentTransfer transfer, ComponentId componentId,
                                Device sourceDevice) throws InterruptedException {
        isNotPrepared.put(componentId, true);
        wakeIfTransferNotAllowed(componentId, sourceDevice);
        prepareTransfer(transfer, componentId);
        wakeWaitingForPrepare(componentId);
        executePerform(transfer, componentId, sourceDevice);
        finishTransfer(componentId, null);
    }

    public void executeMoving(ComponentTransfer transfer, ComponentId componentId,
                              Device sourceDevice, Device destinationDevice) throws TransferException, InterruptedException {
        if (sourceDevice.equals(destinationDevice)) {
            throw new ComponentDoesNotNeedTransfer(componentId, sourceDevice.getDeviceId());
        }

        isNotPrepared.put(componentId, true);
        handleTransferNotAllowed(componentId, destinationDevice, sourceDevice, true);

        // If we were awaken by a component from a cycle, we know which component we need to wake up.
        if (awakenByCycle.containsKey(componentId)) {
            awakenByCycle.put(componentId, true);
            final ComponentId nextOnCycle = componentWhichReserved.get(componentId);

            if (awakenByCycle.containsKey(nextOnCycle) && !awakenByCycle.get(nextOnCycle)) {
                mutex.release();
                transferNotAllowed.get(nextOnCycle).release();
                mutex.acquire();
            }
        }
        else { // Otherwise we check if some other transfer has just become allowed.
            wakeIfTransferNotAllowed(componentId, sourceDevice);
        }

        executePrepare(transfer, componentId);
        executePerform(transfer, componentId, sourceDevice);
        finishTransfer(componentId, destinationDevice);
    }

    public void finishTransfer(ComponentId componentId, Device destinationDevice) {
        isBeingTransferred.remove(componentId);
        componentWhichReserved.remove(componentId);
        transferNotAllowed.remove(componentId);
        reservedPlace.remove(componentId);
        awakenByCycle.remove(componentId);
        waitForPrepare.remove(componentId);

        if (destinationDevice == null) { // We removed this component from system.
            componentPlacementMap.remove(componentId);
        }
        else {
            componentPlacementMap.put(componentId, destinationDevice);
        }
    }

    public void prepareTransfer(ComponentTransfer transfer, ComponentId componentId) throws InterruptedException {
        isNotPrepared.put(componentId, true);

        mutex.release();
        transfer.prepare();
        mutex.acquire();

        isNotPrepared.remove(componentId);
    }

    public void executePrepare(ComponentTransfer transfer, ComponentId componentId) throws InterruptedException {
        prepareTransfer(transfer, componentId);

        // First, we need to inform transfer waiting for us (if any is) that we finished preparing.
        wakeWaitingForPrepare(componentId);

        // If we reserved place after some component, we need to check if it finished preparing.
        if (reservedPlace.containsKey(componentId) && isNotPrepared.containsKey(reservedPlace.get(componentId))) {
            final Semaphore waitForReserved = new Semaphore(0, true);
            waitForPrepare.put(componentId, waitForReserved);

            mutex.release();
            waitForReserved.acquire();
            mutex.acquire();
            waitForPrepare.remove(componentId);
        }
    }

    public void performTransfer(ComponentTransfer transfer) throws InterruptedException {
        mutex.release();
        transfer.perform();
        mutex.acquire();
    }

    public void executePerform(ComponentTransfer transfer, ComponentId componentId, Device sourceDevice) throws InterruptedException {
        performTransfer(transfer);

        // If no one has reserved our place, we increase free slots count on our source device.
        if (sourceDevice.freeTransferredContains(componentId)) {
            sourceDevice.removeFromFreeTransferredComponents(componentId);

            final Integer sourceSlots = sourceDevice.getCurrentSlots();
            sourceDevice.setCurrentSlots(sourceSlots + 1);
        }
    }

    public void wakeWaitingForPrepare(ComponentId componentId) throws InterruptedException {
        if (componentWhichReserved.containsKey(componentId)) {
            final ComponentId reserving = componentWhichReserved.get(componentId);

            if (waitForPrepare.containsKey(reserving)) {
                mutex.release();
                waitForPrepare.get(reserving).release();
                mutex.acquire();
            }
        }
    }
    public void wakeIfTransferNotAllowed(ComponentId componentId, Device sourceDevice) throws InterruptedException {
        final ComponentId reserving = sourceDevice.removeFirstFromWaitingComponents();

        if (reserving != null) {
            reservedPlace.put(reserving, componentId);
            componentWhichReserved.put(componentId, reserving);

            mutex.release();
            transferNotAllowed.get(reserving).release();
            mutex.acquire();
        }
        else {
            sourceDevice.addToFreeTransferredComponents(componentId);
        }
    }

    public void handleTransferNotAllowed(ComponentId componentId, Device destinationDevice,
                                         Device sourceDevice, Boolean moving) throws InterruptedException {
        final Integer destinationSlots = destinationDevice.getCurrentSlots();

        if (destinationSlots > 0) { // First we check if there is free space on a destination device.
            destinationDevice.setCurrentSlots(destinationSlots - 1);
        }
        else {
            // We check if there is a component which place has not yet been reserved.
            final ComponentId toReserve =
                    destinationDevice.removeFirstFromFreeTransferredComponents();

            if (toReserve != null) {
                reservedPlace.put(componentId, toReserve);
                componentWhichReserved.put(toReserve, componentId);
            }
            else {
                final ArrayList<ComponentId> components = new ArrayList<>();
                final Map<Device, Boolean> visited = new HashMap<>();
                destinationDevice.addToWaitingComponents(componentId);

                // If the transfer is of a type moving, we look for a cycle.
                if (moving && findCycle(componentId, sourceDevice, visited, components, componentId)) {
                    handleCycle(components);
                }
                else {
                    // Transfer is currently not allowed, we need to wait.
                    final Semaphore waitForTransfer = new Semaphore(0, true);
                    transferNotAllowed.put(componentId, waitForTransfer);

                    mutex.release();
                    waitForTransfer.acquire();
                    mutex.acquire();
                }
            }
        }
    }

    public void handleCycle(ArrayList<ComponentId> components) {
        final int cycleSize = components.size();

        for (int i = cycleSize - 1; i >= 0; i--) {
            ComponentId current = components.get(i);
            ComponentId previous = components.get((i - 1 + cycleSize) % cycleSize);

            componentWhichReserved.put(current, previous);
            reservedPlace.put(previous, current);
            awakenByCycle.put(current, false);
            isNotPrepared.put(current, true);

            Device currentDevice = componentPlacementMap.get(current);
            currentDevice.removeFromWaitingComponents(previous);
        }
    }
}
