package me.akhalef;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class EventPublisherVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        System.out.println("✅ EventPublisherVerticle démarré");

        vertx.setPeriodic(3000, timerId -> {
            String event = "Event-" + System.currentTimeMillis();
            
            vertx.eventBus().publish("system.events", event);
            System.out.println("📢 [PUBLISHER] Sent event: " + event);
        });

        startPromise.complete();
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        System.out.println("❌ EventPublisherVerticle arrêté");
        stopPromise.complete();
    }
}

