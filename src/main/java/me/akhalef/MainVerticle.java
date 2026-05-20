package me.akhalef;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        DeploymentOptions options = new DeploymentOptions()
                .setInstances(8)
                .setThreadingModel(ThreadingModel.WORKER)
                .setWorkerPoolName("hello-worker-pool")
                .setWorkerPoolSize(16);
        
        vertx.deployVerticle("me.akhalef.HelloVerticle", options);

        Router router = Router.router(vertx);

        router.get("/api/v1/hello")
                .handler(this::helloVertx);

        router.get("/api/v1/hello/:name")
                .handler(this::helloName);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080);
    }

    void helloVertx(RoutingContext ctx) {
        vertx.eventBus().request("hello.vertx.address", "")
                .onSuccess(reply -> ctx.response().end(reply.body().toString()))
                .onFailure(err -> ctx.response()
                        .setStatusCode(500)
                        .end(err.getMessage()));
    }

    void helloName(RoutingContext ctx) {
        String name = ctx.request().getParam("name");
        vertx.eventBus().request("hello.vertx.address", name)
                .onSuccess(reply -> ctx.response().end(reply.body().toString()))
                .onFailure(err -> ctx.response()
                        .setStatusCode(500)
                        .end(err.getMessage()));
    }
}
