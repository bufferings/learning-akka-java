package com.example.chat.oop;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Session extends AbstractBehavior<Session.Command> {

  public interface Command {}

  public record PostMessage(String message) implements Command {}

  public record NotifyClient(MessagePosted message) implements Command {}

  public interface Event {}

  public record SessionGranted(ActorRef<Session.PostMessage> handle) implements Event {}

  public record SessionDenied(String reason) implements Event {}

  public record MessagePosted(String screenName, String message) implements Event {}

  public static Behavior<Command> create(
      ActorRef<ChatRoom.Command> room, String screenName, ActorRef<Event> client) {
    return Behaviors.setup(context -> new Session(context, room, screenName, client));
  }

  private final ActorRef<ChatRoom.Command> room;
  private final String screenName;
  private final ActorRef<Event> client;

  private Session(
      ActorContext<Command> context,
      ActorRef<ChatRoom.Command> room,
      String screenName,
      ActorRef<Event> client) {
    super(context);
    this.room = room;
    this.screenName = screenName;
    this.client = client;
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(PostMessage.class, this::onPostMessage)
        .onMessage(NotifyClient.class, this::onNotifyClient)
        .build();
  }

  private Behavior<Command> onPostMessage(PostMessage post) {
    room.tell(new ChatRoom.PublishSessionMessage(screenName, post.message()));
    return Behaviors.same();
  }

  private Behavior<Command> onNotifyClient(NotifyClient notification) {
    client.tell(notification.message());
    return Behaviors.same();
  }
}
