package me.akhalef;

import io.vertx.core.AbstractVerticle;

import java.util.UUID;

public class HelloVerticle extends AbstractVerticle {

    String verticleId = UUID.randomUUID().toString();
    @Override
    public void start() {
        vertx.eventBus().consumer("hello.vertx.address", msg -> {
            Object body = msg.body();
            String thread = Thread.currentThread().getName();
            System.out.println("[" + thread + "] Received message on hello.vertx.address: " + body);
            if (body == null || body.toString().isEmpty()) {
                msg.reply("Hello from Vert.x!");
            } else {
                String name = body.toString();
                msg.reply(String.format("Hello %s, from %s!", name, verticleId));
            }
        });
    }
}
