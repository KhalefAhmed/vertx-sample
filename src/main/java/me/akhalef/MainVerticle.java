package me.akhalef;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.get("/api/v1/hello")
                .handler(ctx -> ctx.request().response()
                        .putHeader("content-type", "text/plain")
                        .end("Hello, World!"));

        router.get("/api/v1/hello/:name")
                .handler(ctx -> ctx.request().response().putHeader("content-type", "text/plain")
                        .end("Hello, " + ctx.request().getParam("name") + "!"));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080);
    }
}
