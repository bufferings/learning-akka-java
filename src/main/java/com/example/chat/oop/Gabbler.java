package com.example.chat.oop;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.example.chat.oop.Session.MessagePosted;
import com.example.chat.oop.Session.PostMessage;
import com.example.chat.oop.Session.SessionDenied;
import com.example.chat.oop.Session.SessionGranted;

public class Gabbler extends AbstractBehavior<Session.Event> {

  public static Behavior<Session.Event> create() {
    return Behaviors.setup(Gabbler::new);
  }

  private Gabbler(ActorContext<Session.Event> context) {
    super(context);
  }

  @Override
  public Receive<Session.Event> createReceive() {
    return newReceiveBuilder()
        .onMessage(SessionDenied.class, this::onSessionDenied)
        .onMessage(SessionGranted.class, this::onSessionGranted)
        .onMessage(MessagePosted.class, this::onMessagePosted)
        .build();
  }

  private Behavior<Session.Event> onSessionDenied(SessionDenied message) {
    getContext().getLog().info("cannot start chat room session: {}", message.reason());
    return Behaviors.stopped();
  }

  private Behavior<Session.Event> onSessionGranted(SessionGranted message) {
    message.handle().tell(new PostMessage("Hello World!"));
    return this;
  }

  private Behavior<Session.Event> onMessagePosted(MessagePosted message) {
    getContext().getLog()
        .info("message has been posted by '{}': {}", message.screenName(), message.message());
    return this;
  }
}