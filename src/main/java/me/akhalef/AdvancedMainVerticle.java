package me.akhalef;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * MAIN VERTICLE AVANCÉ
 * Déploie tous les autres verticles et fournit des endpoints pour les tester
 */
public class AdvancedMainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.deployVerticle("me.akhalef.DatabaseVerticle",
                new DeploymentOptions().setInstances(2))
            .compose(id1 -> {
                System.out.println("✅ DatabaseVerticle déployé");
                return vertx.deployVerticle("me.akhalef.EventPublisherVerticle");
            })
            .compose(id2 -> {
                return vertx.deployVerticle("me.akhalef.EventSubscriberVerticle",
                        new DeploymentOptions().setInstances(3));
            })
            .onSuccess(id3 -> {
                setupRouter();
                startPromise.complete();
            })
            .onFailure(startPromise::fail);
    }

    private void setupRouter() {
        Router router = Router.router(vertx);

        // Route: GET /api/users/:id
        router.get("/api/users/:id").handler(this::getUser);

        // Route: POST /api/users
        router.post("/api/users").handler(this::createUser);

        // Route: PUT /api/users/:id
        router.put("/api/users/:id").handler(this::updateUser);

        // Route: DELETE /api/users/:id
        router.delete("/api/users/:id").handler(this::deleteUser);

        int httpPort = Integer.parseInt(System.getProperty("http.port", "9090"));

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(httpPort)
            .onSuccess(server -> System.out.println("🚀 HTTP Server listening on port " + httpPort))
            .onFailure(err -> System.out.println("❌ HTTP Server failed: " + err.getMessage()));
    }

    /**
     * GET /api/users/1
     * Appel la DB via event bus pour récupérer un utilisateur
     */
    private void getUser(RoutingContext ctx) {
        String userId = ctx.request().getParam("id");

        vertx.eventBus().request("db.query", userId)
            .onSuccess(reply -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body().toString());
            })
            .onFailure(err -> {
                ctx.response()
                    .setStatusCode(500)
                    .end("Error: " + err.getMessage());
            });
    }

    /**
     * POST /api/users
     * Crée un nouvel utilisateur dans la DB
     */
    private void createUser(RoutingContext ctx) {
        ctx.request().bodyHandler(body -> {
            JsonObject newUser = new JsonObject(body.toString());

            vertx.eventBus().request("db.insert", newUser)
                .onSuccess(reply -> {
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(reply.body().toString());
                })
                .onFailure(err -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end("Insert failed: " + err.getMessage());
                });
        });
    }

    /**
     * PUT /api/users/1
     * Met à jour un utilisateur
     */
    private void updateUser(RoutingContext ctx) {
        String userId = ctx.request().getParam("id");

        ctx.request().bodyHandler(body -> {
            JsonObject updateData = new JsonObject(body.toString());
            updateData.put("id", userId);

            vertx.eventBus().request("db.update", updateData)
                .onSuccess(reply -> {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(reply.body().toString());
                })
                .onFailure(err -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end("Update failed: " + err.getMessage());
                });
        });
    }

    /**
     * DELETE /api/users/1
     * Supprime un utilisateur
     */
    private void deleteUser(RoutingContext ctx) {
        String userId = ctx.request().getParam("id");

        vertx.eventBus().request("db.delete", userId)
            .onSuccess(reply -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body().toString());
            })
            .onFailure(err -> {
                ctx.response()
                    .setStatusCode(500)
                    .end("Delete failed: " + err.getMessage());
            });
    }
}

