package com.example.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.example.iot.DeviceManager.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class DeviceGroup extends AbstractBehavior<DeviceGroup.Command> {

  public interface Command {}

  private record DeviceTerminated(ActorRef<Device.Command> device,
                                  String groupId, String deviceId) implements Command {}

  public static Behavior<Command> create(String groupId) {
    return Behaviors.setup(context -> new DeviceGroup(context, groupId));
  }

  private final String groupId;
  private final Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();

  private DeviceGroup(ActorContext<Command> context, String groupId) {
    super(context);
    this.groupId = groupId;
    context.getLog().info("DeviceGroup {} started", groupId);
  }

  private DeviceGroup onTrackDevice(RequestTrackDevice trackMsg) {
    if (this.groupId.equals(trackMsg.groupId())) {
      ActorRef<Device.Command> deviceActor = deviceIdToActor.get(trackMsg.deviceId());
      if (deviceActor == null) {
        getContext().getLog().info("Creating device actor for {}", trackMsg.deviceId());
        deviceActor = getContext()
            .spawn(Device.create(groupId, trackMsg.deviceId()), "device-" + trackMsg.deviceId());

        getContext()
            .watchWith(deviceActor, new DeviceTerminated(deviceActor, groupId, trackMsg.deviceId()));
        deviceIdToActor.put(trackMsg.deviceId(), deviceActor);
      }
      trackMsg.replyTo().tell(new DeviceRegistered(deviceActor));
    } else {
      getContext()
          .getLog()
          .warn(
              "Ignoring TrackDevice request for {}. This actor is responsible for {}.",
              groupId,
              this.groupId);
    }
    return this;
  }

  private DeviceGroup onDeviceList(RequestDeviceList r) {
    r.replyTo().tell(new ReplyDeviceList(r.requestId(), deviceIdToActor.keySet()));
    return this;
  }

  private Behavior<Command> onTerminated(DeviceTerminated t) {
    getContext().getLog().info("Device actor for {} has been terminated", t.deviceId());
    deviceIdToActor.remove(t.deviceId());
    if (deviceIdToActor.isEmpty()) {
      getContext().getLog().info("DeviceGroup {} has been terminated", groupId);
      return Behaviors.stopped();
    }
    return this;
  }

  private DeviceGroup onAllTemperatures(RequestAllTemperatures r) {
    getContext().spawnAnonymous(DeviceGroupQuery.create(
        Map.copyOf(this.deviceIdToActor), r.requestId(), r.replyTo(), Duration.ofSeconds(3)));
    return this;
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(RequestTrackDevice.class, this::onTrackDevice)
        .onMessage(
            RequestDeviceList.class,
            r -> r.groupId().equals(groupId),
            this::onDeviceList)
        .onMessage(DeviceTerminated.class, this::onTerminated)
        .onMessage(
            RequestAllTemperatures.class,
            r -> r.groupId().equals(groupId),
            this::onAllTemperatures)
        .onSignal(PostStop.class, signal -> onPostStop())
        .build();
  }

  private DeviceGroup onPostStop() {
    getContext().getLog().info("DeviceGroup {} stopped", groupId);
    return this;
  }
}