package me.akhalef;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;


public class AdvancedMainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.deployVerticle("me.akhalef.DatabaseVerticle",
                new DeploymentOptions().setInstances(2))
            .compose(id1 -> vertx.deployVerticle("me.akhalef.EventPublisherVerticle"))
            .compose(id2 -> vertx.deployVerticle("me.akhalef.EventSubscriberVerticle",
                    new DeploymentOptions().setInstances(3)))
            .onSuccess(id3 -> {
                setupRouter();
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void setupRouter() {
        Router router = Router.router(vertx);

        router.get("/api/users/:id").handler(this::getUser);

        router.post("/api/users").handler(this::createUser);

        router.put("/api/users/:id").handler(this::updateUser);

        router.delete("/api/users/:id").handler(this::deleteUser);

        int httpPort = Integer.parseInt(System.getProperty("http.port", "9090"));

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(httpPort)
            .onSuccess(server -> System.out.println("HTTP Server listening on port " + httpPort))
            .onFailure(err -> System.out.println("HTTP Server failed: " + err.getMessage()));
    }

    private void getUser(RoutingContext ctx) {
        String userId = ctx.request().getParam("id");

        vertx.eventBus().request("db.query", userId)
            .onSuccess(reply -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(reply.body().toString()))
            .onFailure(err -> ctx.response()
                .setStatusCode(500)
                .end("Error: " + err.getMessage()));
    }

    private void createUser(RoutingContext ctx) {
        ctx.request().bodyHandler(body -> {
            JsonObject newUser = new JsonObject(body.toString());

            vertx.eventBus().request("db.insert", newUser)
                .onSuccess(reply -> ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body().toString()))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .end("Insert failed: " + err.getMessage()));
        });
    }

    private void updateUser(RoutingContext ctx) {
        String userId = ctx.request().getParam("id");

        ctx.request().bodyHandler(body -> {
            JsonObject updateData = new JsonObject(body.toString());
            updateData.put("id", userId);

            vertx.eventBus().request("db.update", updateData)
                .onSuccess(reply -> ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body().toString()))
                .onFailure(err -> ctx.response()
                    .setStatusCode(500)
                    .end("Update failed: " + err.getMessage()));
        });
    }

    private void deleteUser(RoutingContext ctx) {
        String userId = ctx.request().getParam("id");

        vertx.eventBus().request("db.delete", userId)
            .onSuccess(reply -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(reply.body().toString()))
            .onFailure(err -> ctx.response()
                .setStatusCode(500)
                .end("Delete failed: " + err.getMessage()));
    }
}

