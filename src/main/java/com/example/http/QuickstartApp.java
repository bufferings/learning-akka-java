package com.example.http;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

public class QuickstartApp {

  static void startHttpServer(Route route, ActorSystem<?> system) {
    CompletionStage<ServerBinding> futureBinding =
        Http.get(system).newServerAt("localhost", 8080).bind(route);

    futureBinding.whenComplete((binding, exception) -> {
      if (binding != null) {
        InetSocketAddress address = binding.localAddress();
        system.log().info("Server online at http://{}:{}/",
            address.getHostString(),
            address.getPort());
      } else {
        system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
        system.terminate();
      }
    });
  }

  public static void main(String[] args) throws Exception {
    Behavior<NotUsed> rootBehavior = Behaviors.setup(context -> {
      ActorRef<UserRegistry.Command> userRegistryActor =
          context.spawn(UserRegistry.create(), "UserRegistry");

      UserRoutes userRoutes = new UserRoutes(context.getSystem(), userRegistryActor);
      startHttpServer(userRoutes.userRoutes(), context.getSystem());

      return Behaviors.empty();
    });

    // boot up server using the route as defined below
    ActorSystem.create(rootBehavior, "HelloAkkaHttpServer");
  }

}
