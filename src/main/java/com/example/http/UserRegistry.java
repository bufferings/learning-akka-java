package com.example.http;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRegistry extends AbstractBehavior<UserRegistry.Command> {

  interface Command {}

  public record GetUsers(ActorRef<Users> replyTo) implements Command {}

  public record CreateUser(User user, ActorRef<ActionPerformed> replyTo) implements Command {}

  public record GetUserResponse(Optional<User> maybeUser) {}

  public record GetUser(String name, ActorRef<GetUserResponse> replyTo) implements Command {}

  public record DeleteUser(String name, ActorRef<ActionPerformed> replyTo) implements Command {}

  public record ActionPerformed(String description) implements Command {}

  public record User(String name, int age, String countryOfResidence) {}

  public record Users(List<User> users) {}

  private final List<User> users = new ArrayList<>();

  private UserRegistry(ActorContext<Command> context) {
    super(context);
  }

  public static Behavior<Command> create() {
    return Behaviors.setup(UserRegistry::new);
  }

  @Override
  public Receive<Command> createReceive() {
    return newReceiveBuilder()
        .onMessage(GetUsers.class, this::onGetUsers)
        .onMessage(CreateUser.class, this::onCreateUser)
        .onMessage(GetUser.class, this::onGetUser)
        .onMessage(DeleteUser.class, this::onDeleteUser)
        .build();
  }

  private Behavior<Command> onGetUsers(GetUsers command) {
    // We must be careful not to send out users since it is mutable
    // so for this response we need to make a defensive copy
    command.replyTo().tell(new Users(List.copyOf(users)));
    return this;
  }

  private Behavior<Command> onCreateUser(CreateUser command) {
    users.add(command.user());
    command.replyTo().tell(new ActionPerformed(String.format("User %s created.", command.user().name())));
    return this;
  }

  private Behavior<Command> onGetUser(GetUser command) {
    Optional<User> maybeUser = users.stream()
        .filter(user -> user.name().equals(command.name()))
        .findFirst();
    command.replyTo().tell(new GetUserResponse(maybeUser));
    return this;
  }

  private Behavior<Command> onDeleteUser(DeleteUser command) {
    users.removeIf(user -> user.name().equals(command.name()));
    command.replyTo().tell(new ActionPerformed(String.format("User %s deleted.", command.name())));
    return this;
  }

}
