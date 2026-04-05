# LAB 6: DOMAIN MODELING AND SCHEMA OPTIMIZATION - ASSESSMENT REPORT

## Overview

This lab focuses on domain modeling for a capital markets trading system. The assessment demonstrates the relationships between core entities (Orders, Executions, and Security Master), in-memory data preloading for performance optimization, and the database schema that enforces data integrity through foreign key constraints.

---

## 1. Entity-Relationship Diagram: Orders, Executions, and Security Master

The following ER diagram illustrates the relational structure of the trading system's core entities:

```
                    ┌──────────────────────────┐
                    │   SECURITY_MASTER        │
                    ├──────────────────────────┤
                    │ symbol (PK)              │
                    │ security_type (CS/ETF)   │
                    │ description              │
                    │ underlying               │
                    │ lot_size                 │
                    └──────────────────────────┘
                           △
                           │ 1:N
                           │ (symbol FK)
                           │
        ┌──────────────────┴──────────────────┐
        │                                     │
        │                                     │
    ┌───┴──────────────┐           ┌─────────┴──────────┐
    │     ORDERS       │           │   EXECUTIONS       │
    ├──────────────────┤           ├────────────────────┤
    │ order_id (PK)    │           │ exec_id (PK)       │
    │ cl_ord_id        │           │ order_id (FK) ─────┼──→ ORDERS
    │ symbol (FK) ─────┼───────────┼─ symbol            │
    │ side (1/2)       │◄──────────│ side (1/2)         │
    │ price (DECIMAL)  │           │ exec_qty           │
    │ quantity         │           │ exec_price         │
    │ status           │           │ match_time         │
    │ timestamp        │           │                    │
    └──────────────────┘           └────────────────────┘
```

### Entity Descriptions:

**SECURITY_MASTER** (Dimension Table):
- Reference table containing all tradable instruments
- Preloaded into memory at application startup (O(1) validation)
- Prevents invalid orders for non-existent symbols
- Contains metadata: security type (Common Stock, ETF, Option), lot size rules

**ORDERS** (Fact Table):
- Records all incoming trading orders from clients
- Foreign key to SECURITY_MASTER (symbol)
- Contains order state: price, quantity, side (BUY/SELL), status
- Each order has unique order_id and client-assigned cl_ord_id

**EXECUTIONS** (Child of ORDERS):
- Records actual trade matches/executions
- Foreign key to ORDERS (order_id) - parent-child relationship
- Can have multiple executions per single order (partial fills)
- Maintains audit trail: who, what, when (match_time)

### Key Relationships:

| Relationship | Type | Cardinality | Constraint |
|---|---|---|---|
| ORDERS → SECURITY_MASTER | FK (symbol) | N:1 | No delete cascade |
| EXECUTIONS → ORDERS | FK (order_id) | N:1 | Referential integrity |
| EXECUTIONS → SECURITY_MASTER | Implied (symbol) | N:1 | No cascade |

---

## 2. Java Code: Security Master Preload at Startup

### DatabaseManager.java - loadSecurityMaster() Method

The following method loads all securities from the database into an in-memory HashMap during application startup:

```java
/**
 * Load all securities from security_master into a HashMap for fast in-memory lookup.
 * Called once at startup. O(1) symbol validation thereafter.
 *
 * This approach:
 * - Eliminates database round-trip per order validation (latency: microseconds vs. milliseconds)
 * - Prevents invalid orders for unknown symbols
 * - Supports O(1) lookups even with thousands of securities
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
        
        // Iterate through result set and populate HashMap
        while (rs.next()) {
            Security sec = new Security();
            sec.setSymbol(rs.getString("symbol"));
            sec.setSecurityType(rs.getString("security_type"));
            sec.setDescription(rs.getString("description"));
            sec.setUnderlying(rs.getString("underlying"));
            sec.setLotSize(rs.getInt("lot_size"));
            
            // Add to map for O(1) lookup by symbol
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

### OrderApplication.java - Initialization

The OrderApplication loads the Security Master during construction:

```java
public OrderApplication(OrderBroadcaster broadcaster, BlockingQueue<Order> dbQueue) {
    this.broadcaster = broadcaster;
    this.dbQueue = dbQueue;
    
    // Load Security Master into memory once at startup
    this.validSecurities = DatabaseManager.loadSecurityMaster();
    
    System.out.println("[ORDER SERVICE] Security Master loaded: " 
        + validSecurities.size() + " valid symbols");
}
```

### Usage in Order Validation

During order processing, the preloaded map enables instant symbol validation:

```java
@Override
public void fromApp(Message message, SessionID sessionId) throws FieldNotFound,
        IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
    
    // Extract symbol from FIX message
    String symbol = message.getString(Symbol.FIELD);
    
    // O(1) lookup in preloaded HashMap
    if (!validSecurities.containsKey(symbol)) {
        System.err.println("[ORDER SERVICE] ✗ Invalid symbol: " + symbol);
        sendReject(message, "Unknown symbol");
        return;
    }
    
    // Get security metadata (lot size, type, etc.)
    Security security = validSecurities.get(symbol);
    
    // Validate lot size
    if (!security.isValidLotSize(quantity)) {
        System.err.println("[ORDER SERVICE] ✗ Invalid lot size for " + symbol);
        sendReject(message, "Invalid lot size");
        return;
    }
    
    // Proceed with order processing
    acceptOrder(message, sessionId);
}
```

### Performance Impact

| Operation | Latency | Database Round-Trip | Notes |
|---|---|---|---|
| **Startup preload** | ~50-200 ms | 1 (once) | Acceptable, occurs at boot |
| **Per-order validation** | ~1-5 μs | 0 | HashMap lookup (no DB call) |
| **Traditional approach** | ~2-5 ms | 1 per order | Database query for every order |
| **Benefit per 10k orders** | Saves 20-50 seconds | 10,000 queries avoided | > 1,000x faster |

---

## 3. SQL: Executions Table Creation

### Table Definition

The following SQL creates the Executions table, which records all trade matches:

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

### Column Specifications

| Column | Type | Constraints | Description |
|---|---|---|---|
| `exec_id` | VARCHAR(50) | PRIMARY KEY | Unique execution identifier (e.g., EXEC_001) |
| `order_id` | VARCHAR(50) | NOT NULL, FK | Parent order reference; enforces referential integrity |
| `symbol` | VARCHAR(20) | NOT NULL | Trading instrument (e.g., AAPL, MSFT) |
| `side` | CHAR(1) | NOT NULL | Order direction: '1' = BUY, '2' = SELL |
| `exec_qty` | INT | NOT NULL | Quantity of shares executed |
| `exec_price` | DECIMAL(15, 2) | NOT NULL | Price per share at execution time |
| `match_time` | TIMESTAMPTZ | DEFAULT NOW() | Server timestamp of match (auto-populated) |

### Foreign Key Constraint

```sql
CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
```

**Purpose**: Ensures every execution references a valid order. Prevents orphaned executions.

### Supporting Indexes

For optimal query performance, create the following indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_executions_order_id  ON executions (order_id);
CREATE INDEX IF NOT EXISTS idx_executions_symbol     ON executions (symbol);
CREATE INDEX IF NOT EXISTS idx_executions_match_time ON executions (match_time DESC);
```

**Index Rationale**:
- `idx_executions_order_id`: Enables fast lookup of all fills for a given order
- `idx_executions_symbol`: Supports reporting by instrument
- `idx_executions_match_time DESC`: Accelerates "latest executions" queries

### Sample Queries

**Retrieve recent executions with notional value:**

```sql
SELECT 
    e.exec_id,
    e.order_id,
    e.symbol,
    CASE WHEN e.side = '1' THEN 'BUY' ELSE 'SELL' END as side,
    e.exec_qty,
    e.exec_price,
    (e.exec_qty * e.exec_price) as notional_value,
    e.match_time
FROM executions e
ORDER BY e.match_time DESC
LIMIT 50;
```

**Find all executions for a specific order:**

```sql
SELECT 
    e.exec_id,
    e.symbol,
    e.exec_qty,
    e.exec_price,
    (e.exec_qty * e.exec_price) as notional,
    e.match_time
FROM executions e
WHERE e.order_id = 'ORD_a3f2e1c8'
ORDER BY e.match_time ASC;
```

**Summary statistics by symbol:**

```sql
SELECT 
    e.symbol,
    COUNT(*) as num_executions,
    SUM(e.exec_qty) as total_qty,
    AVG(e.exec_price) as avg_price,
    MIN(e.exec_price) as min_price,
    MAX(e.exec_price) as max_price,
    SUM(e.exec_qty * e.exec_price) as total_notional
FROM executions e
GROUP BY e.symbol
ORDER BY total_notional DESC;
```

---

## Design Principles Demonstrated

### 1. Referential Integrity
- Foreign key constraint ensures executions cannot reference non-existent orders
- Maintains data consistency across related tables

### 2. In-Memory Caching for Performance
- Security Master loaded once at startup (50-200 ms)
- Eliminates database round-trip per order validation
- Microsecond-level symbol lookups vs. millisecond database queries

### 3. Dimensional vs. Transactional Tables
- SECURITY_MASTER: Slowly-changing dimension (reference data)
- ORDERS/EXECUTIONS: Fast-moving fact tables (transactional data)
- Appropriate indexing strategy for each

### 4. Audit Trail
- match_time timestamp automatically recorded
- Enables compliance reporting and trade reconciliation
- Descending index on match_time for "latest trades" queries

---

## Conclusion

This lab demonstrates production-grade database design combining:
- **Relational modeling** through foreign key constraints
- **Performance optimization** via in-memory reference data caching
- **Data integrity** through structured schemas with constraints
- **Query support** through strategic indexing

These patterns are fundamental to high-performance trading systems that must handle order validation latency in microseconds while maintaining referential integrity across millions of records.
