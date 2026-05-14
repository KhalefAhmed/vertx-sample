package me.akhalef;

import io.vertx.core.AbstractVerticle;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() {
        vertx.createHttpServer().requestHandler(request -> {
            request.response().end("Hello Vertx World!");
        }).listen(8080);
    }
}
