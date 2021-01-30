package com.example.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.example.iot.DeviceGroupQuery.WrappedRespondTemperature;
import com.example.iot.DeviceManager.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class DeviceGroupQueryTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReturnTemperatureValueForWorkingDevices() {
    TestProbe<RespondAllTemperatures> requester = testKit.createTestProbe(RespondAllTemperatures.class);
    TestProbe<Device.Command> device1 = testKit.createTestProbe(Device.Command.class);
    TestProbe<Device.Command> device2 = testKit.createTestProbe(Device.Command.class);

    Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();
    deviceIdToActor.put("device1", device1.getRef());
    deviceIdToActor.put("device2", device2.getRef());

    ActorRef<DeviceGroupQuery.Command> queryActor = testKit.spawn(DeviceGroupQuery.create(
        deviceIdToActor, 1L, requester.getRef(), Duration.ofSeconds(3)));

    assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature.class).requestId());
    assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature.class).requestId());

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device1", Optional.of(1.0))));

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device2", Optional.of(2.0))));

    RespondAllTemperatures response = requester.receiveMessage();
    assertEquals(1L, response.requestId());

    Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new Temperature(1.0));
    expectedTemperatures.put("device2", new Temperature(2.0));

    assertEquals(expectedTemperatures, response.temperatures());
  }

  @Test
  public void testReturnTemperatureNotAvailableForDevicesWithNoReadings() {
    TestProbe<RespondAllTemperatures> requester = testKit.createTestProbe(RespondAllTemperatures.class);
    TestProbe<Device.Command> device1 = testKit.createTestProbe(Device.Command.class);
    TestProbe<Device.Command> device2 = testKit.createTestProbe(Device.Command.class);

    Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();
    deviceIdToActor.put("device1", device1.getRef());
    deviceIdToActor.put("device2", device2.getRef());

    ActorRef<DeviceGroupQuery.Command> queryActor = testKit.spawn(DeviceGroupQuery.create(
        deviceIdToActor, 1L, requester.getRef(), Duration.ofSeconds(3)));

    assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature.class).requestId());
    assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature.class).requestId());

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device1", Optional.empty())));

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device2", Optional.of(2.0))));

    RespondAllTemperatures response = requester.receiveMessage();
    assertEquals(1L, response.requestId());

    Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", TemperatureNotAvailable.INSTANCE);
    expectedTemperatures.put("device2", new Temperature(2.0));

    assertEquals(expectedTemperatures, response.temperatures());
  }

  @Test
  public void testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
    TestProbe<RespondAllTemperatures> requester = testKit.createTestProbe(RespondAllTemperatures.class);
    TestProbe<Device.Command> device1 = testKit.createTestProbe(Device.Command.class);
    TestProbe<Device.Command> device2 = testKit.createTestProbe(Device.Command.class);

    Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();
    deviceIdToActor.put("device1", device1.getRef());
    deviceIdToActor.put("device2", device2.getRef());

    ActorRef<DeviceGroupQuery.Command> queryActor = testKit.spawn(DeviceGroupQuery.create(
        deviceIdToActor, 1L, requester.getRef(), Duration.ofSeconds(3)));

    assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature.class).requestId());
    assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature.class).requestId());

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device1", Optional.of(1.0))));
    device2.stop();

    RespondAllTemperatures response = requester.receiveMessage();
    assertEquals(1L, response.requestId());

    Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new Temperature(1.0));
    expectedTemperatures.put("device2", DeviceNotAvailable.INSTANCE);

    assertEquals(expectedTemperatures, response.temperatures());
  }

  @Test
  public void testReturnTemperatureReadingEvenIfDeviceStopsAfterAnswering() {
    TestProbe<RespondAllTemperatures> requester = testKit.createTestProbe(RespondAllTemperatures.class);
    TestProbe<Device.Command> device1 = testKit.createTestProbe(Device.Command.class);
    TestProbe<Device.Command> device2 = testKit.createTestProbe(Device.Command.class);

    Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();
    deviceIdToActor.put("device1", device1.getRef());
    deviceIdToActor.put("device2", device2.getRef());

    ActorRef<DeviceGroupQuery.Command> queryActor = testKit.spawn(DeviceGroupQuery.create(
        deviceIdToActor, 1L, requester.getRef(), Duration.ofSeconds(3)));

    assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature.class).requestId());
    assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature.class).requestId());

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device1", Optional.of(1.0))));
    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device2", Optional.of(2.0))));
    device2.stop();

    RespondAllTemperatures response = requester.receiveMessage();
    assertEquals(1L, response.requestId());

    Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new Temperature(1.0));
    expectedTemperatures.put("device2", new Temperature(2.0));

    assertEquals(expectedTemperatures, response.temperatures());
  }

  @Test
  public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
    TestProbe<RespondAllTemperatures> requester = testKit.createTestProbe(RespondAllTemperatures.class);
    TestProbe<Device.Command> device1 = testKit.createTestProbe(Device.Command.class);
    TestProbe<Device.Command> device2 = testKit.createTestProbe(Device.Command.class);

    Map<String, ActorRef<Device.Command>> deviceIdToActor = new HashMap<>();
    deviceIdToActor.put("device1", device1.getRef());
    deviceIdToActor.put("device2", device2.getRef());

    ActorRef<DeviceGroupQuery.Command> queryActor = testKit.spawn(DeviceGroupQuery.create(
        deviceIdToActor, 1L, requester.getRef(), Duration.ofMillis(200)));

    assertEquals(0L, device1.expectMessageClass(Device.ReadTemperature.class).requestId());
    assertEquals(0L, device2.expectMessageClass(Device.ReadTemperature.class).requestId());

    queryActor.tell(new WrappedRespondTemperature(
        new Device.RespondTemperature(0L, "device1", Optional.of(1.0))));
    // no reply from device2

    RespondAllTemperatures response = requester.receiveMessage();
    assertEquals(1L, response.requestId());

    Map<String, TemperatureReading> expectedTemperatures = new HashMap<>();
    expectedTemperatures.put("device1", new Temperature(1.0));
    expectedTemperatures.put("device2", DeviceTimedOut.INSTANCE);

    assertEquals(expectedTemperatures, response.temperatures());
  }
}