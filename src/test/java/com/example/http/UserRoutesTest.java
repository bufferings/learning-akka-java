package com.example.http;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import org.junit.*;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UserRoutesTest extends JUnitRouteTest {

  @ClassRule
  public static TestKitJunitResource testkit = new TestKitJunitResource();

  // shared registry for all tests
  private static ActorRef<UserRegistry.Command> userRegistry;

  private TestRoute appRoute;

  @BeforeClass
  public static void beforeClass() {
    userRegistry = testkit.spawn(UserRegistry.create());
  }

  @Before
  public void before() {
    UserRoutes userRoutes = new UserRoutes(testkit.system(), userRegistry);
    appRoute = testRoute(userRoutes.userRoutes());
  }

  @AfterClass
  public static void afterClass() {
    testkit.stop(userRegistry);
  }

  @Test
  public void test1NoUsers() {
    appRoute.run(HttpRequest.GET("/users"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType("application/json")
        .assertEntity("{\"users\":[]}");
  }

  @Test
  public void test2HandlePOST() {
    appRoute.run(HttpRequest.POST("/users")
        .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
            "{\"name\": \"Kapi\", \"age\": 42, \"countryOfResidence\": \"jp\"}"))
        .assertStatusCode(StatusCodes.CREATED)
        .assertMediaType("application/json")
        .assertEntity("{\"description\":\"User Kapi created.\"}");
  }

  @Test
  public void test3Remove() {
    appRoute.run(HttpRequest.DELETE("/users/Kapi"))
        .assertStatusCode(StatusCodes.OK)
        .assertMediaType("application/json")
        .assertEntity("{\"description\":\"User Kapi deleted.\"}");

  }
}
