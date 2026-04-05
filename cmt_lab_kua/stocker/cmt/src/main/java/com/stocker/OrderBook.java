package com.stocker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * OrderBook - In-Memory Order Book for a Single Symbol
 * 
 * Holds live, unmatched orders sorted by price-time priority.
 * This is the core data structure for the Matching Engine (Lab 7).
 * 
 * PRICE-TIME PRIORITY:
 * - Best BID = Highest price a buyer is willing to pay  (sorted DESC)
 * - Best ASK = Lowest price a seller is willing to accept (sorted ASC)
 * 
 * Uses ConcurrentSkipListMap for:
 *   1. Thread-safe concurrent access
 *   2. Automatic price-level sorting (O(log n) insert/remove)
 *   3. O(1) best bid/ask lookup via firstKey()
 */
public class OrderBook {
    
    private final String symbol;
    
    // Bids: Sorted HIGH to LOW (Descending) - Best Bid = firstKey()
    private final ConcurrentSkipListMap<Double, List<Order>> bids =
            new ConcurrentSkipListMap<>(Collections.reverseOrder());
    
    // Asks: Sorted LOW to HIGH (Ascending) - Best Ask = firstKey()
    private final ConcurrentSkipListMap<Double, List<Order>> asks =
            new ConcurrentSkipListMap<>();
    
    public OrderBook(String symbol) {
        this.symbol = symbol;
    }
    
    // -------------------------------------------------------------------------
    // CORE MATCHING ENGINE (Lab 7)
    // -------------------------------------------------------------------------
    
    /**
     * Match an incoming order against the resting order book.
     * Returns a list of Execution objects (trades that occurred).
     * 
     * Algorithm:
     * 1. If Buy order: match against Ask side (sellers)
     * 2. If Sell order: match against Bid side (buyers)
     * 3. For each match, create an Execution at the resting price
     * 4. Remove filled orders from book
     * 5. Add remainder to book if not fully filled
     * 
     * @param incoming The newly arrived order to be matched
     * @return List of Execution objects (trades)
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
    
    /**
     * Core matching loop. Continuously tries to execute the incoming order
     * against the opposite side of the book, price by price.
     * 
     * @param incoming The incoming order
     * @param oppositeSide The NavigableMap of the opposite side (Bids or Asks)
     * @param executions List to accumulate trade records
     */
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
                    "[TRADE MATCH] %s | Aggressor: %s | Resting: %s | Qty: %.0f | Price: $%.2f",
                    symbol,
                    incoming.getClOrdID(),
                    resting.getClOrdID(),
                    tradeQty,
                    bestPrice
            ));
            
            // ===== UPDATE ORDER QUANTITIES =====
            incoming.reduceQty(tradeQty);
            resting.reduceQty(tradeQty);
            
            // ===== CLEANUP: Remove fully filled orders =====
            if (resting.getQuantity() == 0) {
                ordersAtLevel.remove(0); // Remove the filled order from the list
                
                // If that was the last order at that price level, remove the price level
                if (ordersAtLevel.isEmpty()) {
                    oppositeSide.remove(bestPrice);
                }
            }
        }
    }
    
    /**
     * Add an order to the book at its price level.
     * Orders at the same price are queued FIFO (time priority).
     */
    public void addToBook(Order order) {
        NavigableMap<Double, List<Order>> side =
                order.getSide() == '1' ? bids : asks;
        
        // ComputeIfAbsent creates the list if this is the first order at this price
        side.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
        
        System.out.println(String.format("[ORDERBOOK] %s | Added %s %s @ %.2f | Bids: %d levels | Asks: %d levels",
                symbol,
                order.getSide() == '1' ? "BUY" : "SELL",
                order.getClOrdID(),
                order.getPrice(),
                bids.size(),
                asks.size()));
    }
    
    // -------------------------------------------------------------------------
    // Order Book Operations (cancel, remove, etc.)
    // -------------------------------------------------------------------------
    
    /**
     * Remove an order from the book (cancel or fill).
     */
    public void removeOrder(Order order) {
        NavigableMap<Double, List<Order>> side =
                order.getSide() == '1' ? bids : asks;
        
        List<Order> priceLevel = side.get(order.getPrice());
        if (priceLevel != null) {
            priceLevel.remove(order);
            if (priceLevel.isEmpty()) {
                side.remove(order.getPrice()); // Clean up empty price level
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // Market Data Accessors
    // -------------------------------------------------------------------------
    
    /**
     * Best Bid: highest price a buyer is willing to pay.
     * Returns null if no bids exist.
     */
    public Double getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }
    
    /**
     * Best Ask: lowest price a seller is willing to accept.
     * Returns null if no asks exist.
     */
    public Double getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }
    
    /**
     * Spread: difference between best ask and best bid.
     * Returns null if one side is empty.
     */
    public Double getSpread() {
        Double bid = getBestBid();
        Double ask = getBestAsk();
        return (bid != null && ask != null) ? ask - bid : null;
    }
    
    /**
     * Check if a match is possible: bid >= ask (crossed book).
     */
    public boolean hasMatchableOrders() {
        Double bid = getBestBid();
        Double ask = getBestAsk();
        return bid != null && ask != null && bid >= ask;
    }
    
    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------
    
    public String getSymbol() { return symbol; }
    
    public ConcurrentSkipListMap<Double, List<Order>> getBids() { return bids; }
    
    public ConcurrentSkipListMap<Double, List<Order>> getAsks() { return asks; }
    
    public int getTotalBidOrders() {
        return bids.values().stream().mapToInt(List::size).sum();
    }
    
    public int getTotalAskOrders() {
        return asks.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * Print a human-readable snapshot of the order book.
     * Used for debugging and display.
     */
    public void printSnapshot() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" ORDER BOOK: " + symbol);
        System.out.println("=".repeat(50));
        
        System.out.println(" ASKS (Sell Orders):");
        if (asks.isEmpty()) {
            System.out.println("   <empty>");
        } else {
            // Print asks in reverse (highest first for readability)
            new ConcurrentSkipListMap<>(Collections.reverseOrder()).putAll(asks);
            for (Map.Entry<Double, List<Order>> level : asks.entrySet()) {
                System.out.printf("   $%8.2f  | %d order(s)%n", level.getKey(), level.getValue().size());
            }
        }
        
        System.out.println(" --- Spread: " + (getSpread() != null ? String.format("$%.2f", getSpread()) : "N/A") + " ---");
        
        System.out.println(" BIDS (Buy Orders):");
        if (bids.isEmpty()) {
            System.out.println("   <empty>");
        } else {
            for (Map.Entry<Double, List<Order>> level : bids.entrySet()) {
                System.out.printf("   $%8.2f  | %d order(s)%n", level.getKey(), level.getValue().size());
            }
        }
        System.out.println("=".repeat(50) + "\n");
    }
}
