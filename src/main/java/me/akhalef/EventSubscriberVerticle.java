package me.akhalef;

import io.vertx.core.AbstractVerticle;

import java.util.UUID;

public class EventSubscriberVerticle extends AbstractVerticle {

    String subscriberId = UUID.randomUUID().toString().substring(0, 8);

    @Override
    public void start() {
        System.out.println("EventSubscriberVerticle [" + subscriberId + "] démarré");

        vertx.eventBus().consumer("system.events", msg -> {
            String event = msg.body().toString();
            System.out.println("[SUBSCRIBER-" + subscriberId + "] Reçu: " + event);
            // Pas de reply() ici car publish/subscribe ne retourne pas
        });
    }
}

