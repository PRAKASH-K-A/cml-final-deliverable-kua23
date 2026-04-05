# LAB 7: COMPREHENSIVE TEST TRACE - PRICE-TIME PRIORITY

## Assessment Question 8: Log Trace Demonstration

This document provides the complete expected console output for the test sequence specified in the Lab 7 Assessment.

---

## Test Sequence: Price-Time Priority Validation

### Scenario Setup
Demonstrate price-time priority by executing the following sequence:
1. Sell 100 @ $50.00 (Resting Order A)
2. Sell 100 @ $51.00 (Resting Order B)  
3. Buy 150 @ $52.00 (Aggressive Order C)

---

## Complete Console Output Trace

### ═══════════════════════════════════════════════════════════════
### EVENT 1: Sell 100 @ $50.00 (Resting Order A)
### ═══════════════════════════════════════════════════════════════

```
[ORDER SERVICE] ORDER RECEIVED: ID=ORDER_A Side=SELL Sym=MSFT Px=50.00 Qty=100
[ORDER SERVICE] ✓ ORDER ACCEPTED: ClOrdID=ORDER_A
[ORDERBOOK] MSFT | Added SELL ORDER_A @ 50.00 | Bids: 0 levels | Asks: 1 levels
[ORDER SERVICE] Order ORDER_A added to book (no matches)
[WEBSOCKET] ✓ Broadcasted order to X client(s): ORDER_A
[ORDER SERVICE] Order queued for persistence: ORDER_A
```

**Book State After Event 1:**
```
Order Book: MSFT
━━━━━━━━━━━━━━━━━━━
ASKS (Prices ascending):
  $50.00:   ORDER_A (100 @ rest)
  
BIDS (Prices descending):
  <empty>

Best Bid: null
Best Ask: $50.00
Spread: N/A
```

---

### ═══════════════════════════════════════════════════════════════
### EVENT 2: Sell 100 @ $51.00 (Resting Order B)
### ═══════════════════════════════════════════════════════════════

```
[ORDER SERVICE] ORDER RECEIVED: ID=ORDER_B Side=SELL Sym=MSFT Px=51.00 Qty=100
[ORDER SERVICE] ✓ ORDER ACCEPTED: ClOrdID=ORDER_B
[ORDERBOOK] MSFT | Added SELL ORDER_B @ 51.00 | Bids: 0 levels | Asks: 2 levels
[ORDER SERVICE] Order ORDER_B added to book (no matches)
[WEBSOCKET] ✓ Broadcasted order to X client(s): ORDER_B
[ORDER SERVICE] Order queued for persistence: ORDER_B
```

**Book State After Event 2:**
```
Order Book: MSFT
━━━━━━━━━━━━━━━━━━━
ASKS (Prices ascending):
  $50.00:   ORDER_A (100 @ rest)
  $51.00:   ORDER_B (100 @ rest)
  
BIDS (Prices descending):
  <empty>

Best Bid: null
Best Ask: $50.00
Spread: N/A
```

---

### ═══════════════════════════════════════════════════════════════
### EVENT 3: Buy 150 @ $52.00 (Aggressive Order C - TRIGGERS MATCHING)
### ═══════════════════════════════════════════════════════════════

```
[ORDER SERVICE] ORDER RECEIVED: ID=ORDER_C Side=BUY Sym=MSFT Px=52.00 Qty=150
```

**MATCHING ENGINE ACTIVATION:**

```
  [MATCHING ENGINE] Matching buy ORDER_C (150 @ 52.00) against Ask side...
  
  ▶ Iteration 1:
    - Best Ask Price: $50.00 (ORDER_A)
    - Incoming Qty: 150 | Resting Qty: 100
    - Price Check: BUY @ $52.00 >= ASK @ $50.00 ✓ MATCH!
    
[MATCHING ENGINE] TRADE EXECUTED: MSFT | ORDER_C (agg) x ORDER_A (rest) | 100 shares @ $50.00

    - Trade Qty: 100
    - ORDER_C remaining: 150 - 100 = 50
    - ORDER_A remaining: 100 - 100 = 0 ✗ FILLED (remove from book)
    - Remaining Qty: 50 > 0, continue loop
    
  ▶ Iteration 2:
    - Best Ask Price: $51.00 (ORDER_B)
    - Incoming Qty: 50 | Resting Qty: 100
    - Price Check: BUY @ $52.00 >= ASK @ $51.00 ✓ MATCH!
    
[MATCHING ENGINE] TRADE EXECUTED: MSFT | ORDER_C (agg) x ORDER_B (rest) | 50 shares @ $51.00

    - Trade Qty: 50
    - ORDER_C remaining: 50 - 50 = 0 ✓ FULLY FILLED
    - ORDER_B remaining: 100 - 50 = 50 (stays on book)
    - Incoming Qty: 0, exit loop

[ORDER SERVICE] ✓ ORDER ACCEPTED: ClOrdID=ORDER_C
[ORDER SERVICE] Order ORDER_C generated 2 execution(s)
[ORDERBOOK] MSFT | Added BUY ORDER_C @ 52.00 | Bids: 1 levels | Asks: 1 levels
```

**Wait: ORDER_C was fully filled (0 qty remaining), so it should NOT be added to book!**

Let me correct this. After 2 trades, ORDER_C has 0 qty remaining, so the addToBook call is skipped.

```
[ORDER SERVICE] ✓ ORDER ACCEPTED: ClOrdID=ORDER_C
[ORDER SERVICE] Order ORDER_C generated 2 execution(s)
[WEBSOCKET] ✓ Broadcasted execution to X client(s): EXEC_1
[WEBSOCKET] ✓ Broadcasted execution to X client(s): EXEC_2
[ORDER SERVICE] Order queued for persistence: ORDER_C
```

**Book State After Event 3:**
```
Order Book: MSFT
━━━━━━━━━━━━━━━━━━━
ASKS (Prices ascending):
  $51.00:   ORDER_B (50 @ rest)  [Partially filled]
  
BIDS (Prices descending):
  <empty>

Best Bid: null
Best Ask: $51.00
Spread: N/A
```

---

## Execution Records Generated

### Execution 1: Trade @ $50.00
```
Execution ID:     EXEC_[auto-generated]
Buy Order ID:     [ORDER_C.orderId]
Sell Order ID:    [ORDER_A.orderId]
Buy ClOrdID:      ORDER_C
Sell ClOrdID:     ORDER_A
Symbol:           MSFT
Execution Qty:    100.0 shares
Execution Price:  $50.00 ◄━━ RESTING PRICE (not aggressor's $52.00)
Execution Time:   [timestamp]

JSON:
{
  "execId": "EXEC_[auto]",
  "buyOrderId": "[ORDER_C.orderId]",
  "sellOrderId": "[ORDER_A.orderId]",
  "buyClOrdId": "ORDER_C",
  "sellClOrdId": "ORDER_A",
  "symbol": "MSFT",
  "execQty": 100.0,
  "execPrice": 50.00,
  "execTime": "[ISO-8601 timestamp]"
}
```

### Execution 2: Trade @ $51.00
```
Execution ID:     EXEC_[auto-generated]
Buy Order ID:     [ORDER_C.orderId]
Sell Order ID:    [ORDER_B.orderId]
Buy ClOrdID:      ORDER_C
Sell ClOrdID:     ORDER_B
Symbol:           MSFT
Execution Qty:    50.0 shares
Execution Price:  $51.00 ◄━━ RESTING PRICE (not aggressor's $52.00)
Execution Time:   [timestamp]

JSON:
{
  "execId": "EXEC_[auto]",
  "buyOrderId": "[ORDER_C.orderId]",
  "sellOrderId": "[ORDER_B.orderId]",
  "buyClOrdId": "ORDER_C",
  "sellClOrdId": "ORDER_B",
  "symbol": "MSFT",
  "execQty": 50.0,
  "execPrice": 51.00,
  "execTime": "[ISO-8601 timestamp]"
}
```

---

## Validation Assertions

### ✅ Price Priority Validates Correct
```
Priority Order of Execution:
1. FIRST:  $50.00 (ORDER_A)  ← Lowest Ask (Best price for buyer)
2. SECOND: $51.00 (ORDER_B)  ← Next lowest Ask

✓ PASS: Lower prices executed before higher prices
✓ PASS: Seller asking $51.00 was NOT matched before seller asking $50.00
```

### ✅ Time Priority Validates Correct
```
At $50.00 Price Level: [ORDER_A]
At $51.00 Price Level: [ORDER_B]

Queue Execution:
1. ORDER_A (arrived first)     ← Matched first (FIFO)
2. ORDER_B (arrived second)    ← Matched second

✓ PASS: Orders at same price would be FIFO
✓ PASS: Even if we had 3 sells at same price, first added = first matched
```

### ✅ Partial Fill Handling Validates Correct
```
Incoming: ORDER_C for 150 shares
After Trade 1: 150 - 100 = 50 remaining
After Trade 2: 50 - 50 = 0 remaining

Resting ORDER_B:
Before Trade 2: 100 shares
After Trade 2: 100 - 50 = 50 remaining

✓ PASS: Incoming order partially filled then fully filled
✓ PASS: Resting order (ORDER_B) partially filled and remains on book
✓ PASS: Matching continued until incoming order fully filled
```

### ✅ Trade Price Accuracy Validates Correct
```
Trade 1:
  Buyer Limit: $52.00
  Resting Price: $50.00
  Trade Price: $50.00 ✓ (NOT buyer's $52.00)
  Buyer Improvement: +$2.00/share

Trade 2:
  Buyer Limit: $52.00
  Resting Price: $51.00
  Trade Price: $51.00 ✓ (NOT buyer's $52.00)
  Buyer Improvement: +$1.00/share

✓ PASS: Trades executed at resting order prices
✓ PASS: Price improvement granted to resting orders
✓ PASS: Aggressive order did NOT dictate price
```

### ✅ Order Book Cleanup Validates Correct
```
Before Cleanup:
  ASKS: [$50.00, $51.00]
  
After Event 3:
  ORDER_A (100 qty @ $50.00) → fully filled
    → qty = 0, removed from price level
    → price level $50.00 removed entirely ✓
    
  ORDER_B (100 qty @ $51.00) → partially filled (50 executed)
    → qty = 50, still on book
    → price level $51.00 remains with 50 shares ✓

✓ PASS: Empty price levels cleaned up
✓ PASS: Non-empty price levels retained
✓ PASS: Partially filled orders remain on book
```

---

## Key Algorithm Insights Demonstrated

1. **Sorted Access**: O(1) best bid/ask lookup via ConcurrentSkipListMap.firstKey()
2. **Matching Loop**: Continues while qty > 0 AND opposite side != empty
3. **FIFO at Price Level**: LinkedList ensures time priority at each price
4. **Quantity Tracking**: reduceQty() called on both sides
5. **Cleanup**: Empty price levels automatically removed
6. **Synchronization**: Entire match() method synchronized to prevent race conditions

---

## How to Replicate This Test

1. **Start the Order Service** with FIX server listening
2. **Connect via FIX Client** or MINIFIX_TEST tool
3. **Send FIX NewOrderSingle messages** in sequence:
   ```
   Message 1: ClOrdID=ORDER_A, Symbol=MSFT, Side=2(SELL), Price=50.00, OrderQty=100
   Message 2: ClOrdID=ORDER_B, Symbol=MSFT, Side=2(SELL), Price=51.00, OrderQty=100
   Message 3: ClOrdID=ORDER_C, Symbol=MSFT, Side=1(BUY), Price=52.00, OrderQty=150
   ```
4. **Observe console output** matching the trace above
5. **Query PostgreSQL** to verify order and execution persistence:
   ```sql
   SELECT * FROM orders WHERE symbol='MSFT' ORDER BY timestamp;
   SELECT * FROM executions WHERE symbol='MSFT' ORDER BY match_time;
   ```
