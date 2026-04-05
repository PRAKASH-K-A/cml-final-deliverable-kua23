# LAB 7: QUICK REFERENCE CARD

## Matching Engine Components at a Glance

### Core Classes

```
OrderBook
├── bids: ConcurrentSkipListMap<Double, List<Order>>  (DESC)
├── asks: ConcurrentSkipListMap<Double, List<Order>>  (ASC)
├── match(Order) → List<Execution>
└── matchOrder(Order, Map, List)  [Core Algorithm]

Order
├── quantity: double
├── price: double
├── side: '1'(BUY) | '2'(SELL)
└── reduceQty(double)  [NEW]

Execution  [NEW]
├── execId: String
├── buyOrderId, sellOrderId: String
├── execQty: double
├── execPrice: double  (RESTING PRICE - CRITICAL!)
└── execTime: Instant

OrderApplication
├── orderBooks: Map<String, OrderBook>  [Registry per symbol]
└── processNewOrder()  [Calls book.match(order)]
```

---

## The Matching Loop (Pseudocode)

```
match(incoming Order):
  if incoming.side == BUY:
    oppositeSide = asks
  else:
    oppositeSide = bids
  
  while incoming.qty > 0 AND oppositeSide not empty:
    bestPrice = oppositeSide.firstKey()  // O(1)
    
    // Price validation
    if (incoming.side == BUY AND incoming.price < bestPrice):
      break  // Can't afford it
    if (incoming.side == SELL AND incoming.price > bestPrice):
      break  // Not acceptable payment
    
    // Execute trade
    resting = oppositeSide[bestPrice][0]  // FIFO at price level
    tradeQty = min(incoming.qty, resting.qty)
    
    CREATE Execution(incoming, resting, tradeQty, bestPrice)
    //                                                 ↑
    //                              RESTING PRICE (NOT incoming.price)
    
    incoming.reduceQty(tradeQty)
    resting.reduceQty(tradeQty)
    
    if resting.qty == 0:
      remove resting from level
      if level.empty():
        remove level from oppositeSide
  
  if incoming.qty > 0:
    addToBook(incoming)
  
  return executions
```

---

## Data Structure Sorting

```
BIDS (Buy Orders):                ASKS (Sell Orders):
$100.00 (HEAD - Best Price)       $50.00  (HEAD - Best Price)
$99.50                            $50.50
$99.00                            $51.00  (TAIL)
(TAIL)                            

reversed order                     natural order
Collections.reverseOrder()         ConcurrentSkipListMap()
```

---

## Trade Price Rule (Critical!)

```
✅ CORRECT
Trade Price = Resting Order's Price

Why:
- Incentivizes providing liquidity early
- Market fairness standard
- Industry convention (NASDAQ, NYSE)

Example:
  Resting Sell @ $50.00
  Incoming Buy @ $55.00
  → Trade @ $50.00 (not $55.00)
  → Buyer gets $5.00/share improvement


❌ WRONG
Trade Price = Incoming Order's Price

Why: Violates fairness, discourages liquidity
```

---

## Test Scenario Output

```
INPUT:
  Sell 100 @ $50.00  (Order A)
  Sell 100 @ $51.00  (Order B)
  Buy 150 @ $52.00   (Order C)

OUTPUT:
  [MATCHING ENGINE] TRADE EXECUTED: SYM | ORDER_C x ORDER_A | 100 @ $50.00
  [MATCHING ENGINE] TRADE EXECUTED: SYM | ORDER_C x ORDER_B | 50 @ $51.00

BOOK AFTER:
  Asks: [$51.00]  (Order B with 50 qty remaining)
  Bids: <empty>
```

---

## Performance Complexity

| Operation | Big-O | Via |
|-----------|-------|-----|
| Best Bid/Ask | O(1) | firstKey() |
| Insert Level | O(log n) | Red-Black tree |
| FIFO at Price | O(1) | LinkedList |
| Full Match | O(m log n) | m levels × log n |

---

## Safety: Synchronization

```java
public synchronized List<Execution> match(Order incoming) {
    // Only ONE thread can execute here per symbol
    // ConcurrentSkipListMap handles structural changes
    // synchronized block makes matching atomic
}
```

**Why:** Prevent race conditions on compound operations
- Check qty → reduce both → update map → must be atomic

---

## Integration Points

```
FIX Client → OrderApplication.processNewOrder()
             ↓
             Get/Create OrderBook[symbol]
             ↓
             OrderBook.match(order)  ← LAB 7
             ↓
             List<Execution> returned
             ↓
             broadcast to UI (WebSocket)
             ↓
             queue for DB (Lab 8)
```

---

## Quick Validation Checklist

- ✅ Bids descending (higher first)
- ✅ Asks ascending (lower first)
- ✅ Match at best price (O(1) lookup)
- ✅ FIFO at each price level
- ✅ Trade price = resting price
- ✅ Quantities decreased via reduceQty()
- ✅ Fully filled orders removed
- ✅ Empty price levels cleaned up
- ✅ Partial fills stay on book
- ✅ Synchronized per symbol
- ✅ List<Execution> returned
- ✅ Broadcasts to UI

---

## Common Implementation Mistakes (Don't Do These!)

```
❌ Using incoming.price for trade price
   → Violates fairness

❌ Not cleaning up empty price levels
   → Memory bloat, slower firstKey()

❌ Not reducing both order quantities
   → Incorrect partial fill tracking

❌ Matching order beyond best price
   → Could execute unfavorable trades

❌ Not synchronizing match() method
   → Race conditions on quantity updates

❌ Removing filled orders mid-loop
   → ConcurrentModificationException

❌ Using HashMap instead of TreeMap
   → No sorted access, can't find best bid/ask
```

---

## Files to Review

| File | What to Look For |
|------|-----------------|
| OrderBook.java | match() and matchOrder() methods |
| Order.java | reduceQty() implementation |
| Execution.java | Trade event creation |
| OrderApplication.java | orderBooks registry, match() call |
| OrderBroadcaster.java | broadcastExecution() method |

---

## Lab 8 Hooks (Ready for Next Lab)

```
// List<Execution> from match() can be:

1. PERSISTED to executions table
   → INSERT exec_id, order_id, symbol, qty, price, match_time

2. SENT BACK as FIX ExecutionReport
   → MsgType=8, ExecID, CumQty, LeavesQty, lastPx

3. STORED with order FK
   → executions.order_id → orders.order_id

All infrastructure is in place!
```

---

## Performance Example

**Scenario:** Buy order for 10,000 shares, crossing 15 Ask price levels

Complexity Analysis:
```
- Get best Ask: O(1)
- Process each level: O(1) for FIFO, O(1) for qty update
- Remove filled order: O(1) from LinkedList head
- Remove price level: O(1) from ConcurrentSkipListMap
- Repeat 15 times: 15 × O(1) + 15 × O(log n)

Total: O(15 log n) = O(log n) essentially constant
Execution time: microseconds, not milliseconds
```

---

## Key Insight

This matching engine is what makes a trading venue an **EXCHANGE** rather than just an **ORDER ROUTER**.

- Order Router: "I'll take your order and find a buyer somewhere"
- Exchange: "Your order will match here with other orders at fair prices immediately"

**Price-Time Priority ensures:**
- Someone always willing to trade (liquidity)
- No favoritism (fair treatment of all orders)
- Efficient price discovery (best prices executed first)
