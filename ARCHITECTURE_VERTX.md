/**
 * DIAGRAMME VISUEL - ARCHITECTURE VERT.X
 * 
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    VERTX APPLICATION                            │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                                                                 │
 * │  ┌──────────────────────────────────────────────────────────┐   │
 * │  │           HTTP SERVER (Localhost:8080 & 9090)            │   │
 * │  └──────────────────────────────────────────────────────────┘   │
 * │    ↓ Réception des requêtes HTTP                                │
 * │                                                                 │
 * │  ┌─────────────────────────────────────────────────────────┐    │
 * │  │          MAIN VERTICLE (Orchestrator)                   │    │
 * │  │  - Lance le HTTP Server                                 │    │
 * │  │  - Déploie les autres verticles                         │    │
 * │  │  - Route les requêtes via Event Bus                     │    │
 * │  └─────────────────────────────────────────────────────────┘    │
 * │         ↓       ↓         ↓          ↓                          │
 * │                                                                 │
 * │  ┌──────────┬────────────┬──────────┬─────────────────┐         │
 * │  │          │            │          │                 │         │
 * │  ↓          ↓            ↓          ↓                 ↓         │
 * │                                                                 │
 * │ ┌────────┐ ┌────────────┐ ┌───────────────┐ ┌──────────────┐    │
 * │ │ HELLO  │ │ DATABASE   │ │PUBLISHER      │ │ SUBSCRIBER   │    │
 * │ │VERTICLE│ │ VERTICLE   │ │ VERTICLE      │ │ VERTICLE     │    │
 * │ │        │ │            │ │               │ │              │    │
 * │ │ 4 inst │ │ 2 instances│ │ 1 instance    │ │ 3 instances  │    │
 * │ │ UUID:  │ │ UUID:      │ │ UUID:         │ │ UUID:        │    │
 * │ │ abc... │ │ def...     │ │ ghi...        │ │ jkl...       │    │
 * │ │ def... │ │ xyz...     │ │               │ │ mno...       │    │
 * │ │ ghi... │ │            │ │ Timer: 3sec   │ │ pqr...       │    │
 * │ │ jkl... │ │            │ │ Publie EVENT  │ │ TOUS reçoi   │    │
 * │ └────────┘ └────────────┘ └───────────────┘ └──────────────┘    │
 * │     ↓            ↓              ↓                ↓              │
 * │                                                                 │
 * │  ┌──────────────────────────────────────────────────────────┐   │
 * │  │               EVENT BUS (Infinispan)                     │   │
 * │  │                                                          │   │
 * │  │  Consumer Registry:                                      │   │
 * │  │  • "hello.vertx.address" → [4 handlers]                  │   │
 * │  │  • "db.query" → [2 handlers]                             │   │
 * │  │  • "db.insert" → [2 handlers]                            │   │
 * │  │  • "system.events" → [3 handlers]                        │   │
 * │  │                                                          │   │
 * │  │  Distribution Method:                                    │   │
 * │  │  • request(): ROUND-ROBIN entre handlers                 │   │
 * │  │  • publish(): TOUS les handlers reçoivent                │   │
 * │  └──────────────────────────────────────────────────────────┘   │
 * │            ↑                                                    │
 * │            │ (Communication inter-verticles)                    │
 * │                                                                 │
 * │  ┌──────────────────────────────────────────────────────────┐   │
 * │  │         JGROUPS (Si clustering activé)                   │   │
 * │  │  Port: 7800                                              │   │
 * │  │  Permet communication entre machines                     │   │
 * │  └──────────────────────────────────────────────────────────┘   │
 * │            ↕ (Réseau TCP/IP)                                    │
 * │       [Autre instance Vert.x]                                   │
 * │                                                                 │
 * └─────────────────────────────────────────────────────────────────┘
 * 
 * 
 * FLUX D'UNE REQUÊTE HTTP - EXEMPLE
 * ──────────────────────────────────
 * 
 * Requête 1: GET /api/v1/hello/Ahmed
 * ─────────────────────────────────
 * 
 *  Client
 *    │ GET /api/v1/hello/Ahmed
 *    ↓
 *  MainVerticle (HTTP handler)
 *    │ vertx.eventBus().request("hello.vertx.address", "Ahmed")
 *    ↓
 *  [Infinispan cherche les handlers de "hello.vertx.address"]
 *  [Trouve 4 HelloVerticle instances]
 *  [Round-robin sélectionne la #2]
 *    ↓
 *  HelloVerticle #2 (Thread pool)
 *    │ Reçoit message: "Ahmed"
 *    │ msg.reply("Hello Ahmed, from def456!")
 *    ↓
 *  [Infinispan route la réponse]
 *    ↓
 *  MainVerticle (reçoit reply)
 *    │ onSuccess(reply -> ctx.response().end(...))
 *    ↓
 *  HTTP 200 + Body: "Hello Ahmed, from def456!"
 *    ↓
 *  Client reçoit réponse
 * 
 * 
 * Requête 2 (simultaneous): GET /api/v1/hello/Bob
 * ──────────────────────────────────────────────
 *  Même processus mais HelloVerticle #3 répond (round-robin)
 * 
 * 
 * Événement Publish/Subscribe - CHAQUE 3 SECONDES
 * ────────────────────────────────────────────
 * 
 *  EventPublisherVerticle
 *    │ vertx.eventBus().publish("system.events", "Event-123")
 *    ↓
 *  [Infinispan cherche subscribers de "system.events"]
 *  [Trouve 3 EventSubscriberVerticle instances]
 *  [Envoie à TOUS les 3 - PAS de round-robin!]
 *    ↓
 *  EventSubscriber #1 reçoit: "Event-123"
 *  EventSubscriber #2 reçoit: "Event-123"
 *  EventSubscriber #3 reçoit: "Event-123"
 *    ↓
 *  Console affiche 3 lignes identiques
 *    │ [SUBSCRIBER-xyz] Reçu: Event-123
 *    │ [SUBSCRIBER-abc] Reçu: Event-123
 *    │ [SUBSCRIBER-def] Reçu: Event-123
 * 
 * 
 * NON-BLOQUANT - AVANTAGE VERT.X
 * ────────────────────────────
 * 
 *  ❌ Approche classique (threads bloqués):
 *  
 *     Thread 1: GET /api/users/1 → DB Query (200ms) → attends... → réponse
 *     Thread 2: GET /api/users/2 → attends aussi... (pas disponible)
 *     Thread 3: Libre? Non, aussi en attente...
 *     → Besoin de 1000 threads pour 1000 requêtes! (Lent)
 * 
 *  ✅ Approche Vert.x (asynchrone):
 *  
 *     Request 1: GET /api/users/1
 *        ↓ eventBus.request("db.query", 1) [Lance la requête asynchrone]
 *        ↓ Continue immédiatement!
 *     
 *     Thread 1: Traite REQUEST 2 pendant que #1 attend la DB
 *        ↓ GET /api/users/2
 *        ↓ eventBus.request("db.query", 2) [Lance la requête asynchrone]
 *        ↓ Continue immédiatement!
 *     
 *     Thread 1: Traite REQUEST 3 pendant que #1 et #2 attendent la DB
 *        ↓ GET /api/users/3
 *        ↓ ...
 *     
 *     [200ms après]
 *     Reply #1 arrive
 *        ↓ onSuccess() callback
 *        ↓ Envoie réponse HTTP #1
 *     
 *     Reply #2 arrive
 *        ↓ onSuccess() callback
 *        ↓ Envoie réponse HTTP #2
 *     
 *     → 1 thread traite 1000+ requêtes! (Rapide)
 * 
 */

// RÉSUMÉ EN PSEUDO-CODE VERT.X
// ────────────────────────────

// 1. Créer une verticle
class MonVerticle extends AbstractVerticle {
    public void start() {
        // S'enregistrer sur event bus
        vertx.eventBus().consumer("mon.adresse", message -> {
            // Traiter le message
            message.reply("Réponse");
        });
    }
}

// 2. Les déployer
vertx.deployVerticle(new MainVerticle());
vertx.deployVerticle("me.akhalef.HelloVerticle", 
                     new DeploymentOptions().setInstances(4));

// 3. Communiquer via Event Bus
// Request/Reply (UN handler reçoit):
vertx.eventBus().request("mon.adresse", "Hello")
    .onSuccess(reply -> System.out.println(reply.body()))
    .onFailure(err -> System.out.println(err));

// Publish/Subscribe (TOUS reçoivent):
vertx.eventBus().publish("mon.adresse", "Hello");

// 4. Opérations asynchrones (non-bloquant)
vertx.setTimer(1000, id -> {
    System.out.println("Après 1 seconde");
});

vertx.eventBus().request("db.query", userId)
    .onSuccess(reply -> {
        // Traiter résultat
    })
    .onFailure(err -> {
        // Gérer erreur
    });

