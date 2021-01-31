package com.example.chat.fn;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.Behaviors;

import java.io.IOException;

public class Main {
  public static Behavior<Void> create() {
    //noinspection DuplicatedCode
    return Behaviors.setup(
        context -> {
          ActorRef<ChatRoom.RoomCommand> chatRoom = context.spawn(ChatRoom.create(), "chatRoom");
          ActorRef<ChatRoom.SessionEvent> gabbler = context.spawn(Gabbler.create(), "gabbler");
          ActorRef<ChatRoom.SessionEvent> gabbler2 = context.spawn(Gabbler.create(), "gabbler2");
          context.watch(gabbler);
          context.watch(gabbler2);
          chatRoom.tell(new ChatRoom.GetSession("ol’ Gabbler", gabbler));
          chatRoom.tell(new ChatRoom.GetSession("ol’ Gabbler2", gabbler2));

          return Behaviors.receive(Void.class)
              .onSignal(Terminated.class, sig -> Behaviors.stopped())
              .build();
        });
  }

  public static void main(String[] args) {
    ActorSystem<Void> chatRoomDemo = ActorSystem.create(Main.create(), "ChatRoomDemo");
    try {
      System.out.println(">>> Press ENTER to exit <<<");
      //noinspection ResultOfMethodCallIgnored
      System.in.read();
    } catch (IOException ignored) {
    } finally {
      chatRoomDemo.terminate();
    }
  }
}