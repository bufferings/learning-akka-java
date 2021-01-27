package com.example.hierarchy;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

class SupervisingActor extends AbstractBehavior<String> {

  static Behavior<String> create() {
    return Behaviors.setup(SupervisingActor::new);
  }

  private final ActorRef<String> child;

  private SupervisingActor(ActorContext<String> context) {
    super(context);
    child =
        context.spawn(
            Behaviors.supervise(SupervisedActor.create()).onFailure(SupervisorStrategy.restart()),
            "supervised-actor");
  }

  @Override
  public Receive<String> createReceive() {
    return newReceiveBuilder().onMessageEquals("failChild", this::onFailChild).build();
  }

  private Behavior<String> onFailChild() {
    child.tell("fail");
    return this;
  }
}

class SupervisedActor extends AbstractBehavior<String> {

  static Behavior<String> create() {
    return Behaviors.setup(SupervisedActor::new);
  }

  private SupervisedActor(ActorContext<String> context) {
    super(context);
    System.out.println("supervised actor started");
  }

  @Override
  public Receive<String> createReceive() {
    return newReceiveBuilder()
        .onMessageEquals("fail", this::fail)
        .onSignal(PreRestart.class, signal -> preRestart())
        .onSignal(PostStop.class, signal -> postStop())
        .build();
  }

  private Behavior<String> fail() {
    System.out.println("supervised actor fails now");
    throw new RuntimeException("I failed!");
  }

  private Behavior<String> preRestart() {
    System.out.println("second will be restarted");
    return this;
  }

  private Behavior<String> postStop() {
    System.out.println("second stopped");
    return this;
  }
}

public class ActorHierarchyExperiments3 {
  public static void main(String[] args) {
    ActorRef<String> supervisingActor =
        ActorSystem.create(SupervisingActor.create(), "supervising-actor");
    supervisingActor.tell("failChild");
  }
}
