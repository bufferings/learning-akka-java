package com.example.chat.fn;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
public class ChatRoom {

  interface RoomCommand {}

  public record GetSession(String screenName, ActorRef<SessionEvent> replyTo) implements RoomCommand {}

  interface SessionEvent {}

  public record SessionGranted(ActorRef<PostMessage> handle) implements SessionEvent {}

  public record SessionDenied(String reason) implements SessionEvent {}

  public record MessagePosted(String screenName, String message) implements SessionEvent {}

  interface SessionCommand {}

  public record PostMessage(String message) implements SessionCommand {}

  private record NotifyClient(MessagePosted message) implements SessionCommand {}

  private record PublishSessionMessage(String screenName, String message) implements RoomCommand {}

  public static Behavior<RoomCommand> create() {
    return Behaviors.setup(
        ctx -> new ChatRoom(ctx).chatRoom(new ArrayList<>()));
  }

  private final ActorContext<RoomCommand> context;

  private ChatRoom(ActorContext<RoomCommand> context) {
    this.context = context;
  }

  private Behavior<RoomCommand> chatRoom(List<ActorRef<SessionCommand>> sessions) {
    return Behaviors.receive(RoomCommand.class)
        .onMessage(GetSession.class, getSession -> onGetSession(sessions, getSession))
        .onMessage(PublishSessionMessage.class, pub -> onPublishSessionMessage(sessions, pub))
        .build();
  }

  private Behavior<RoomCommand> onGetSession(List<ActorRef<SessionCommand>> sessions, GetSession getSession)
      throws UnsupportedEncodingException {
    ActorRef<SessionEvent> client = getSession.replyTo();
    ActorRef<SessionCommand> ses = context.spawn(
        Session.create(context.getSelf(), getSession.screenName(), client),
        URLEncoder.encode(getSession.screenName(), StandardCharsets.UTF_8.name())
    );
    // narrow to only expose PostMessage
    client.tell(new SessionGranted(ses.narrow()));
    List<ActorRef<SessionCommand>> newSessions = new ArrayList<>(sessions);
    newSessions.add(ses);
    return chatRoom(newSessions);
  }

  private Behavior<RoomCommand> onPublishSessionMessage(
      List<ActorRef<SessionCommand>> sessions, PublishSessionMessage pub) {
    NotifyClient notification = new NotifyClient((new MessagePosted(pub.screenName(), pub.message())));
    sessions.forEach(s -> s.tell(notification));
    return Behaviors.same();
  }

  static class Session {
    static Behavior<ChatRoom.SessionCommand> create(
        ActorRef<RoomCommand> room, String screenName, ActorRef<SessionEvent> client) {
      return Behaviors.receive(ChatRoom.SessionCommand.class)
          .onMessage(PostMessage.class, post -> onPostMessage(room, screenName, post))
          .onMessage(NotifyClient.class, notification -> onNotifyClient(client, notification))
          .build();
    }

    private static Behavior<SessionCommand> onPostMessage(
        ActorRef<RoomCommand> room, String screenName, PostMessage post) {
      // from client, publish to others via the room
      room.tell(new PublishSessionMessage(screenName, post.message()));
      return Behaviors.same();
    }

    private static Behavior<SessionCommand> onNotifyClient(
        ActorRef<SessionEvent> client, NotifyClient notification) {
      // published from the room
      client.tell(notification.message());
      return Behaviors.same();
    }
  }
}
