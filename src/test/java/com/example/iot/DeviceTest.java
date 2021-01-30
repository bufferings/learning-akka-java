package com.example.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.example.iot.Device.ReadTemperature;
import com.example.iot.Device.RecordTemperature;
import com.example.iot.Device.RespondTemperature;
import com.example.iot.Device.TemperatureRecorded;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class DeviceTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReplyWithLatestTemperatureReading() {
    TestProbe<TemperatureRecorded> recordProbe = testKit.createTestProbe(TemperatureRecorded.class);
    TestProbe<RespondTemperature> readProbe = testKit.createTestProbe(RespondTemperature.class);
    ActorRef<Device.Command> deviceActor = testKit.spawn(Device.create("group", "device"));

    deviceActor.tell(new RecordTemperature(1L, 24.0, recordProbe.getRef()));
    assertEquals(1L, recordProbe.receiveMessage().requestId());

    deviceActor.tell(new ReadTemperature(2L, readProbe.getRef()));
    RespondTemperature response1 = readProbe.receiveMessage();
    assertEquals(2L, response1.requestId());
    assertEquals(Optional.of(24.0), response1.value());

    deviceActor.tell(new RecordTemperature(3L, 55.0, recordProbe.getRef()));
    assertEquals(3L, recordProbe.receiveMessage().requestId());

    deviceActor.tell(new ReadTemperature(4L, readProbe.getRef()));
    RespondTemperature response2 = readProbe.receiveMessage();
    assertEquals(4L, response2.requestId());
    assertEquals(Optional.of(55.0), response2.value());
  }

  @Test
  public void testReplyWithEmptyReadingIfNoTemperatureIsKnown() {
    TestProbe<RespondTemperature> probe = testKit.createTestProbe(RespondTemperature.class);
    ActorRef<Device.Command> deviceActor = testKit.spawn(Device.create("group", "device"));

    deviceActor.tell(new ReadTemperature(42L, probe.getRef()));

    RespondTemperature response = probe.receiveMessage();
    assertEquals(42L, response.requestId());
    assertEquals(Optional.empty(), response.value());
  }
}