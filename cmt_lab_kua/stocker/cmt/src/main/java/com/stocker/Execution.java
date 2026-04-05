package com.stocker;

import quickfix.SessionID;
import java.time.Instant;
import java.util.UUID;

/**
 * Execution - Represents a completed trade (matched orders)
 * 
 * When two orders cross (Bid >= Ask), an Execution is generated.
 * This becomes the source of truth for fill events sent to clients
 * and stored in the executions table in PostgreSQL.
 * 
 * Key Insight: The trade price is ALWAYS the resting order's price,
 * NOT the aggressor's price. This is "price improvement" for the resting order.
 */
public class Execution {
    
    private String execId;          // Unique execution/trade ID
    private String buyOrderId;      // Server Order ID of the buyer
    private String sellOrderId;     // Server Order ID of the seller
    private String buyClOrdId;      // Client Order ID of the buyer
    private String sellClOrdId;     // Client Order ID of the seller
    private SessionID buySessionId; // Session of the buyer (for routing execution reports)
    private SessionID sellSessionId;// Session of the seller (for routing execution reports)
    private Order buyOrder;         // Reference to buyer Order object (for access to cumulative qty)
    private Order sellOrder;        // Reference to seller Order object (for access to cumulative qty)
    private String symbol;
    private double execQty;         // Quantity filled in this trade
    private double execPrice;       // Price at which trade executed (resting price)
    private Instant execTime;       // Timestamp of execution
    
    public Execution() {
        this.execId = "EXEC_" + UUID.randomUUID().toString().substring(0, 8);
        this.execTime = Instant.now();
    }
    
    /**
     * Constructor for creating an execution from two matched orders.
     * 
     * @param incomingOrder The aggressive order (market order or crossing order)
     * @param restingOrder  The order already on the book
     * @param tradeQty      The quantity that was matched
     * @param restingPrice  The price at which the trade occurred (resting order's price)
     */
    public Execution(Order incomingOrder, Order restingOrder, double tradeQty, double restingPrice) {
        this.execId = "EXEC_" + UUID.randomUUID().toString().substring(0, 8);
        this.symbol = incomingOrder.getSymbol();
        this.execQty = tradeQty;
        this.execPrice = restingPrice;
        this.execTime = Instant.now();
        
        // Determine who is the buyer and who is the seller
        if (incomingOrder.getSide() == '1') { // Incoming is a BUY
            this.buyOrderId = incomingOrder.getOrderId();
            this.buyClOrdId = incomingOrder.getClOrdID();
            this.buySessionId = incomingOrder.getSessionId();
            this.buyOrder = incomingOrder;
            this.sellOrderId = restingOrder.getOrderId();
            this.sellClOrdId = restingOrder.getClOrdID();
            this.sellSessionId = restingOrder.getSessionId();
            this.sellOrder = restingOrder;
        } else { // Incoming is a SELL
            this.buyOrderId = restingOrder.getOrderId();
            this.buyClOrdId = restingOrder.getClOrdID();
            this.buySessionId = restingOrder.getSessionId();
            this.buyOrder = restingOrder;
            this.sellOrderId = incomingOrder.getOrderId();
            this.sellClOrdId = incomingOrder.getClOrdID();
            this.sellSessionId = incomingOrder.getSessionId();
            this.sellOrder = incomingOrder;
        }
    }
    
    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------
    
    public String getExecId() { return execId; }
    
    public String getBuyOrderId() { return buyOrderId; }
    
    public String getSellOrderId() { return sellOrderId; }
    
    public String getBuyClOrdId() { return buyClOrdId; }
    
    public String getSellClOrdId() { return sellClOrdId; }
    
    public SessionID getBuySessionId() { return buySessionId; }
    
    public SessionID getSellSessionId() { return sellSessionId; }
    
    public Order getBuyOrder() { return buyOrder; }
    
    public Order getSellOrder() { return sellOrder; }
    
    public String getSymbol() { return symbol; }
    
    public double getExecQty() { return execQty; }
    
    public double getExecPrice() { return execPrice; }
    
    public Instant getExecTime() { return execTime; }
    
    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------
    
    public void setExecId(String execId) { this.execId = execId; }
    
    public void setBuyOrderId(String buyOrderId) { this.buyOrderId = buyOrderId; }
    
    public void setSellOrderId(String sellOrderId) { this.sellOrderId = sellOrderId; }
    
    public void setBuyClOrdId(String buyClOrdId) { this.buyClOrdId = buyClOrdId; }
    
    public void setSellClOrdId(String sellClOrdId) { this.sellClOrdId = sellClOrdId; }
    
    public void setBuySessionId(SessionID buySessionId) { this.buySessionId = buySessionId; }
    
    public void setSellSessionId(SessionID sellSessionId) { this.sellSessionId = sellSessionId; }
    
    public void setBuyOrder(Order buyOrder) { this.buyOrder = buyOrder; }
    
    public void setSellOrder(Order sellOrder) { this.sellOrder = sellOrder; }
    
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public void setExecQty(double execQty) { this.execQty = execQty; }
    
    public void setExecPrice(double execPrice) { this.execPrice = execPrice; }
    
    public void setExecTime(Instant execTime) { this.execTime = execTime; }
    
    @Override
    public String toString() {
        return String.format("Execution{execId='%s', symbol='%s', qty=%.0f, price=%.2f, time=%s}",
                execId, symbol, execQty, execPrice, execTime);
    }
}
