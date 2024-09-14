# Concurrent-System-Storage

## Overview

`Concurrent-System-Storage` is a project designed to implement a concurrent data storage system in Java. This project is part of a concurrent programming assignment and involves creating a system that manages the concurrent transfer of data components between storage devices while adhering to specific safety and liveliness constraints.

## Specification

### System Model

- **Components**: Data are grouped into components and stored on devices. Each component and device has a unique, immutable identifier (`ComponentId` and `DeviceId`).
- **Devices**: Devices have a maximum capacity indicating how many components they can store at any given time.
- **Storage System**: Implements the `StorageSystem` interface which manages the transfer of components between devices.

### Interfaces

- **StorageSystem**: Manages component transfers.
  ```java
  public interface StorageSystem {
      void execute(ComponentTransfer transfer) throws TransferException;
  }
  ```

- **ComponentTransfer**: Represents a component transfer operation.
  ```java
  public interface ComponentTransfer {
      ComponentId getComponentId();
      DeviceId getSourceDeviceId();
      DeviceId getDestinationDeviceId();
      void prepare();
      void perform();
  }
  ```

### Transfer Types

1. **Add New Component**: When `getSourceDeviceId` is `null` and `getDestinationDeviceId` is not `null`.
2. **Move Component**: When both `getSourceDeviceId` and `getDestinationDeviceId` are not `null`.
3. **Remove Component**: When `getSourceDeviceId` is not `null` and `getDestinationDeviceId` is `null`.

### Safety and Liveliness

- **Safety**: Ensures that the system adheres to constraints such as no component can be transferred simultaneously, proper resource allocation, and valid component operations.
- **Liveliness**: Transfers should begin as soon as they are allowed, prioritizing older requests where possible.

### Error Handling

- Handles various exceptions including `IllegalTransferType`, `DeviceDoesNotExist`, `ComponentAlreadyExists`, `ComponentDoesNotExist`, `ComponentDoesNotNeedTransfer`, and `ComponentIsBeingOperatedOn`.


## Project Structure

The project is organized into the following packages:

- **`cp2023/base`**: Contains the core interfaces and classes.
  - `ComponentId.java`
  - `ComponentTransfer.java`
  - `DeviceId.java`
  - `StorageSystem.java`

- **`cp2023/demo`**: Contains the demonstration application.
  - `TransferBurst.java`

- **`cp2023/exceptions`**: Contains exception classes used for error handling.
  - `ComponentAlreadyExists.java`
  - `ComponentDoesNotNeedTransfer.java`
  - `DeviceDoesNotExist.java`
  - `TransferException.java`
  - `ComponentDoesNotExist.java`
  - `ComponentIsBeingOperatedOn.java`
  - `IllegalTransferType.java`

- **`cp2023/solution`**: Contains the implementation of the storage system.
  - `ConcurrentSystemStorage.java`
  - `Device.java`
  - `StorageSystemFactory.java`

## Running the Demo

To run the demonstration application:

```bash
javac cp2023/base/*.java cp2023/exceptions/*.java cp2023/solution/*.java cp2023/demo/*.java
java cp2023.demo.TransferBurst
```
