package com.example.iot;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.example.iot.Device.TemperatureRecorded;
import com.example.iot.DeviceManager.*;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DeviceManagerTest {

  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void testReplyToRegistrationRequests() {
    TestProbe<DeviceRegistered> deviceProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group", "device", deviceProbe.getRef()));
    DeviceRegistered registered1 = deviceProbe.receiveMessage();

    // another deviceId
    managerActor.tell(new RequestTrackDevice("group", "device3", deviceProbe.getRef()));
    DeviceRegistered registered2 = deviceProbe.receiveMessage();
    assertNotEquals(registered1.device(), registered2.device());

    // Check that the device actors are working
    TestProbe<TemperatureRecorded> recordProbe = testKit.createTestProbe(TemperatureRecorded.class);
    registered1.device().tell(new Device.RecordTemperature(0L, 1.0, recordProbe.getRef()));
    assertEquals(0L, recordProbe.receiveMessage().requestId());
    registered2.device().tell(new Device.RecordTemperature(1L, 2.0, recordProbe.getRef()));
    assertEquals(1L, recordProbe.receiveMessage().requestId());
  }

  @Test
  public void testReturnSameActorForSameDeviceId() {
    TestProbe<DeviceRegistered> probe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group", "device", probe.getRef()));
    DeviceRegistered registered1 = probe.receiveMessage();

    // registering same again should be idempotent
    managerActor.tell(new RequestTrackDevice("group", "device", probe.getRef()));
    DeviceRegistered registered2 = probe.receiveMessage();
    assertEquals(registered1.device(), registered2.device());
  }

  @Test
  public void testReturnDifferentActorForDifferentGroupId() {
    TestProbe<DeviceRegistered> probe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group", "device", probe.getRef()));
    DeviceRegistered registered1 = probe.receiveMessage();

    // registering same again should be idempotent
    managerActor.tell(new RequestTrackDevice("group1", "device", probe.getRef()));
    DeviceRegistered registered2 = probe.receiveMessage();
    assertNotEquals(registered1.device(), registered2.device());
  }

  @Test
  public void testListActiveGroupsAndDevices() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group", "device1", registeredProbe.getRef()));
    registeredProbe.receiveMessage();

    managerActor.tell(new RequestTrackDevice("group", "device2", registeredProbe.getRef()));
    registeredProbe.receiveMessage();

    managerActor.tell(new RequestTrackDevice("group2", "device1", registeredProbe.getRef()));
    registeredProbe.receiveMessage();

    managerActor.tell(new RequestTrackDevice("group3", "device1", registeredProbe.getRef()));
    registeredProbe.receiveMessage();

    TestProbe<ReplyDeviceList> deviceListProbe = testKit.createTestProbe(ReplyDeviceList.class);

    managerActor.tell(new RequestDeviceList(0L, "group", deviceListProbe.getRef()));
    ReplyDeviceList deviceListReply = deviceListProbe.receiveMessage();
    assertEquals(0L, deviceListReply.requestId());
    assertEquals(Set.of("device1", "device2"), deviceListReply.ids());

    TestProbe<ReplyDeviceGroupList> groupListProbe = testKit.createTestProbe(ReplyDeviceGroupList.class);

    managerActor.tell(new RequestDeviceGroupList(0L, groupListProbe.getRef()));
    ReplyDeviceGroupList groupListReply = groupListProbe.receiveMessage();
    assertEquals(0L, groupListReply.requestId());
    assertEquals(Set.of("group", "group2", "group3"), groupListReply.ids());
  }

  @Test
  public void testListActiveDevicesAfterOneShutsDown() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group", "device1", registeredProbe.getRef()));
    DeviceRegistered registered1 = registeredProbe.receiveMessage();

    managerActor.tell(new RequestTrackDevice("group", "device2", registeredProbe.getRef()));
    DeviceRegistered registered2 = registeredProbe.receiveMessage();

    ActorRef<Device.Command> toShutDown = registered1.device();

    TestProbe<ReplyDeviceList> deviceListProbe = testKit.createTestProbe(ReplyDeviceList.class);

    managerActor.tell(new RequestDeviceList(0L, "group", deviceListProbe.getRef()));
    ReplyDeviceList reply = deviceListProbe.receiveMessage();
    assertEquals(0L, reply.requestId());
    assertEquals(Set.of("device1", "device2"), reply.ids());

    toShutDown.tell(Device.Passivate.INSTANCE);
    registeredProbe.expectTerminated(toShutDown, registeredProbe.getRemainingOrDefault());

    // using awaitAssert to retry because it might take longer for the groupActor
    // to see the Terminated, that order is undefined
    registeredProbe.awaitAssert(
        () -> {
          managerActor.tell(new RequestDeviceList(1L, "group", deviceListProbe.getRef()));
          ReplyDeviceList r = deviceListProbe.receiveMessage();
          assertEquals(1L, r.requestId());
          assertEquals(Set.of("device2"), r.ids());
          return null;
        });
  }

  @Test
  public void testListActiveGroupsAfterOneShutsDownWithNoDevice() {
    TestProbe<DeviceRegistered> registeredProbe = testKit.createTestProbe(DeviceRegistered.class);
    ActorRef<DeviceManager.Command> managerActor = testKit.spawn(DeviceManager.create());

    managerActor.tell(new RequestTrackDevice("group1", "device", registeredProbe.getRef()));
    DeviceRegistered registered1 = registeredProbe.receiveMessage();

    managerActor.tell(new RequestTrackDevice("group2", "device", registeredProbe.getRef()));
    DeviceRegistered registered2 = registeredProbe.receiveMessage();

    ActorRef<Device.Command> toShutDown = registered1.device();

    TestProbe<ReplyDeviceGroupList> groupListProbe = testKit.createTestProbe(ReplyDeviceGroupList.class);

    managerActor.tell(new RequestDeviceGroupList(0L, groupListProbe.getRef()));
    ReplyDeviceGroupList reply = groupListProbe.receiveMessage();
    assertEquals(0L, reply.requestId());
    assertEquals(Set.of("group1", "group2"), reply.ids());

    toShutDown.tell(Device.Passivate.INSTANCE);
    registeredProbe.expectTerminated(toShutDown, registeredProbe.getRemainingOrDefault());

    // using awaitAssert to retry because it might take longer for the groupActor
    // to see the Terminated, that order is undefined
    registeredProbe.awaitAssert(
        () -> {
          managerActor.tell(new RequestDeviceGroupList(1L, groupListProbe.getRef()));
          ReplyDeviceGroupList r = groupListProbe.receiveMessage();
          assertEquals(1L, r.requestId());
          assertEquals(Set.of("group2"), r.ids());
          return null;
        });
  }
}