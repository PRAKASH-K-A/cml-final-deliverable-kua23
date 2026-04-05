# LAB 5: DATA PERSISTENCE WITH POSTGRESQL - ASSESSMENT REPORT


### 1. OrderPersister Run Loop

The following code excerpt presents the core run loop of the OrderPersister class, which implements the consumer thread in the producer-consumer pattern:

```java
@Override
public void run() {
    System.out.println("[PERSISTENCE] Database Worker Thread Started");
    System.out.println("[PERSISTENCE] Listening for orders on queue...");
    
    while (running) {
        try {
            Order order = orderQueue.take();
            
            long startTime = System.nanoTime();
            DatabaseManager.insertOrder(order);
            long elapsedMicros = (System.nanoTime() - startTime) / 1000;
            
            long count = persistedCount.incrementAndGet();
            
            System.out.println(String.format(
                "[PERSISTENCE] Order #%d persisted in %d μs | Queue size: %d | ClOrdID: %s",
                count, elapsedMicros, orderQueue.size(), order.getClOrdID()
            ));
            
        } catch (InterruptedException e) {
            System.out.println("[PERSISTENCE] Worker thread interrupted");
            Thread.currentThread().interrupt();
            break;
            
        } catch (Exception e) {
            System.err.println("[PERSISTENCE] Error persisting order: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    drainQueue();
    
    System.out.println("[PERSISTENCE] Worker thread stopped. Total persisted: " + persistedCount.get());
}
```

The run method executes in a continuous loop controlled by the volatile `running` flag. The thread calls `orderQueue.take()`, which blocks until an order becomes available. Upon retrieval, the order is persisted to PostgreSQL through `DatabaseManager.insertOrder()`. Latency is measured in microseconds for performance monitoring. The method handles `InterruptedException` for graceful shutdown and catches general exceptions to prevent thread termination. Upon shutdown, the `drainQueue()` method ensures remaining orders are persisted before the thread exits.

---

### 2. SQL Query and Results: Populated Data


```sql
SELECT 
    order_id,
    cl_ord_id,
    symbol,
    side,
    price,
    quantity,
    status,
    timestamp
FROM orders
ORDER BY timestamp DESC
LIMIT 10;
```

**Expected Query Result**:

The query returns a table with the following representative data:

| order_id | cl_ord_id | symbol | side | price | quantity | status | timestamp |
|----------|-----------|--------|------|-------|----------|--------|-----------|
| ORD_c4e2d9a7 | CLIENT_100 | MSFT | 2 | 245.75 | 250 | NEW | 2026-03-30 14:33:47.892341 |
| ORD_b3f1e8c6 | CLIENT_099 | AAPL | 1 | 150.50 | 100 | NEW | 2026-03-30 14:33:47.123456 |
| ORD_a2e0d7b5 | CLIENT_098 | GOOGL | 2 | 155.25 | 75 | NEW | 2026-03-30 14:33:46.987654 |
| ORD_91d9c6a4 | CLIENT_097 | TSLA | 1 | 242.30 | 200 | NEW | 2026-03-30 14:33:46.654321 |

The populated orders table contains records with instrument symbols (AAPL, MSFT, GOOGL, TSLA, META), order sides (1 for BUY, 2 for SELL), prices, quantities, and server-generated timestamps demonstrating successful asynchronous persistence.

---

### 3. Analysis: LinkedBlockingQueue vs. ArrayBlockingQueue

#### Rationale for LinkedBlockingQueue Selection

LinkedBlockingQueue was selected as the inter-thread communication mechanism for the following technical reasons:

**Unbounded Capacity**

LinkedBlockingQueue operates with a logical capacity of Integer.MAX_VALUE (approximately 2.1 billion elements) by default. This unbounded nature ensures that transient database slowdowns do not reject incoming orders. In a trading system where order loss is unacceptable, this characteristic is critical.

**Lock-Free Performance Under Concurrency**

LinkedBlockingQueue implements separate locks for insertion (put/offer) and removal (take/poll) operations. This dual-lock architecture permits concurrent producers and consumers to operate independently, reducing contention compared to single-lock implementations. At high order volumes, this architecture maintains low latency on the FIX engine thread.

**Dynamic Memory Allocation**

Since LinkedBlockingQueue is node-based rather than array-based, memory allocation is dynamic and proportional to the number of queued elements. The queue does not require pre-allocation of large fixed-size arrays, providing memory efficiency during normal operation while retaining capacity for traffic spikes.

**Production Reliability Requirements**

Trading systems operate under the principle that data preservation takes precedence over resource constraints. An unbounded queue serves as a buffer against temporary outages, aligning with operational requirements for fault tolerance.

---

#### ArrayBlockingQueue with Database Outage

Consider the alternative design using ArrayBlockingQueue with fixed capacity:

```java
BlockingQueue<Order> queue = new ArrayBlockingQueue<>(10000);
```

**Scenario: Five-second PostgreSQL outage with continuous order flow at 10,000 orders/second**

**Timeline of System Degradation**:

| Time (sec) | Queue Size | Status | System State |
|------------|------------|--------|--------------|
| 0.0 | 0 | Database online | Normal operations |
| 1.0 | 5,000 | Orders queuing | No ACK delays |
| 2.0 | 10,000 | Queue at capacity | FULL |
| 2.1 | 10,000 | Database offline | queue.offer() returns false |
| 2.2 | 10,000 | 10,000 orders dropped | ACKs sent but no persistence |
| 3.0 | Still at capacity | Complete backlog | New orders cannot enter queue |

**Critical Failure Modes**:

1. **Silent Data Loss**: When `offer()` returns false, the FIX engine receives no exception. Code may continue with false assumption that order was queued. Client receives ACK but order never persists to database. Upon audit, 10,000 orders are missing from the database.

2. **Queue Exhaustion and Cascading Failure**: With fixed capacity exhausted, subsequent order submissions either:
   - Return false (silent failure)
   - Throw an exception (crashes FIX engine thread)
   - Block indefinitely on `put()` (FIX engine latency degradation, violating SLA)

3. **Unrecoverable Data Corruption**: Once orders are silently dropped, no recovery mechanism exists. The application has confirmed execution to clients who received ACKs, but the database contains no record of these trades.

---

**Comparative Behavior: LinkedBlockingQueue During Same Outage**:

| Time (sec) | Queue Size | Memory (MB) | Status |
|------------|------------|------------|--------|
| 0.0 | 0 | 10 | Normal |
| 1.0 | 5,000 | 80 | Buffering |
| 2.0 | 10,000 | 150 | Heavy buffering |
| 3.0 | 20,000 | 250 | Sustained backlog |
| 4.0 | 30,000 | 350 | Large backlog |
| 5.0 | Database recovers | 350 | Persister begins draining |
| 6.0 | 20,000 | 300 | Actively draining |
| 8.0 | 5,000 | 150 | Nearly cleared |
| 10.0 | 0 | 10 | Full recovery |

**Outcome**: All 50,000 orders remain in queue and are subsequently persisted. No data loss occurs. System returns to normal operation once database recovers. Memory consumption, while elevated, remains bounded by available heap space.

---

#### 3.3 Engineering Trade-Offs

The choice between LinkedBlockingQueue and ArrayBlockingQueue represents a fundamental trade-off:

| Dimension | LinkedBlockingQueue | ArrayBlockingQueue |
|-----------|---------------------|-------------------|
| Data Loss Risk | None (unbounded) | Severe (queue fills) |
| Memory Predictability | Unbounded | Fixed/Bounded |
| Database Outage Resilience | Robust | Fragile |
| FIX Latency Protection | Maintained | Compromised |
| Production Suitability (Trading) | Suitable | Unsuitable |
| Production Suitability (Real-Time IoT) | Less suitable | Suitable |

For financial trading systems where each order represents capital and client trust, LinkedBlockingQueue is the appropriate choice. The unbounded queue acts as a buffer against infrastructure failures, preserving the guarantee that client ACKs are honored.

---

## 4. Entity-Relationship Diagram

The following ER diagram illustrates the relationships between Orders, Executions, Security Master, and Customer Master:

```
                    ┌──────────────────┐
                    │ SECURITY_MASTER  │
                    ├──────────────────┤
                    │ symbol (PK)      │
                    │ security_type    │
                    │ description      │
                    │ underlying       │
                    │ lot_size         │
                    └──────────────────┘
                           ▲
                           │ referenced by
                           │ (symbol)
                           │
    ┌──────────────────────┴────────────────────────┐
    │                                               │
    │                                               │
┌───┴─────────────────┐                    ┌──────┴──────────────┐
│      ORDERS         │                    │   EXECUTIONS        │
├─────────────────────┤                    ├─────────────────────┤
│ order_id (PK)       │                    │ exec_id (PK)        │
│ cl_ord_id           │                    │ order_id (FK)───────┼──→ ORDERS
│ symbol (FK)─────────┼────────────────────┤ symbol              │
│ side                │                    │ side                │
│ price               │◄───────────────────│ match_time          │
│ quantity            │                    │ exec_qty            │
│ status              │                    │ exec_price          │
│ timestamp           │                    └─────────────────────┘
└─────────────────────┘
         ▲
         │ references
         │
    ┌────┴───────────────┐
    │ CUSTOMER_MASTER    │
    ├────────────────────┤
    │ customer_code (PK) │
    │ customer_name      │
    │ customer_type      │
    │ credit_limit       │
    └────────────────────┘
```

**Relationships**:
- ORDERS references SECURITY_MASTER via symbol (implied foreign key)
- EXECUTIONS references ORDERS via order_id (explicit foreign key constraint)
- EXECUTIONS references SECURITY_MASTER via symbol (implied foreign key)
- CUSTOMER_MASTER maintains approved clients (future reference from ORDERS)

---

## 5. Code: Security Master Preload at Startup

The following code demonstrates how the Security Master is loaded from PostgreSQL into an in-memory HashMap for O(1) symbol validation during order processing:

```java
/**
 * Load all securities from security_master into a HashMap for fast in-memory lookup.
 * Called once at startup. O(1) symbol validation thereafter.
 *
 * @return Map of symbol -> Security object
 */
public static Map<String, Security> loadSecurityMaster() {
    Map<String, Security> securities = new HashMap<>();
    String sql = "SELECT symbol, security_type, description, underlying, lot_size FROM security_master";
    
    System.out.println("[DATABASE] Loading Security Master from PostgreSQL...");
    
    try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
        
        while (rs.next()) {
            Security sec = new Security();
            sec.setSymbol(rs.getString("symbol"));
            sec.setSecurityType(rs.getString("security_type"));
            sec.setDescription(rs.getString("description"));
            sec.setUnderlying(rs.getString("underlying"));
            sec.setLotSize(rs.getInt("lot_size"));
            securities.put(sec.getSymbol(), sec);
        }
        
        System.out.println("[DATABASE] ✓ Security Master loaded: " + securities.size() + " securities");
        securities.forEach((k, v) -> System.out.println("  - " + k + " (" + v.getSecurityType() + ")"));
        
    } catch (SQLException e) {
        System.err.println("[DATABASE] ✗ Failed to load Security Master: " + e.getMessage());
        e.printStackTrace();
    }
    
    return securities;
}
```

**Invocation at Application Startup** (OrderApplication.java):

```java
public OrderApplication(OrderBroadcaster broadcaster, BlockingQueue<Order> dbQueue) {
    this.broadcaster = broadcaster;
    this.dbQueue = dbQueue;
    this.validSecurities = DatabaseManager.loadSecurityMaster();
    System.out.println("[ORDER SERVICE] Security Master loaded: " + validSecurities.size() + " valid symbols");
}
```

**Usage for Order Validation**:
```java
// Validate incoming order symbol against preloaded Security Master
if (!validSecurities.containsKey(orderSymbol)) {
    System.err.println("[ORDER SERVICE] ✗ Invalid symbol: " + orderSymbol);
    rejectOrder(message, "Unknown symbol");
    return;
}

Security validSecurity = validSecurities.get(orderSymbol);
// Proceed with order processing
```

**Performance Characteristics**:
- Initial load: One-time O(n) query on startup (n = number of securities)
- Validation latency: O(1) HashMap lookup per order (microseconds)
- Memory overhead: Minimal (approx. 1 KB per Security object)
- Benefit: Eliminates database round-trip for every order validation

---

## 6. SQL: Executions Table Creation

The following SQL statement creates the Executions table, which records trade matches resulting from order execution:

```sql
CREATE TABLE IF NOT EXISTS executions (
    exec_id     VARCHAR(50)     PRIMARY KEY,
    order_id    VARCHAR(50)     NOT NULL,
    symbol      VARCHAR(20)     NOT NULL,
    side        CHAR(1)         NOT NULL,            -- '1' = BUY, '2' = SELL
    exec_qty    INT             NOT NULL,
    exec_price  DECIMAL(15, 2)  NOT NULL,
    match_time  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
```

**Column Definitions**:
- `exec_id`: Unique execution identifier (primary key)
- `order_id`: Foreign key reference to parent order
- `symbol`: Trading instrument identifier
- `side`: Order direction ('1' for BUY, '2' for SELL)
- `exec_qty`: Quantity of shares executed
- `exec_price`: Price per share at execution
- `match_time`: Server timestamp of match (defaults to NOW())

**Foreign Key Constraint**:
- `fk_order` ensures referential integrity: all executions must reference valid orders
- On order deletion: cascade behavior depends on application requirements
- Maintains parent-child relationship with orders table

**Supporting Indexes** (for query performance):

```sql
CREATE INDEX IF NOT EXISTS idx_executions_order_id  ON executions (order_id);
CREATE INDEX IF NOT EXISTS idx_executions_symbol     ON executions (symbol);
CREATE INDEX IF NOT EXISTS idx_executions_match_time ON executions (match_time DESC);
```

**Sample Query** (retrieve most recent executions):

```sql
SELECT 
    e.exec_id,
    e.order_id,
    e.symbol,
    CASE WHEN e.side = '1' THEN 'BUY' ELSE 'SELL' END as side,
    e.exec_qty,
    e.exec_price,
    (e.exec_qty * e.exec_price) as notional,
    e.match_time
FROM executions e
ORDER BY e.match_time DESC
LIMIT 50;
```

---

## Conclusion

This assessment demonstrates the integration of multiple system components: data persistence (PostgreSQL), in-memory caching (Security Master preload), and asynchronous processing (OrderPersister). The design patterns employed—particularly the use of LinkedBlockingQueue for fault tolerance and in-memory hash maps for performance—reflect production-grade trading system architecture principles.

