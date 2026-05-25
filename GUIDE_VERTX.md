# VERT.X - COMPLETE GUIDE

## Overview

Vert.x is an **event-driven** and **non-blocking** framework for building distributed applications.

---

## 3 KEY CONCEPTS

### 1️⃣ VERTICLES (Actors)
```
Each verticle = independent execution unit

┌────────────────┐
│ MainVerticle   │ (Starts HTTP server + deploys others)
└────────────────┘
        ├─ HelloVerticle (4 instances)
        ├─ DatabaseVerticle (2 instances)
        ├─ EventPublisherVerticle (1 instance)
        └─ EventSubscriberVerticle (3 instances)
```

**Multiple deployment:**
```java
new DeploymentOptions().setInstances(4)
// Launches 4 copies of the same verticle in parallel
```

---

### 2️⃣ EVENT BUS (Communication)

#### A) REQUEST/REPLY (One-to-One)
```
Client (MainVerticle)
    ↓ eventBus.request("hello.vertx.address", "Ahmed")
    ↓ [Infinispan decides WHICH HelloVerticle responds]
Handler (HelloVerticle instance 2) receives ONLY
    ↓ msg.reply("Hello Ahmed, from 2!")
Response returns to client
```

**Example:**
```java
// Client
vertx.eventBus().request("hello.vertx.address", "Ahmed")
    .onSuccess(reply -> console.log(reply.body()))  // "Hello Ahmed, from abc123!"
    .onFailure(err -> console.log(err));

// Server (HelloVerticle)
vertx.eventBus().consumer("hello.vertx.address", msg -> {
    msg.reply("Hello " + msg.body() + "!");  // REPLY = response to sender
});
```

---

#### B) PUBLISH/SUBSCRIBE (One-to-Many)
```
EventPublisherVerticle
    ↓ eventBus.publish("system.events", "Event-123")
    ↓ [Infinispan distributes to ALL subscribers]
EventSubscriberVerticle #1 receives
EventSubscriberVerticle #2 receives
EventSubscriberVerticle #3 receives
```

**Example:**
```java
// Publisher (EventPublisherVerticle)
vertx.eventBus().publish("system.events", "Event-123");
// No reply()!

// Subscribers (EventSubscriberVerticle)
vertx.eventBus().consumer("system.events", msg -> {
    System.out.println("Received: " + msg.body());
    // No msg.reply() here!
});
```

**Key differences:**
| Request/Reply | Publish/Subscribe |
|---|---|
| ONE handler responds | ALL receive |
| Response expected | No response |
| Point-to-point | Broadcast |
| Use case: data | Use case: notifications |

---

### 3️⃣ ASYNCHRONOUS OPERATIONS (Non-blocking)

**The "real" Vert.x - asynchronous magic!**

```java
// ❌ BLOCKING (NON-vert.x!)
DB.query("SELECT * FROM users WHERE id=1");  // Waits 200ms
response.end(result);

// ✅ NON-BLOCKING (Vert.x goodness!)
vertx.eventBus().request("db.query", userId)
    .onSuccess(reply -> response.end(reply.body().toString()))
    .onFailure(err -> response.end("Error"));
// Continues immediately, processes other requests!
```

**Why it matters:**
- 1 thread = can handle thousands of requests
- No blocking waits
- High performance

**With timers (simulates DB delay):**
```java
vertx.setTimer(200, timerId -> {
    // After 200ms, this executes
    msg.reply(result);  // Send the response
});
```

---

## HTTP REQUEST FLOW

```
1. GET /api/users/1
   ↓
2. MainVerticle (route handler)
   ↓
3. vertx.eventBus().request("db.query", "1")
   ↓
4. [Infinispan looks up who has "db.query" consumer]
   ↓
5. DatabaseVerticle (one of 2 instances) receives
   ↓
6. vertx.setTimer(200ms) → simulates DB query
   ↓
7. msg.reply(JsonObject user) → returns the response
   ↓
8. onSuccess() → ctx.response().end(user)
   ↓
9. HTTP 200 + JSON to client
```

---

## PROVIDED FILES

### MainVerticle.java (Original)
- HTTP route: `/api/v1/hello`
- Request/Reply: only one handler responds
- 4 HelloVerticle instances

### AdvancedMainVerticle.java (New)
- CRUD HTTP route: `/api/users/{id}`
- Deploys: Database + Publisher + Subscribers
- Port: 9090

### DatabaseVerticle.java (New)
- Simulates a database with delays
- Handlers: db.query, db.insert, db.update, db.delete
- 2 instances for HA

### EventPublisherVerticle.java (New)
- Pub/Sub: sends events every 3 seconds
- All subscribers receive them

### EventSubscriberVerticle.java (New)
- Receives published events
- 3 instances to see broadcast in action

---

## TESTING

### Compilation
```bash
mvn clean package
```

### Run original (port 8080)
```bash
java -cp target/classes:~/.m2/repository/... me.akhalef.MainVerticle
```

### Run advanced (port 9090)
```bash
java -Dhttp.port=9090 -cp target/classes:~/.m2/repository/... me.akhalef.AdvancedMainVerticle
```

### Test endpoints
```bash
# GET user
curl http://localhost:9090/api/users/1

# CREATE user
curl -X POST http://localhost:9090/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Dave","email":"dave@example.com"}'

# UPDATE user
curl -X PUT http://localhost:9090/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice Updated"}'

# DELETE user
curl -X DELETE http://localhost:9090/api/users/2
```

### Watch console
- DatabaseVerticle logs: database calls, shows delays
- EventSubscriberVerticle logs: receives all events
- EventPublisherVerticle logs: sends every 3 seconds

---

## INFINISPAN - Role in all this

```
Vert.x Event Bus
    ↓
Infinispan (Manager)
    ├─ Maintains list of consumers ("hello.vertx.address" → 4 handlers)
    ├─ Decides who receives (round-robin for request/reply)
    ├─ Distributes to all (publish/subscribe)
    └─ Synchronizes between instances if multi-machine
                ↓
                JGroups (port 7800)
                ↓
        [Can communicate with other machines]
```

**Without Infinispan:** Local event bus only (no clustering)
**With Infinispan:** Event bus distributable across machines

---

## RECAP: THE 5 KEY VERTICLES

| Verticle | Role | Instances | Example Message |
|---|---|---|---|
| **MainVerticle** | HTTP server + deploys others | 1 | GET /api/v1/hello |
| **HelloVerticle** | Responds to hello requests | 4 | "Hello Ahmed!" |
| **DatabaseVerticle** | Simulates a database | 2 | Query/Insert/Update/Delete |
| **EventPublisherVerticle** | Sends events | 1 | "Event-12345" every 3 sec |
| **EventSubscriberVerticle** | Receives events | 3 | All receive each publication |

---

## KEY TAKEAWAYS

1. **Verticles** = independent actors (can have multiple copies)
2. **Event Bus** = messaging system (request/reply OR publish/subscribe)
3. **Asynchronous** = non-blocking, processes many requests with few threads
4. **Infinispan** = enables event bus distribution across multiple machines
5. **Resilience** = multiple instances = high availability

