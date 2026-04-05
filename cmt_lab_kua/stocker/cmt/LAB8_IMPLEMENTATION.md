# LAB 8: EXECUTION REPORTING AND FEEDBACK LOOPS

## Overview
Lab 8 completes the end-to-end trading loop by implementing proper execution report generation and real-time UI updates. When orders match, execution reports must be sent to both counterparties with accurate FIX fields, the trades must be persisted to the database, and the UI must receive real-time updates.

## Implementation Summary

### 1. Execution Report Generation

**File:** `OrderApplication.java` - Methods `sendExecutionReportToBuySide()` and `sendExecutionReportToSellSide()`

#### FIX Fields Included:
```
ExecutionReport (MsgType=8) Fields:
├─ Execution IDs
│  ├─ OrderID (Tag 37)      → Server-generated order ID
│  ├─ ExecID (Tag 17)       → Unique execution ID
│  └─ ClOrdID (Tag 11)      → Echo back client's order ID (CRITICAL for matching response)
├─ Order Details
│  ├─ Symbol (Tag 55)       → Security symbol (e.g., GOOG)
│  ├─ Side (Tag 54)         → '1' = BUY, '2' = SELL
│  └─ OrdType (Tag 40)      → Order type (LIMIT)
├─ Execution Status (CRITICAL FOR LAB 8)
│  ├─ ExecType (Tag 150)    → TRADE (trade executed)
│  └─ OrdStatus (Tag 39)    → FILLED, PARTIALLY_FILLED, or NEW
├─ Quantity Accounting (CRITICAL FOR YOUR FIX)
│  ├─ CumQty (Tag 14)       → Total filled qty across ALL trades
│  ├─ LeavesQty (Tag 107)   → Remaining qty = Original - CumQty
│  ├─ LastQty (Tag 32)      → Qty in THIS trade
│  └─ LastPx (Tag 31)       → Price of THIS trade
├─ Price Information
│  ├─ Price (Tag 44)        → Order's limit price
│  ├─ AvgPx (Tag 6)         → Average price across all fills
│  └─ LastPx (Tag 31)       → Price at which THIS trade executed (resting price)
└─ Calculated in Real-Time
   ├─ OrdStatus determination
   │  └─ IF remainingQty == 0 → FILLED
   │     ELSE IF cumulativeQty > 0 → PARTIALLY_FILLED
   │     ELSE → NEW
   └─ LeavesQty = Order.Quantity - Order.CumulativeQty
```

### 2. Session Routing (CRITICAL)

**Problem Solved:** Before Lab 8, both buy and sell execution reports were sent to the SAME session (the incoming order's session). Now they route correctly:

```java
// Send to buyer's session
if (exec.getBuySessionId() != null) {
    sendExecutionReportToBuySide(exec, exec.getBuySessionId());
}
// Send to seller's session  
if (exec.getSellSessionId() != null) {
    sendExecutionReportToSellSide(exec, exec.getSellSessionId());
}
```

### 3. Execution Logging  

**New Class:** `ExecutionLogger.java` - Comprehensive trade tracing

Logs the complete execution lifecycle:
1. Order accepted into system
2. Order added to book
3. Trade matched
4. Execution reports sent (buy and sell)
5. Trade persisted to database
6. Trade broadcast to UI clients
7. Execution summary (final state of both orders)

### 4. Database Persistence

**File:** `DatabaseManager.java` - Enhanced `insertExecution()` method

```java
// For each trade, insert TWO records (one for buy, one for sell)
insertExecution(exec.getExecId() + "_BUY", exec.getBuyOrderId(), ...);
insertExecution(exec.getExecId() + "_SELL", exec.getSellOrderId(), ...);

// THEN update both orders with cumulative quantity and status
updateOrderFill(exec.getBuyClOrdId(), buyOrder.getCumulativeQty(), 
                buyOrder.getQuantity(), buyStatus);
updateOrderFill(exec.getSellClOrdId(), sellOrder.getCumulativeQty(), 
                sellOrder.getQuantity(), sellStatus);
```

### 5. Real-Time UI Updates

**WebSocket Flow:**
```
OrderApplication.processNewOrder()
├─ OrderBook.match() generates Execution
├─ sendExecutionReportToBuySide()    ← FIX Client (MiniFix)
├─ sendExecutionReportToSellSide()   ← FIX Client (MiniFix)
├─ broadcaster.broadcastExecution()  ← WebSocket to Angular
│  └─ JSON format: {"type":"execution", "data": {...}}
└─ broadcaster.queueExecutionForPersistence()  ← Async DB write
```

**Angular Components:**
- `TradesComponent` - Displays live trades blotter
- `TradingDashboardComponent` - Unified view with tabs
- `WebsocketService` - Manages execution stream

---

## Complete Test Scenario

### Setup
- **FIX Client (MiniFix):** Two instances (Buyer and Seller)
- **Java Backend:** Order Service with matching engine
- **Database:** MySQL with orders and executions tables  
- **UI:** Angular dashboard on localhost:4200

### Execution Flow (Your Scenario)

```
TIME    ACTION                              STATE
────────────────────────────────────────────────────────────────
T+0ms   SEND: OrderID=1, SELL 100@50        
        └─ Database: orders table
           cl_ord_id   | qty | cumulative_qty | status
           1           | 100 | 0              | NEW

T+10ms  SEND: OrderID=2, SELL 100@51
        └─ Database: orders table  
           cl_ord_id   | qty | cumulative_qty | status
           1           | 100 | 0              | NEW
           2           | 100 | 0              | NEW

T+20ms  SEND: OrderID=3, BUY 150@52
        ├─ Match 1: 100@50 (resting order 1)
        │  ├─ Create Execution: exec_qty=100, exec_price=50
        │  ├─ Update Order 1: cumulative_qty=100, status=FILLED
        │  ├─ Update Order 3: cumulative_qty=100, status=PARTIALLY_FILLED
        │  ├─ ExecutionReport to Buyer (Order 3):
        │  │  ClOrdID=3, CumQty=100, LeavesQty=50, OrdStatus=PARTIALLY_FILLED
        │  ├─ ExecutionReport to Seller (Order 1):
        │  │  ClOrdID=1, CumQty=100, LeavesQty=0, OrdStatus=FILLED
        │  ├─ Send to DB: exec_id=EXEC_XXX_BUY, EXEC_XXX_SELL
        │  ├─ Broadcast to UI (JSON): Execution #1
        │  └─ Update Database:
        │     orders: cl_ord_id=1 → cumulative_qty=100, status=FILLED
        │     orders: cl_ord_id=3 → cumulative_qty=100, status=PARTIALLY_FILLED
        │     executions: 2 rows (buy side + sell side)
        │
        └─ Match 2: 50@51 (resting order 2)
           ├─ Create Execution: exec_qty=50, exec_price=51
           ├─ Update Order 2: cumulative_qty=50, status=PARTIALLY_FILLED
           ├─ Update Order 3: cumulative_qty=150, status=FILLED
           ├─ ExecutionReport to Buyer (Order 3):
           │  ClOrdID=3, CumQty=150, LeavesQty=0, OrdStatus=FILLED
           ├─ ExecutionReport to Seller (Order 2):
           │  ClOrdID=2, CumQty=50, LeavesQty=50, OrdStatus=PARTIALLY_FILLED
           ├─ Send to DB: exec_id=EXEC_YYY_BUY, EXEC_YYY_SELL
           ├─ Broadcast to UI (JSON): Execution #2
           └─ Update Database:
              orders: cl_ord_id=2 → cumulative_qty=50, status=PARTIALLY_FILLED
              orders: cl_ord_id=3 → cumulative_qty=150, status=FILLED
              executions: 2 rows (buy side + sell side)
```

---

## Console Output Example

```
[TRADE TRACE] 2026-03-31T23:00:15.123Z | ORDER ACCEPTED | ClOrdID: SELL1 | Symbol: GOOG | Side: SELL | Qty: 100 @ $50.00
[TRADE TRACE] 2026-03-31T23:00:15.124Z | ORDER BOOKED | ClOrdID: SELL1 | Symbol: GOOG | Price Level: $50.00 | Qty: 100
[TRADE TRACE] 2026-03-31T23:00:15.200Z | ORDER ACCEPTED | ClOrdID: SELL2 | Symbol: GOOG | Side: SELL | Qty: 100 @ $51.00
[TRADE TRACE] 2026-03-31T23:00:15.201Z | ORDER BOOKED | ClOrdID: SELL2 | Symbol: GOOG | Price Level: $51.00 | Qty: 100
[TRADE TRACE] 2026-03-31T23:00:15.300Z | ORDER ACCEPTED | ClOrdID: BUY1 | Symbol: GOOG | Side: BUY | Qty: 150 @ $52.00
[TRADE TRACE] 2026-03-31T23:00:15.301Z | TRADE #1 MATCHED | Buy: BUY1 | Sell: SELL1 | Symbol: GOOG | Qty: 100 @ $50.00
[TRADE TRACE] 2026-03-31T23:00:15.302Z | EXEC REPORT | BUY  | ClOrdID: BUY1 | CumQty: 100 | Leaves: 50 | Status: PARTIALLY_FILLED | Price: $50.00
[TRADE TRACE] 2026-03-31T23:00:15.303Z | EXEC REPORT | SELL | ClOrdID: SELL1 | CumQty: 100 | Leaves: 0 | Status: FILLED | Price: $50.00
====================================================================================================
EXECUTION COMPLETE | ExecID: EXEC_ABC123 | Trade #1
  Buy Order  : BUY1 | CumQty: 100 | Leaves: 50 | Status: PARTIALLY_FILLED
  Sell Order : SELL1 | CumQty: 100 | Leaves: 0 | Status: FILLED
  Trade      : 100 shares @ $50.00
====================================================================================================

[TRADE TRACE] 2026-03-31T23:00:15.304Z | DB PERSIST | ExecID: EXEC_ABC123 | Symbol: GOOG | Qty: 100 @ $50.00
[DATABASE] ✓ Order fill updated: BUY1 | CumQty: 100 | Remaining: 50 | Status: PARTIALLY_FILLED
[DATABASE] ✓ Order fill updated: SELL1 | CumQty: 100 | Remaining: 0 | Status: FILLED
[TRADE TRACE] 2026-03-31T23:00:15.310Z | UI BROADCAST | ExecID: EXEC_ABC123 | Clients: 1
[WS BROADCAST] Execution | ExecID: EXEC_ABC123 | Qty: 100 @ $50.00 | Clients: 1

[TRADE TRACE] 2026-03-31T23:00:15.311Z | TRADE #2 MATCHED | Buy: BUY1 | Sell: SELL2 | Symbol: GOOG | Qty: 50 @ $51.00
[TRADE TRACE] 2026-03-31T23:00:15.312Z | EXEC REPORT | BUY  | ClOrdID: BUY1 | CumQty: 150 | Leaves: 0 | Status: FILLED | Price: $51.00
[TRADE TRACE] 2026-03-31T23:00:15.313Z | EXEC REPORT | SELL | ClOrdID: SELL2 | CumQty: 50 | Leaves: 50 | Status: PARTIALLY_FILLED | Price: $51.00
====================================================================================================
EXECUTION COMPLETE | ExecID: EXEC_XYZ789 | Trade #2
  Buy Order  : BUY1 | CumQty: 150 | Leaves: 0 | Status: FILLED
  Sell Order : SELL2 | CumQty: 50 | Leaves: 50 | Status: PARTIALLY_FILLED
  Trade      : 50 shares @ $51.00
====================================================================================================

[TRADE TRACE] 2026-03-31T23:00:15.314Z | DB PERSIST | ExecID: EXEC_XYZ789 | Symbol: GOOG | Qty: 50 @ $51.00
[DATABASE] ✓ Order fill updated: BUY1 | CumQty: 150 | Remaining: 0 | Status: FILLED
[DATABASE] ✓ Order fill updated: SELL2 | CumQty: 50 | Remaining: 50 | Status: PARTIALLY_FILLED
[TRADE TRACE] 2026-03-31T23:00:15.320Z | UI BROADCAST | ExecID: EXEC_XYZ789 | Clients: 1
[WS BROADCAST] Execution | ExecID: EXEC_XYZ789 | Qty: 50 @ $51.00 | Clients: 1
```

---

## Database State After Lab 8

### Orders Table
```sql
SELECT * FROM orders;
```
| order_id | cl_ord_id | symbol | side | price | quantity | cumulative_qty | leaves_qty | status              |
|----------|-----------|--------|------|-------|----------|----------------|------------|---------------------|
| ORD_S001 | SELL1     | GOOG   | 2    | 50.00 | 100      | 100            | 0          | FILLED              |
| ORD_S002 | SELL2     | GOOG   | 2    | 51.00 | 100      | 50             | 50         | PARTIALLY_FILLED    |
| ORD_B001 | BUY1      | GOOG   | 1    | 52.00 | 150      | 150            | 0          | FILLED              |

### Executions Table
```sql
SELECT * FROM executions;
```
| exec_id     | order_id | symbol | side | exec_qty | exec_price | match_time          |
|-------------|----------|--------|------|----------|-----------|---------------------|
| EXEC_01_BUY | ORD_B001 | GOOG   | 1    | 100      | 50.00     | 2026-03-31 23:00:15 |
| EXEC_01_SEL | ORD_S001 | GOOG   | 2    | 100      | 50.00     | 2026-03-31 23:00:15 |
| EXEC_02_BUY | ORD_B001 | GOOG   | 1    | 50       | 51.00     | 2026-03-31 23:00:15 |
| EXEC_02_SEL | ORD_S002 | GOOG   | 2    | 50       | 51.00     | 2026-03-31 23:00:15 |

---

## Angular UI Display

### Trades Component Output
```
Live Trades (Executions)
Connected ●

Total Trades: 2  |  Total Shares: 150  |  Avg Price: $50.33  |  Total Value: $7,550.00

─────────────────────────────────────────────────────────────────────────────
Exec ID     │ Symbol │ Buy Order │ Sell Order │ Shares │ Price │ Trade Value
─────────────────────────────────────────────────────────────────────────────
EXEC_01_BUY │ GOOG   │ BUY1      │ SELL1      │ 100    │ $50.00 │ $5,000.00
EXEC_02_BUY │ GOOG   │ BUY1      │ SELL2      │ 50     │ $51.00 │ $2,550.00
─────────────────────────────────────────────────────────────────────────────
```

### Trading Dashboard Stats
```
📊 Total Orders: 3 (1 buy | 2 sell)
💱 Executions: 2
   150 shares traded
💰 Trade Value: $7,550.00
   Avg: $50.33
📈 Order Notional: $0.00 (all orders filled)
```

---

## Key Learnings (Lab 8)

1. **Execution Reports are NOT optional** - FIX clients expect them for every fill
2. **CumQty must accumulate** - Each report shows total filled, not just this trade
3. **OrdStatus progression** - Must correctly transition: NEW → PARTIALLY_FILLED → FILLED
4. **Session routing is critical** - Both buy and sell sides need reports sent to THEIR session
5. **Database must reflect matching** - cumulative_qty column tracks fills accurately
6. **UI updates must be real-time** - WebSocket ensures traders see fills immediately
7. **Trade price = resting price** - Always the limit price of the order already in the book  

---

## Verification Checklist

- [ ] Two SELL orders entered (100@50, 100@51)
- [ ] One BUY order entered (150@52)
- [ ] First SELL matched completely (Status=FILLED)
- [ ] BUY partially filled after first match (Status=PARTIALLY_FILLED, CumQty=100)
- [ ] Second SELL partially matched (Status=PARTIALLY_FILLED, CumQty=50)
- [ ] BUY fully filled after second match (Status=FILLED, CumQty=150)
- [ ] Execution reports received in MiniFix (both buy and sell)
- [ ] Trades appear instantly in Angular UI
- [ ] Database persists both execution records
- [ ] Order status changes persist in database
- [ ] Console shows complete execution trace

