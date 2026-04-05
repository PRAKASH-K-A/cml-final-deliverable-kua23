# LAB 8: TEST TRACE & EXECUTION FLOW

## Test Scenario

### Pre-test State
- Order Service running on port 9876
- WebSocket server running on port 8080
- Angular dashboard on localhost:4200
- MiniFix connected as two separate sessions

---

## Complete Execution Trace

### PHASE 1: First SELL Order (100@50)

```
┌─────────────────────────────────────────────────────────────────┐
│ MiniFix (Seller 1) → Send: NewOrderSingle                       │
│ ClOrdID=SELL1 | Symbol=GOOG | Side=SELL | Qty=100 | Price=50.00 │
└─────────────────────────────────────────────────────────────────┘
                            ↓
        [ORDER SERVICE - FIX Acceptor]
        
[TRADE TRACE] 2026-03-31T23:00:15.120Z | ORDER ACCEPTED | 
  ClOrdID: SELL1 | Symbol: GOOG | Side: SELL | Qty: 100 @ $50.00

[ORDER RECEIVED] ID=SELL1 | Side: SELL | Symbol: GOOG | Price: $50.00 | Qty: 100

        [VALIDATION]
        ✓ Price > 0
        ✓ Qty > 0  
        ✓ Lot Size OK
        ✓ Security Master has GOOG

        [MATCHING ENGINE]
        OrderBook.match(SELL_ORDER)
        ├─ Check BID side (buyers) for matches
        │  └─ BID side empty → No matches
        └─ Add to ASK side at $50.00
        
[TRADE TRACE] 2026-03-31T23:00:15.121Z | ORDER BOOKED | 
  ClOrdID: SELL1 | Symbol: GOOG | Price Level: $50.00 | Qty: 100

        [ACKNOWLEDGMENT - ExecutionReport (ExecType=NEW)]
        ├─ OrderID: ORD_S001
        ├─ ClOrdID: SELL1 (ECHO BACK)
        ├─ Symbol: GOOG
        ├─ Side: SELL
        ├─ CumQty: 0
        ├─ LeavesQty: 100
        ├─ OrdStatus: NEW
        └─→ Send to Seller 1's FIX session
        
[ACK SENT] ACCEPTED | Filled: 0 | Remaining: 100

        [PERSISTENCE]
        Order pushed to database queue (asynchronous)
        
[ORDER SERVICE] Order queued for persistence: SELL1
[DB WORKER] Processing asynchronous write...
[DATABASE] ✓ Order persisted: SELL1 (GOOG) Type=2
  Inserted into orders:
  ├─ order_id: ORD_S001
  ├─ cl_ord_id: SELL1
  ├─ symbol: GOOG
  ├─ side: 2 (SELL)
  ├─ price: 50.00
  ├─ quantity: 100
  ├─ cumulative_qty: 0
  ├─ status: NEW
  └─ timestamp: 2026-03-31 23:00:15

        [UI BROADCAST]
        └─→ Send to Angular UI via WebSocket
              {"clOrdID": "SELL1", "symbol": "GOOG", "side": "2", ...}
              
[WS BROADCAST] Order | ClOrdID: SELL1 | Clients: 1
[WEBSOCKET] ✓ Connected UI received order update

                ↓ ↓ ↓
        [MiniFix (Seller 1)]        [Angular Dashboard]
        ✓ Order Status=New          ✓ Order appears in grid
```

---

### PHASE 2: Second SELL Order (100@51)

```
┌─────────────────────────────────────────────────────────────────┐
│ MiniFix (Seller 2) → Send: NewOrderSingle                       │
│ ClOrdID=SELL2 | Symbol=GOOG | Side=SELL | Qty=100 | Price=51.00 │
└─────────────────────────────────────────────────────────────────┘
                            ↓
        [SAME FLOW AS PHASE 1]
        
[TRADE TRACE] 2026-03-31T23:00:15.220Z | ORDER ACCEPTED | 
  ClOrdID: SELL2 | Symbol: GOOG | Side: SELL | Qty: 100 @ $51.00

[TRADE TRACE] 2026-03-31T23:00:15.221Z | ORDER BOOKED | 
  ClOrdID: SELL2 | Symbol: GOOG | Price Level: $51.00 | Qty: 100

        BookState:
        ASK Side (Sellers) - Sorted Low to High:
        ├─ $50.00: [SELL1:100]
        └─ $51.00: [SELL2:100]  ← Newly added
        
        [DATABASE UPDATE]
        INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, 
                          quantity, cumulative_qty, status)
        VALUES ('ORD_S002', 'SELL2', 'GOOG', '2', 51.00, 100, 0, 'NEW');

[WEBSOCKET] ✓ Connected UI received order update
```

---

### PHASE 3: BUY Order (150@52) → TRIGGERS MATCHING

```
┌─────────────────────────────────────────────────────────────────┐
│ MiniFix (Buyer) → Send: NewOrderSingle                          │
│ ClOrdID=BUY1 | Symbol=GOOG | Side=BUY | Qty=150 | Price=52.00   │
└─────────────────────────────────────────────────────────────────┘
                            ↓
        [ORDER SERVICE]
        
[TRADE TRACE] 2026-03-31T23:00:15.320Z | ORDER ACCEPTED | 
  ClOrdID: BUY1 | Symbol: GOOG | Side: BUY | Qty: 150 @ $52.00

[ORDER RECEIVED] ID=BUY1 | Side: BUY | Symbol: GOOG | Price: $52.00 | Qty: 150

        [MATCHING ENGINE - CRITICAL SECTION]
        OrderBook.match(BUY_ORDER)
        ├─ Incoming: BUY 150@52
        ├─ Check ASK side (sellers) for matches
        │
        ├─ MATCH CHECK 1:
        │  ├─ Best Ask: $50.00 (SELL1)
        │  ├─ Condition: BUY@52 >= ASK@50? YES → MATCH!
        │  ├─ Trade Qty: min(150, 100) = 100 shares
        │  ├─ Trade Price: $50.00 (resting order price)
        │  │
        │  ├─ CREATE EXECUTION #1
        │  │  ├─ execId: EXEC_ABC_001
        │  │  ├─ buyClOrdId: BUY1
        │  │  ├─ sellClOrdId: SELL1
        │  │  ├─ execQty: 100
        │  │  ├─ execPrice: 50.00
        │  │  └─ execTime: 2026-03-31T23:00:15.321Z
        │  │
        │  ├─ REDUCE QUANTITIES:
        │  │  ├─ BUY1.quantity: 150 → 50 (100 filled)
        │  │  ├─ BUY1.cumulativeQty: 0 → 100
        │  │  ├─ SELL1.quantity: 100 → 0 (fully filled)
        │  │  └─ SELL1.cumulativeQty: 0 → 100
        │  │
        │  ├─ DETERMINE ORDER STATUS:
        │  │  ├─ BUY1: remainingQty=50, cumQty=100 → PARTIALLY_FILLED
        │  │  └─ SELL1: remainingQty=0, cumQty=100 → FILLED
        │  │
        │  ├─ SEND EXECUTION REPORTS:
        │  │  │
        │  │  ├─→ TO BUYER (BUY1's session):
        │  │  │    ExecutionReport:
        │  │  │    ├─ OrderID: ORD_B001
        │  │  │    ├─ ExecID: EXEC_ABC_001_BUY
        │  │  │    ├─ ClOrdID: BUY1
        │  │  │    ├─ Symbol: GOOG
        │  │  │    ├─ Side: BUY (1)
        │  │  │    ├─ ExecType: TRADE (F)
        │  │  │    ├─ OrdStatus: PARTIALLY_FILLED (2)
        │  │  │    ├─ CumQty: 100      [CRITICAL]
        │  │  │    ├─ LeavesQty: 50    [CRITICAL]
        │  │  │    ├─ LastQty: 100
        │  │  │    ├─ LastPx: 50.00
        │  │  │    ├─ AvgPx: 50.00
        │  │  │    └─ Price: 52.00
        │  │  │    
[TRADE TRACE] 2026-03-31T23:00:15.321Z | EXEC REPORT | BUY  | 
  ClOrdID: BUY1 | CumQty: 100 | Leaves: 50 | Status: PARTIALLY_FILLED | Price: $50.00
[EXECUTION REPORT] BUY  | ClOrdID: BUY1 | Filled: 100 | CumQty: 100 | Leaves: 50 | Status: 2
        │  │    
        │  ├─→ TO SELLER (SELL1's session):
        │  │    ExecutionReport:
        │  │    ├─ OrderID: ORD_S001
        │  │    ├─ ExecID: EXEC_ABC_001_SELL
        │  │    ├─ ClOrdID: SELL1
        │  │    ├─ Symbol: GOOG
        │  │    ├─ Side: SELL (2)
        │  │    ├─ ExecType: TRADE (F)
        │  │    ├─ OrdStatus: FILLED (2)
        │  │    ├─ CumQty: 100      [100% filled]
        │  │    ├─ LeavesQty: 0
        │  │    ├─ LastQty: 100
        │  │    ├─ LastPx: 50.00
        │  │    ├─ AvgPx: 50.00
        │  │    └─ Price: 50.00
        │  │    
[TRADE TRACE] 2026-03-31T23:00:15.322Z | EXEC REPORT | SELL | 
  ClOrdID: SELL1 | CumQty: 100 | Leaves: 0 | Status: FILLED | Price: $50.00
[EXECUTION REPORT] SELL | ClOrdID: SELL1 | Filled: 100 | CumQty: 100 | Leaves: 0 | Status: 2
        │  │
        │  ├─ LOG EXECUTION SUMMARY:
        │  │
        │  └────────────────────────────────────────────────────────
[TRADE TRACE] 2026-03-31T23:00:15.323Z | TRADE #1 MATCHED | 
  Buy: BUY1 | Sell: SELL1 | Symbol: GOOG | Qty: 100 @ $50.00
        │
        │    ════════════════════════════════════════════════════════════════════════════════════════
        │    EXECUTION COMPLETE | ExecID: EXEC_ABC_001 | Trade #1
        │    Buy Order  : BUY1 | CumQty: 100 | Leaves: 50 | Status: PARTIALLY_FILLED
        │    Sell Order : SELL1 | CumQty: 100 | Leaves: 0 | Status: FILLED
        │    Trade      : 100 shares @ $50.00
        │    ════════════════════════════════════════════════════════════════════════════════════════
        │
        │  ├─ DATABASE PERSISTENCE (ASYNC):
        │  │  ├─ Thread spawned for DB write
        │  │  ├─ INSERT executions (buy side):
        │  │  │  exec_id=EXEC_ABC_001_BUY, order_id=ORD_B001, symbol=GOOG, 
        │  │  │  side=1, exec_qty=100, exec_price=50.00
        │  │  ├─ INSERT executions (sell side):
        │  │  │  exec_id=EXEC_ABC_001_SELL, order_id=ORD_S001, symbol=GOOG, 
        │  │  │  side=2, exec_qty=100, exec_price=50.00
        │  │  ├─ UPDATE orders (BUY1):
        │  │  │  cumulative_qty=100, status=PARTIALLY_FILLED WHERE cl_ord_id=BUY1
        │  │  └─ UPDATE orders (SELL1):
        │  │     cumulative_qty=100, status=FILLED WHERE cl_ord_id=SELL1
        │  │
[TRADE TRACE] 2026-03-31T23:00:15.325Z | DB PERSIST | 
  ExecID: EXEC_ABC_001 | Symbol: GOOG | Qty: 100 @ $50.00
[DATABASE] ✓ Order fill updated: BUY1 | CumQty: 100 | Remaining: 50 | Status: PARTIALLY_FILLED
[DATABASE] ✓ Order fill updated: SELL1 | CumQty: 100 | Remaining: 0 | Status: FILLED
        │  │
        │  ├─ UI BROADCAST (VIA WEBSOCKET):
        │  │  ├─ Convert Execution to JSON:
        │  │  │  {
        │  │  │    "type": "execution",
        │  │  │    "data": {
        │  │  │      "execId": "EXEC_ABC_001",
        │  │  │      "buyOrderId": "ORD_B001",
        │  │  │      "sellOrderId": "ORD_S001",
        │  │  │      "buyClOrdId": "BUY1",
        │  │  │      "sellClOrdId": "SELL1",
        │  │  │      "symbol": "GOOG",
        │  │  │      "execQty": 100,
        │  │  │      "execPrice": 50.00,
        │  │  │      "execTime": "2026-03-31T23:00:15.321Z"
        │  │  │    }
        │  │  │  }
        │  │  └─ Broadcast to all connected Angular UIs
        │  │
[TRADE TRACE] 2026-03-31T23:00:15.327Z | UI BROADCAST | 
  ExecID: EXEC_ABC_001 | Clients: 1
[WS BROADCAST] Execution | ExecID: EXEC_ABC_001 | Qty: 100 @ $50.00 | Clients: 1
        │  │
        │  └─ CONTINUE MATCHING LOOP (BUY1 still has 50 qty remaining)
        │
        │
        ├─ MATCH CHECK 2:
        │  ├─ Incoming: BUY 50@52 (reduced from 150)
        │  ├─ Best Ask: $51.00 (SELL2)
        │  ├─ Condition: BUY@52 >= ASK@51? YES → MATCH!
        │  ├─ Trade Qty: min(50, 100) = 50 shares
        │  ├─ Trade Price: $51.00 (resting order price)
        │  │
        │  ├─ CREATE EXECUTION #2
        │  │  ├─ execId: EXEC_XYZ_002
        │  │  ├─ buyClOrdId: BUY1
        │  │  ├─ sellClOrdId: SELL2
        │  │  ├─ execQty: 50
        │  │  ├─ execPrice: 51.00
        │  │  └─ execTime: 2026-03-31T23:00:15.328Z
        │  │
        │  ├─ REDUCE QUANTITIES:
        │  │  ├─ BUY1.quantity: 50 → 0 (fully filled now)
        │  │  ├─ BUY1.cumulativeQty: 100 → 150  [ACCUMULATES]
        │  │  ├─ SELL2.quantity: 100 → 50 (partially filled)
        │  │  └─ SELL2.cumulativeQty: 0 → 50
        │  │
        │  ├─ DETERMINE ORDER STATUS:
        │  │  ├─ BUY1: remainingQty=0, cumQty=150 → FILLED
        │  │  └─ SELL2: remainingQty=50, cumQty=50 → PARTIALLY_FILLED
        │  │
        │  ├─ SEND EXECUTION REPORTS:
        │  │  │
        │  │  ├─→ TO BUYER (BUY1's session):
        │  │  │    ExecutionReport:
        │  │  │    ├─ OrderID: ORD_B001
        │  │  │    ├─ ClOrdID: BUY1
        │  │  │    ├─ OrdStatus: FILLED (fully filled now)
        │  │  │    ├─ CumQty: 150      [ACCUMULATED FROM BOTH TRADES]
        │  │  │    ├─ LeavesQty: 0
        │  │  │    ├─ LastQty: 50      (this trade)
        │  │  │    ├─ LastPx: 51.00    (this trade's price)
        │  │  │    ├─ AvgPx: 50.33     (weighted average of both trades)
        │  │  │    └─ Price: 52.00     (original limit)
        │  │  │    
[TRADE TRACE] 2026-03-31T23:00:15.328Z | EXEC REPORT | BUY  | 
  ClOrdID: BUY1 | CumQty: 150 | Leaves: 0 | Status: FILLED | Price: $51.00
[EXECUTION REPORT] BUY  | ClOrdID: BUY1 | Filled: 50 | CumQty: 150 | Leaves: 0 | Status: 2
        │  │    
        │  ├─→ TO SELLER (SELL2's session):
        │  │    ExecutionReport:
        │  │    ├─ OrderID: ORD_S002
        │  │    ├─ ClOrdID: SELL2
        │  │    ├─ OrdStatus: PARTIALLY_FILLED (only 50 of 100 filled)
        │  │    ├─ CumQty: 50
        │  │    ├─ LeavesQty: 50
        │  │    ├─ LastQty: 50
        │  │    ├─ LastPx: 51.00
        │  │    └─ Price: 51.00
        │  │    
[TRADE TRACE] 2026-03-31T23:00:15.329Z | EXEC REPORT | SELL | 
  ClOrdID: SELL2 | CumQty: 50 | Leaves: 50 | Status: PARTIALLY_FILLED | Price: $51.00
[EXECUTION REPORT] SELL | ClOrdID: SELL2 | Filled: 50 | CumQty: 50 | Leaves: 50 | Status: 1
        │  │
        │  ├─ LOG EXECUTION SUMMARY:
        │  │
        │  └────────────────────────────────────────────────────────
[TRADE TRACE] 2026-03-31T23:00:15.330Z | TRADE #2 MATCHED | 
  Buy: BUY1 | Sell: SELL2 | Symbol: GOOG | Qty: 50 @ $51.00
        │
        │    ════════════════════════════════════════════════════════════════════════════════════════
        │    EXECUTION COMPLETE | ExecID: EXEC_XYZ_002 | Trade #2
        │    Buy Order  : BUY1 | CumQty: 150 | Leaves: 0 | Status: FILLED
        │    Sell Order : SELL2 | CumQty: 50 | Leaves: 50 | Status: PARTIALLY_FILLED
        │    Trade      : 50 shares @ $51.00
        │    ════════════════════════════════════════════════════════════════════════════════════════
        │
        │  ├─ DATABASE PERSISTENCE:
        │  │  ├─ INSERT executions (buy side)
        │  │  ├─ INSERT executions (sell side)
        │  │  ├─ UPDATE orders: BUY1 → cumulative_qty=150, status=FILLED
        │  │  └─ UPDATE orders: SELL2 → cumulative_qty=50, status=PARTIALLY_FILLED
        │  │
[TRADE TRACE] 2026-03-31T23:00:15.332Z | DB PERSIST | 
  ExecID: EXEC_XYZ_002 | Symbol: GOOG | Qty: 50 @ $51.00
[DATABASE] ✓ Order fill updated: BUY1 | CumQty: 150 | Remaining: 0 | Status: FILLED
[DATABASE] ✓ Order fill updated: SELL2 | CumQty: 50 | Remaining: 50 | Status: PARTIALLY_FILLED
        │  │
        │  ├─ UI BROADCAST:
        │  │
[TRADE TRACE] 2026-03-31T23:00:15.334Z | UI BROADCAST | 
  ExecID: EXEC_XYZ_002 | Clients: 1
[WS BROADCAST] Execution | ExecID: EXEC_XYZ_002 | Qty: 50 @ $51.00 | Clients: 1
        │  │
        │  └─ LOOP CHECK: BUY1.quantity == 0? YES
        │     → EXIT MATCHING LOOP
        │
        └─ ORDER STILL HAS QUANTITY? NO (50 became 0 after trade 2)
           → Do NOT add to book
        
[ACK SENT] FULLY FILLED | Filled: 150 | Remaining: 0

        [DATABASE]
        Async: INSERT INTO orders (...)
        
[ORDER SERVICE] Order queued for persistence: BUY1

                ↓ ↓ ↓
        [MiniFix (Buyer)]           [MiniFix (Seller 1)]        [MiniFix (Seller 2)]       [Angular Dashboard]
        ✓ ACK received              ✓ Trade Report #1 received   ✓ Trade Report #2 recv'd   ✓ Two trades
        ✓ Status=Filled             ✓ Status=Filled             ✓ Status=PartiallyFilled   ✓ Blotter updated
        ✓ CumQty=150                ✓ CumQty=100                ✓ CumQty=50                ✓ Real-time data
```

---

## Final Database State

### SQL Queries & Results

```sql
SELECT cl_ord_id, quantity, cumulative_qty, (quantity - cumulative_qty) as leaves_qty, 
       status, timestamp 
FROM orders 
WHERE symbol = 'GOOG'
ORDER BY timestamp;

── Output ─────────────────────────────────────────────────────────────────
cl_ord_id │ quantity │ cumulative_qty │ leaves_qty │ status               │ timestamp
─────────┼──────────┼────────────────┼────────────┼──────────────────────┼─────────────────────
SELL1     │ 100      │ 100            │ 0          │ FILLED               │ 2026-03-31 23:00:15
SELL2     │ 100      │ 50             │ 50         │ PARTIALLY_FILLED     │ 2026-03-31 23:00:15
BUY1      │ 150      │ 150            │ 0          │ FILLED               │ 2026-03-31 23:00:15
```

```sql
SELECT exec_id, order_id, symbol, side, exec_qty, exec_price, match_time
FROM executions
WHERE symbol = 'GOOG'
ORDER BY match_time;

── Output ──────────────────────────────────────────────────────────────
exec_id        │ order_id │ symbol │ side │ exec_qty │ exec_price │ match_time
───────────────┼──────────┼────────┼──────┼──────────┼────────────┼─────────────────────
EXEC_ABC_001_BUY  │ ORD_B001 │ GOOG   │ 1    │ 100      │ 50.00      │ 2026-03-31 23:00:15
EXEC_ABC_001_SELL │ ORD_S001 │ GOOG   │ 2    │ 100      │ 50.00      │ 2026-03-31 23:00:15
EXEC_XYZ_002_BUY  │ ORD_B001 │ GOOG   │ 1    │ 50       │ 51.00      │ 2026-03-31 23:00:15
EXEC_XYZ_002_SELL │ ORD_S002 │ GOOG   │ 2    │ 50       │ 51.00      │ 2026-03-31 23:00:15
```

---

## Key Success Criteria ✓

- ✅ CumQty accumulates across multiple trades (100 → 150 for BUY1)
- ✅ OrdStatus correctly transitions (NEW → PARTIALLY_FILLED → FILLED)
- ✅ Execution reports sent to correct sessions (Buyer & Seller separately)
- ✅ Database persists cumulative_qty accurately
- ✅ UI receives real-time trade updates via WebSocket
- ✅ Database audit trail shows both sides of each trade
- ✅ Resting order prices used for trades (not aggressor's limit)
- ✅ All console logging provides clear execution trace

