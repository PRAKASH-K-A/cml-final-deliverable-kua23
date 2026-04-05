package com.stocker;

/**
 * OrderDTO - Data Transfer Object for JSON Serialization
 * 
 * Used to properly serialize Order objects to JSON for the frontend.
 * This DTO ensures proper field names and only includes JSON-serializable fields.
 * 
 * Solves the issue where SessionID objects don't serialize to JSON properly.
 * Also provides TypeScript-compatible field names (cumQty instead of cumulativeQty).
 */
public class OrderDTO {
    // Order Identifiers
    public String ordID;      // Matches FIX Tag 37 (Server-assigned Order ID)
    public String clOrdID;    // Matches FIX Tag 11 (Client Order ID)
    
    // Security & Side
    public String symbol;
    public String side;       // "1" for BUY, "2" for SELL
    
    // Order Type and Price
    public String ordType;    // "1"=Market, "2"=Limit
    public double price;
    public double quantity;
    
    // Execution Report Fields
    public String ordStatus;  // "NEW", "PARTIALLY_FILLED", "FILLED"
    public String execType;   // "NEW", "PARTIAL_FILL", "FILL", "TRADE"
    public double cumQty;     // Cumulative Quantity filled (matches TypeScript interface)
    public double leavesQty;  // Remaining Quantity
    public double lastQty;    // Last execution quantity
    public double lastPx;     // Last execution price
    public double avgPx;      // Average execution price
    
    /**
     * Constructor from Order object
     * Safely converts Order → DTO, excluding non-JSON-serializable fields like SessionID
     */
    public OrderDTO(Order order) {
        this.ordID = order.getOrderId();
        this.clOrdID = order.getClOrdID();
        this.symbol = order.getSymbol();
        this.side = String.valueOf(order.getSide());  // Convert char to String
        this.ordType = String.valueOf(order.getOrderType());
        this.price = order.getPrice();
        this.quantity = order.getQuantity();
        
        // Calculate leaves quantity and status
        this.cumQty = order.getCumulativeQty();
        this.leavesQty = Math.max(0, order.getQuantity() - this.cumQty);
        
        // Determine order status based on fills
        if (this.leavesQty == 0 && this.cumQty > 0) {
            this.ordStatus = "FILLED";
            this.execType = "FILL";
        } else if (this.cumQty > 0) {
            this.ordStatus = "PARTIALLY_FILLED";
            this.execType = "PARTIAL_FILL";
        } else {
            this.ordStatus = "NEW";
            this.execType = "NEW";
        }
        
        // Set defaults for fields not yet in Order
        this.lastQty = 0;
        this.lastPx = 0;
        this.avgPx = 0;
    }
    
    /**
     * Constructor from Order and Execution (for trades)
     */
    public OrderDTO(Order order, Execution execution, boolean isBuyer) {
        this(order);
        
        // Update status based on execution
        this.execType = "TRADE";
        if (order.getQuantity() == 0) {
            this.ordStatus = "FILLED";
        } else if (order.getCumulativeQty() > 0) {
            this.ordStatus = "PARTIALLY_FILLED";
        } else {
            this.ordStatus = "NEW";
        }
        
        // Update execution fields
        this.lastQty = execution.getExecQty();
        this.lastPx = execution.getExecPrice();
        this.avgPx = execution.getExecPrice();
    }
    
    @Override
    public String toString() {
        return "OrderDTO{" +
                "clOrdID='" + clOrdID + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", quantity=" + quantity +
                ", cumQty=" + cumQty +
                ", leavesQty=" + leavesQty +
                ", price=" + price +
                ", ordStatus='" + ordStatus + '\'' +
                '}';
    }
}
