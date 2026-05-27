package me.akhalef;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseVerticle extends AbstractVerticle {

    private Map<String, JsonObject> database = new HashMap<>();
    private String verticleId = UUID.randomUUID().toString().substring(0, 8);

    @Override
    public void start() {
        System.out.println("DatabaseVerticle [" + verticleId + "] démarré");

        database.put("user:1", new JsonObject()
                .put("id", "1")
                .put("name", "Alice")
                .put("email", "alice@example.com"));
        database.put("user:2", new JsonObject()
                .put("id", "2")
                .put("name", "Bob")
                .put("email", "bob@example.com"));

        vertx.eventBus().consumer("db.query", msg -> {
            String userId = msg.body().toString();

            vertx.setTimer(200, timerId -> {
                JsonObject user = database.get("user:" + userId);
                if (user != null) {
                    System.out.println("🔍 [DB-" + verticleId + "] Query user:" + userId + " → Found");
                    msg.reply(user);
                } else {
                    System.out.println("🔍 [DB-" + verticleId + "] Query user:" + userId + " → Not found");
                    msg.fail(404, "User not found");
                }
            });
        });

        vertx.eventBus().consumer("db.insert", msg -> {
            JsonObject newUser = (JsonObject) msg.body();

            vertx.setTimer(300, timerId -> {
                String newId = String.valueOf(database.size() + 1);
                newUser.put("id", newId);
                database.put("user:" + newId, newUser);

                System.out.println("➕ [DB-" + verticleId + "] Inserted user:" + newId);
                msg.reply(new JsonObject().put("id", newId).put("status", "success"));
            });
        });

        vertx.eventBus().consumer("db.update", msg -> {
            JsonObject updateData = (JsonObject) msg.body();
            String userId = updateData.getString("id");

            vertx.setTimer(250, timerId -> {
                if (database.containsKey("user:" + userId)) {
                    JsonObject existing = database.get("user:" + userId);
                    existing.mergeIn(updateData);
                    System.out.println("[DB-" + verticleId + "] Updated user:" + userId);
                    msg.reply(new JsonObject().put("status", "updated"));
                } else {
                    System.out.println("[DB-" + verticleId + "] Update failed: user:" + userId + " not found");
                    msg.fail(404, "User not found");
                }
            });
        });

        // Handler pour DELETE
        vertx.eventBus().consumer("db.delete", msg -> {
            String userId = msg.body().toString();

            vertx.setTimer(200, timerId -> {
                if (database.remove("user:" + userId) != null) {
                    System.out.println("[DB-" + verticleId + "] Deleted user:" + userId);
                    msg.reply(new JsonObject().put("status", "deleted"));
                } else {
                    System.out.println("[DB-" + verticleId + "] Delete failed: user:" + userId + " not found");
                    msg.fail(404, "User not found");
                }
            });
        });
    }
}

