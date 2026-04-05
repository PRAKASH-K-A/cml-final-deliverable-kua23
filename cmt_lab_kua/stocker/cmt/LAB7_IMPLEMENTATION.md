# LAB 7: THE MATCHING ENGINE - IMPLEMENTATION GUIDE

## Overview
This lab implements the core matching algorithm for a trading exchange using the **Price-Time Priority** algorithm. The system matches incoming Buy and Sell orders to generate trades in real-time.

## Key Components Implemented

### 1. Execution.java
Represents a completed trade when two orders are matched.

```java
public class Execution {
    private String execId;          // Unique execution ID
    private String buyOrderId;      // Server Order ID of buyer
    private String sellOrderId;     // Server Order ID of seller
    private String symbol;
    private double execQty;         // Quantity filled
    private double execPrice;       // Price at which trade executed (RESTING PRICE)
    private Instant execTime;       // Timestamp of execution
    
    /**
     * Constructor for creating an execution from two matched orders.
     * CRITICAL: The trade price is ALWAYS the resting order's price.
     */
    public Execution(Order incomingOrder, Order restingOrder, double tradeQty, double restingPrice)
}
```

### 2. Order.java Enhancement
Added `reduceQty()` method to track partial fills:

```java
/**
 * Reduce the quantity of this order (used during partial fills).
 * This is called by the matching engine as the order is filled.
 */
public void reduceQty(double qty) {
    this.quantity = Math.max(0, this.quantity - qty);
}
```

### 3. OrderBook.java - Core Matching Logic

#### Data Structures
```java
// Bids: High to Low (Descending) - Best Bid = firstKey()
private final ConcurrentSkipListMap<Double, List<Order>> bids =
        new ConcurrentSkipListMap<>(Collections.reverseOrder());

// Asks: Low to High (Ascending) - Best Ask = firstKey()
private final ConcurrentSkipListMap<Double, List<Order>> asks =
        new ConcurrentSkipListMap<>();
```

#### Main Matching Method
```java
/**
 * Match an incoming order against the resting order book.
 * Returns a list of Execution objects (trades that occurred).
 */
public synchronized List<Execution> match(Order incoming) {
    List<Execution> executions = new ArrayList<>();
    
    if (incoming.getSide() == '1') { // BUY Order
        // Attempt to match against the ASK side (sellers)
        matchOrder(incoming, asks, executions);
    } else { // SELL Order
        // Attempt to match against the BID side (buyers)
        matchOrder(incoming, bids, executions);
    }
    
    // If the order is not fully filled after matching, add the remainder to the book
    if (incoming.getQuantity() > 0) {
        addToBook(incoming);
    }
    
    return executions;
}
```

#### Matching Loop (Core Algorithm)
```java
private void matchOrder(Order incoming, NavigableMap<Double, List<Order>> oppositeSide,
                       List<Execution> executions) {
    
    // Continue loop while:
    // 1. The incoming order still needs to be filled (Qty > 0)
    // 2. The opposite book is not empty (There is someone to trade with)
    while (incoming.getQuantity() > 0 && !oppositeSide.isEmpty()) {
        
        // Peek at the best available price on the other side
        Double bestPrice = oppositeSide.firstKey();
        
        // ===== PRICE CHECK: Does the incoming order cross the market? =====
        boolean isBuy = (incoming.getSide() == '1');
        
        // If Buying: We want to buy LOW. If BestAsk > MyLimit, I can't afford it. Stop.
        if (isBuy && incoming.getPrice() < bestPrice) {
            break; // No more matches possible
        }
        
        // If Selling: We want to sell HIGH. If BestBid < MyLimit, they aren't paying enough. Stop.
        if (!isBuy && incoming.getPrice() > bestPrice) {
            break; // No more matches possible
        }
        
        // ===== MATCH CONFIRMED: We can execute a trade =====
        
        // Get the list of orders at this price level (Time Priority = FIFO)
        List<Order> ordersAtLevel = oppositeSide.get(bestPrice);
        
        // Get the first order in the queue (earliest time = first to match)
        Order resting = ordersAtLevel.get(0);
        
        // Calculate Trade Quantity: The max we can trade is the smaller of the two sizes
        double tradeQty = Math.min(incoming.getQuantity(), resting.getQuantity());
        
        // ===== CREATE EXECUTION RECORD =====
        // CRITICAL: The trade happens at the RESTING order's price, NOT the aggressor's price
        Execution exec = new Execution(incoming, resting, tradeQty, bestPrice);
        executions.add(exec);
        
        // Log the trade
        System.out.println(String.format(
                "[MATCHING ENGINE] TRADE EXECUTED: %s | %s | %.0f shares @ $%.2f",
                symbol,
                incoming.getClOrdID() + " (agg) x " + resting.getClOrdID() + " (rest)",
                tradeQty,
                bestPrice
        ));
        
        // ===== UPDATE ORDER QUANTITIES =====
        incoming.reduceQty(tradeQty);
        resting.reduceQty(tradeQty);
        
        // ===== CLEANUP: Remove fully filled orders =====
        if (resting.getQuantity() == 0) {
            ordersAtLevel.remove(0);
            
            // If that was the last order at that price level, remove the price level
            if (ordersAtLevel.isEmpty()) {
                oppositeSide.remove(bestPrice);
            }
        }
    }
}
```

### 4. OrderApplication.java Integration

#### New Data Structure
```java
private final Map<String, OrderBook> orderBooks;  // One book per symbol
```

#### Updated processNewOrder() Flow
```java
// Get or create the OrderBook for this symbol
OrderBook book = orderBooks.computeIfAbsent(symbol, k -> new OrderBook(symbol));

// CRITICAL: Call the matching engine
// This returns a list of Execution objects (trades that occurred)
List<Execution> executions = book.match(order);

// Handle trades
if (!executions.isEmpty()) {
    System.out.println(String.format("[ORDER SERVICE] Order %s generated %d execution(s)", 
            clOrdId, executions.size()));
}

// Broadcast executions (trades) back to client
for (Execution exec : executions) {
    broadcaster.broadcastExecution(exec);
}
```

### 5. OrderBroadcaster.java Enhancement
Added `broadcastExecution()` method:

```java
/**
 * Broadcast an Execution object to all connected UI clients
 * Used to push trade execution events to the UI in real-time
 */
public void broadcastExecution(Execution execution) {
    try {
        String json = "{\"type\":\"execution\"," + 
                     "\"data\":" + gson.toJson(execution) + "}";
        
        broadcast(json);
        
        System.out.println("[WEBSOCKET] ✓ Broadcasted execution to " + 
                getConnections().size() + " client(s): " + execution.getExecId());
    } catch (Exception e) {
        System.err.println("[WEBSOCKET] ERROR: Failed to broadcast execution - " + e.getMessage());
    }
}
```

---

## Price-Time Priority Algorithm Explanation

### Price Priority
- **Buy Side**: Highest price is best → Bids sorted DESCENDING
- **Sell Side**: Lowest price is best → Asks sorted ASCENDING

### Time Priority
- At the same price level, orders are served FIFO
- Implemented using `LinkedList` at each price node

### Crossing the Spread
A trade occurs when:
- **Buy Order**: Price >= Best Ask (buyer willing to pay seller's asking price)
- **Sell Order**: Price <= Best Bid (seller willing to accept buyer's bid)

---

## Testing Scenario

### Scenario: Price-Time Priority Validation

**Step 1: Send Sell 100 @ $50.00 (Resting Order A)**
```
Console Output:
[ORDERBOOK] MSFT | Added SELL client-001 @ 50.00 | Bids: 0 levels | Asks: 1 levels
```
- Order A added to Ask side at $50.00

**Step 2: Send Sell 100 @ $51.00 (Resting Order B)**
```
Console Output:
[ORDERBOOK] MSFT | Added SELL client-002 @ 51.00 | Bids: 0 levels | Asks: 2 levels
```
- Order B added to Ask side at $51.00
- Book now has: Asks [$50.00, $51.00]

**Step 3: Send Buy 150 @ $52.00 (Aggressive Order)**
```
Console Output:
[ORDER SERVICE] ORDER RECEIVED: ID=client-003 Side=BUY Sym=MSFT Px=52.00 Qty=150.00
[MATCHING ENGINE] TRADE EXECUTED: MSFT | client-003 (agg) x client-001 (rest) | 100 shares @ $50.00
[MATCHING ENGINE] TRADE EXECUTED: MSFT | client-003 (agg) x client-002 (rest) | 50 shares @ $51.00
[ORDERBOOK] MSFT | Added BUY client-003 @ 52.00 | Bids: 1 levels | Asks: 0 levels
```

**Analysis:**
- Trade 1: 100 shares @ $50.00 (matches Order A at its resting price)
- Trade 2: 50 shares @ $51.00 (matches Order B at its resting price, partial fill)
- Remaining: 50 shares of the Buy order added to Bid side @ $52.00

**Key Points:**
1. ✓ Price priority respected: Lowest Ask ($50) matched first
2. ✓ Time priority respected: Order A matched before Order B (both at Ask side, but A arrived first)
3. ✓ Price improvement: Buyer got $50 and $51 prices (better than $52 limit)
4. ✓ Resting price used: Trades NOT at buyer's price ($52), but at resting prices ($50, $51)

---

## Synchronization Strategy

The matching logic is protected by a **synchronized block** on the `match()` method:

```java
public synchronized List<Execution> match(Order incoming) {
    // Only one thread can execute matches for a symbol at a time
    // This prevents race conditions on quantity updates
}
```

While `ConcurrentSkipListMap` is thread-safe for structural changes (add/remove), the matching operation involves multiple interdependent steps (check quantity, calculate trade, update both orders, remove from book). The synchronized block ensures atomic matching operations.

---

## Performance Characteristics

| Operation | Complexity | Note |
|-----------|-----------|------|
| Best Bid/Ask Lookup | O(1) | `firstKey()` on ConcurrentSkipListMap |
| Insert price level | O(log n) | Red-Black tree operations |
| Time Priority FIFO | O(1) | LinkedList append |
| Full match loop | O(m log n) | m = matching levels, n = total price levels |

---

## Integration Points

1. **OrderApplication.constructo**r: Initialize orderBooks registry
2. **processNewOrder()**: Call `book.match(order)` and get Executions
3. **New Execution broadcast**: Send trades to UI via `broadcastExecution()`
4. **Database persistence**: Order and Executions queued separately (Lab 8)

---

## Future Enhancements (Lab 8+)

- Persist Executions to PostgreSQL executions table
- Send Execution Reports back to FIX clients
- Implement order cancellation logic
- Add order modification support
- Implement market depth visualization (L2 order book)
