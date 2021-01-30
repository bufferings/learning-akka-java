package com.example.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DeviceManager extends AbstractBehavior<DeviceManager.Command> {

  public interface Command {}

  public record RequestTrackDevice(String groupId, String deviceId, ActorRef<DeviceRegistered> replyTo)
      implements DeviceManager.Command, DeviceGroup.Command {}

  public record DeviceRegistered(ActorRef<Device.Command> device) {}

  public record RequestDeviceList(long requestId, String groupId, ActorRef<ReplyDeviceList> replyTo)
      implements DeviceManager.Command, DeviceGroup.Command {}

  public record ReplyDeviceList(long requestId, Set<String> ids) {}

  public record RequestDeviceGroupList(long requestId, ActorRef<ReplyDeviceGroupList> replyTo)
      implements DeviceManager.Command {}

  public record ReplyDeviceGroupList(long requestId, Set<String> ids) {}

  private record DeviceGroupTerminated(String groupId) implements DeviceManager.Command {}

  public record RequestAllTemperatures(long requestId, String groupId, ActorRef<RespondAllTemperatures> replyTo)
      implements DeviceGroupQuery.Command, DeviceGroup.Command, Command {}

  public record RespondAllTemperatures(long requestId, Map<String, TemperatureReading> temperatures) {}

  public interface TemperatureReading {}

  public record Temperature(double value) implements TemperatureReading {
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Temperature that = (Temperature) o;
      return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
      long temp = Double.doubleToLongBits(value);
      return (int) (temp ^ (temp >>> 32));
    }
  }

  public enum TemperatureNotAvailable implements TemperatureReading {INSTANCE}

  public enum DeviceNotAvailable implements TemperatureReading {INSTANCE}

  public enum DeviceTimedOut implements TemperatureReading {INSTANCE}
  
  public static Behavior<Command> create() {
    return Behaviors.setup(DeviceManager::new);
  }

  private final Map<String, ActorRef<DeviceGroup.Command>> groupIdToActor = new HashMap<>();

  private DeviceManager(ActorContext<Command> context) {
    super(context);
    context.getLog().info("DeviceManager started");
  }

  private DeviceManager onTrackDevice(RequestTrackDevice trackMsg) {
    String groupId = trackMsg.groupId();
    ActorRef<DeviceGroup.Command> ref = groupIdToActor.get(groupId);
    if (ref != null) {
      ref.tell(trackMsg);
    } else {
      getContext().getLog().info("Creating device group actor for {}", groupId);
      ActorRef<DeviceGroup.Command> groupActor =
          getContext().spawn(DeviceGroup.create(groupId), "group-" + groupId);
      getContext().watchWith(groupActor, new DeviceGroupTerminated(groupId));
      groupActor.tell(trackMsg);
      groupIdToActor.put(groupId, groupActor);
    }
    return this;
  }

  private DeviceManager onRequestDeviceList(RequestDeviceList request) {
    ActorRef<DeviceGroup.Command> ref = groupIdToActor.get(request.groupId());
    if (ref != null) {
      ref.tell(request);
    } else {
      request.replyTo().tell(new ReplyDeviceList(request.requestId(), Set.of()));
    }
    return this;
  }

  private DeviceManager onDeviceGroupList(RequestDeviceGroupList r) {
    r.replyTo().tell(new ReplyDeviceGroupList(r.requestId(), groupIdToActor.keySet()));
    return this;
  }

  private DeviceManager onTerminated(DeviceGroupTerminated t) {
    getContext().getLog().info("Device group actor for {} has been terminated", t.groupId());
    groupIdToActor.remove(t.groupId());
    return this;
  }

  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(RequestTrackDevice.class, this::onTrackDevice)
        .onMessage(RequestDeviceList.class, this::onRequestDeviceList)
        .onMessage(RequestDeviceGroupList.class, this::onDeviceGroupList)
        .onMessage(DeviceGroupTerminated.class, this::onTerminated)
        .onSignal(PostStop.class, signal -> onPostStop())
        .build();
  }

  private DeviceManager onPostStop() {
    getContext().getLog().info("DeviceManager stopped");
    return this;
  }
}