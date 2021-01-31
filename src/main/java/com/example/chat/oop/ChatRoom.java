package com.example.chat.oop;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.example.chat.oop.Session.MessagePosted;
import com.example.chat.oop.Session.SessionGranted;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ChatRoom extends AbstractBehavior<ChatRoom.Command> {

  public interface Command {}

  public record GetSession(String screenName, ActorRef<Session.Event> replyTo) implements Command {}

  public record PublishSessionMessage(String screenName, String message) implements Command {}

  public static Behavior<Command> create() {
    return Behaviors.setup(ChatRoom::new);
  }

  private final List<ActorRef<Session.Command>> sessions = new ArrayList<>();

  private ChatRoom(ActorContext<Command> context) {
    super(context);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(GetSession.class, this::onGetSession)
        .onMessage(PublishSessionMessage.class, this::onPublishSessionMessage)
        .build();
  }

  private Behavior<Command> onGetSession(GetSession getSession) throws UnsupportedEncodingException {
    ActorRef<Session.Event> client = getSession.replyTo();
    ActorRef<Session.Command> ses = getContext().spawn(
        Session.create(getContext().getSelf(), getSession.screenName(), client),
        URLEncoder.encode(getSession.screenName(), StandardCharsets.UTF_8.name())
    );
    // narrow to only expose PostMessage
    client.tell(new SessionGranted(ses.narrow()));
    sessions.add(ses);
    return this;
  }

  private Behavior<Command> onPublishSessionMessage(PublishSessionMessage pub) {
    var notification = new Session.NotifyClient((new MessagePosted(pub.screenName(), pub.message())));
    sessions.forEach(s -> s.tell(notification));
    return this;
  }

}