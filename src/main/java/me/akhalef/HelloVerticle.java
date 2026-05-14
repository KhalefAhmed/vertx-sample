package me.akhalef;

import io.vertx.core.AbstractVerticle;

public class HelloVerticle extends AbstractVerticle {

    @Override
    public void start() {
        vertx.eventBus().consumer("hello.vertx.address", msg -> {
            System.out.println("Received message: " + msg.body());
            msg.reply("Hello from Vert.x!");
        });

        vertx.eventBus().consumer("hello.vertx.address", msg -> {
            String name = msg.body().toString();
            msg.reply(String.format("Hello, %s!", name));
        });
    }
}
