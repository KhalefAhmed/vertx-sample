# VERT.X - GUIDE COMPLET

## Vue d'ensemble

Vert.x est framework **event-driven** et **non-bloquant** pour construire des applications distribuées.

---

## 3 CONCEPTS CLÉS

### 1️⃣ VERTICLES (Acteurs)
```
Chaque verticle = unité d'exécution indépendante

┌────────────────┐
│ MainVerticle   │ (Lance HTTP server + déploie autres)
└────────────────┘
        ├─ HelloVerticle (4 instances)
        ├─ DatabaseVerticle (2 instances)
        ├─ EventPublisherVerticle (1 instance)
        └─ EventSubscriberVerticle (3 instances)
```

**Déploiement multiple:**
```java
new DeploymentOptions().setInstances(4)
// Lance 4 copies du même verticle en parallèle
```

---

### 2️⃣ EVENT BUS (Communication)

#### A) REQUEST/REPLY (One-to-One)
```
Client (MainVerticle)
    ↓ eventBus.request("hello.vertx.address", "Ahmed")
    ↓ [Infinispan décide LEQUEL HelloVerticle répond]
Handler (HelloVerticle instance 2) reçoit SEUL
    ↓ msg.reply("Hello Ahmed, from 2!")
Réponse retourne au client
```

**Exemple:**
```java
// Client
vertx.eventBus().request("hello.vertx.address", "Ahmed")
    .onSuccess(reply -> console.log(reply.body()))  // "Hello Ahmed, from abc123!"
    .onFailure(err -> console.log(err));

// Serveur (HelloVerticle)
vertx.eventBus().consumer("hello.vertx.address", msg -> {
    msg.reply("Hello " + msg.body() + "!");  // REPLY = réponse au sender
});
```

---

#### B) PUBLISH/SUBSCRIBE (One-to-Many)
```
EventPublisherVerticle
    ↓ eventBus.publish("system.events", "Event-123")
    ↓ [Infinispan distribue à TOUS les subscribers]
EventSubscriberVerticle #1 reçoit
EventSubscriberVerticle #2 reçoit
EventSubscriberVerticle #3 reçoit
```

**Exemple:**
```java
// Publisher (EventPublisherVerticle)
vertx.eventBus().publish("system.events", "Event-123");
// Pas de reply()!

// Subscribers (EventSubscriberVerticle)
vertx.eventBus().consumer("system.events", msg -> {
    System.out.println("Received: " + msg.body());
    // Pas de msg.reply() ici!
});
```

**Différences clés:**
| Request/Reply | Publish/Subscribe |
|---|---|
| UN handler répond | TOUS reçoivent |
| Réponse attendue | Pas de réponse |
| Point-to-point | Broadcast |
| Cas: données | Cas: notifications |

---

### 3️⃣ OPERATIONS ASYNCHRONES (Non-bloquant)

**Le "vrai" Vert.x - l'asynchrone!**

```java
// ❌ BLOQUANT (NON-vert.x!)
DB.query("SELECT * FROM users WHERE id=1");  // Attend 200ms
response.end(result);

// ✅ NON-BLOQUANT (Vert.x goodness!)
vertx.eventBus().request("db.query", userId)
    .onSuccess(reply -> response.end(reply.body().toString()))
    .onFailure(err -> response.end("Error"));
// Continue immédiatement, traite autres requêtes!
```

**Pourquoi c'est important:**
- 1 thread = peut traiter des milliers de requêtes
- Pas d'attente bloquante
- Haute performance

**Avec timers (simule DB delay):**
```java
vertx.setTimer(200, timerId -> {
    // Après 200ms, ceci s'exécute
    msg.reply(result);  // Envoie la réponse
});
```

---

## FLUX D'UNE REQUÊTE HTTP

```
1. GET /api/users/1
   ↓
2. MainVerticle (route handler)
   ↓
3. vertx.eventBus().request("db.query", "1")
   ↓
4. [Infinispan cherche qui a "db.query" consumer]
   ↓
5. DatabaseVerticle (une des 2 instances) reçoit
   ↓
6. vertx.setTimer(200ms) → simule DB query
   ↓
7. msg.reply(JsonObject user) → retourne la réponse
   ↓
8. onSuccess() → ctx.response().end(user)
   ↓
9. HTTP 200 + JSON au client
```

---

## FICHIERS FOURNIS

### MainVerticle.java (Original)
- Route HTTP: `/api/v1/hello`
- Request/Reply: un seul handler répond
- 4 instances HelloVerticle

### AdvancedMainVerticle.java (Nouveau)
- Route HTTP CRUD: `/api/users/{id}`
- Déploie: Database + Publisher + Subscribers
- Port: 9090

### DatabaseVerticle.java (Nouveau)
- Simule une BD avec delays
- Handlers: db.query, db.insert, db.update, db.delete
- 2 instances pour HA

### EventPublisherVerticle.java (Nouveau)
- Pub/Sub: envoie événements toutes les 3sec
- Tous les subscribers reçoivent

### EventSubscriberVerticle.java (Nouveau)
- Reçoit les événements publiés
- 3 instances pour voir le broadcast

---

## TESTER

### Compilation
```bash
mvn clean package
```

### Lancer original (port 8080)
```bash
java -cp target/classes:~/.m2/repository/... me.akhalef.MainVerticle
```

### Lancer avancé (port 9090)
```bash
java -Dhttp.port=9090 -cp target/classes:~/.m2/repository/... me.akhalef.AdvancedMainVerticle
```

### Tester endpoints
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

### Observer console
- DatabaseVerticle logs: calls DB, shows delays
- EventSubscriberVerticle logs: reçoit tous les événements
- EventPublisherVerticle logs: envoie chaque 3sec

---

## INFINISPAN - Rôle dans tout ça

```
Vert.x Event Bus
    ↓
Infinispan (Manager)
    ├─ Tient une liste des consumers ("hello.vertx.address" → 4 handlers)
    ├─ Décide qui reçoit (round-robin pour request/reply)
    ├─ Distribue à tous (publish/subscribe)
    └─ Synchronise entre instances si multi-machine
                ↓
                JGroups (port 7800)
                ↓
        [Peut parler à autre machine]
```

**Sans Infinispan:** Event bus local seulement (pas de clustering)
**Avec Infinispan:** Event bus distribuable entre machines

---

## RECAP: LES 3 VERTICLES EXPLICITES

| Verticle | Rôle | Instances | Exemple Message |
|---|---|---|---|
| **MainVerticle** | HTTP server + déploie autres | 1 | GET /api/v1/hello |
| **HelloVerticle** | Répond à requêtes hello | 4 | "Hello Ahmed!" |
| **DatabaseVerticle** | Simule une BD | 2 | Query/Insert/Update/Delete |
| **EventPublisherVerticle** | Envoie événements | 1 | "Event-12345" toutes les 3sec |
| **EventSubscriberVerticle** | Reçoit événements | 3 | Tous reçoivent chaque publication |

---

## POINTS CLÉS À RETENIR

1. **Verticles** = acteurs indépendants (peut avoir plusieurs copies)
2. **Event Bus** = système de messaging (request/reply OU publish/subscribe)
3. **Asynchrone** = non-bloquant, traite plein de requêtes avec peu de threads
4. **Infinispan** = permet de distribuer l'event bus sur plusieurs machines
5. **Résilience** = plusieurs instances = haute disponibilité

