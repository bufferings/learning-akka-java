package com.example.iot;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Optional;

public class Device extends AbstractBehavior<Device.Command> {

  public interface Command {}

  public record RecordTemperature(long requestId, double value,
                                  ActorRef<TemperatureRecorded> replyTo) implements Command {}

  public record TemperatureRecorded(long requestId) {}

  public record ReadTemperature(long requestId, ActorRef<RespondTemperature> replyTo) implements Command {}

  public record RespondTemperature(long requestId, String deviceId, Optional<Double> value) {}

  enum Passivate implements Command {
    INSTANCE
  }

  public static Behavior<Command> create(String groupId, String deviceId) {
    return Behaviors.setup(context -> new Device(context, groupId, deviceId));
  }

  private final String groupId;
  private final String deviceId;

  @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "FieldMayBeFinal"})
  private Optional<Double> lastTemperatureReading = Optional.empty();

  private Device(ActorContext<Command> context, String groupId, String deviceId) {
    super(context);
    this.groupId = groupId;
    this.deviceId = deviceId;

    context.getLog().info("Device actor {}-{} started", groupId, deviceId);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(RecordTemperature.class, this::onRecordTemperature)
        .onMessage(ReadTemperature.class, this::onReadTemperature)
        .onMessage(Passivate.class, m -> Behaviors.stopped())
        .onSignal(PostStop.class, signal -> onPostStop())
        .build();
  }

  private Behavior<Command> onRecordTemperature(RecordTemperature r) {
    getContext().getLog().info("Recorded temperature reading {} with {}", r.value, r.requestId);
    lastTemperatureReading = Optional.of(r.value);
    r.replyTo.tell(new TemperatureRecorded(r.requestId));
    return this;
  }

  private Behavior<Command> onReadTemperature(ReadTemperature r) {
    r.replyTo.tell(new RespondTemperature(r.requestId, deviceId, lastTemperatureReading));
    return this;
  }

  private Device onPostStop() {
    getContext().getLog().info("Device actor {}-{} stopped", groupId, deviceId);
    return this;
  }

}