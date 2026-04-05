package com.stocker;

import quickfix.SessionID;
import java.util.UUID;

public class Order {
    private String orderId;      // Server-generated unique ID for database
    private String clOrdID;      // Client Order ID (from FIX message)
    private String symbol;
    private char side;
    private double price;
    private double quantity;
    private char orderType;      // Order Type (1=Market, 2=Limit, etc.) - Tag 40
    private SessionID sessionId;  // Session that submitted this order (for routing execution reports)
    private double cumulativeQty; // Track cumulative filled qty across multiple executions

    public Order() {
        this.orderId = "ORD_" + UUID.randomUUID().toString().substring(0, 8);
        this.cumulativeQty = 0;
    }

    public Order(String clOrdID, String symbol, char side, double price, double quantity) {
        this.orderId = "ORD_" + UUID.randomUUID().toString().substring(0, 8);
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.orderType = '2';  // Default to Limit order
        this.cumulativeQty = 0;
    }

    public Order(String clOrdID, String symbol, char side, double price, double quantity, char orderType) {
        this.orderId = "ORD_" + UUID.randomUUID().toString().substring(0, 8);
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.orderType = orderType;
        this.cumulativeQty = 0;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getClOrdID() {
        return clOrdID;
    }

    public void setClOrdID(String clOrdID) {
        this.clOrdID = clOrdID;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public char getSide() {
        return side;
    }

    public void setSide(char side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public char getOrderType() {
        return orderType;
    }

    public void setOrderType(char orderType) {
        this.orderType = orderType;
    }

    /**
     * Reduce the quantity of this order (used during partial fills).
     * This is called by the matching engine as the order is filled.
     * 
     * @param qty Amount to reduce by
     */
    public void reduceQty(double qty) {
        this.quantity = Math.max(0, this.quantity - qty);
        this.cumulativeQty += qty; // Track cumulative fills
    }

    public SessionID getSessionId() {
        return sessionId;
    }

    public void setSessionId(SessionID sessionId) {
        this.sessionId = sessionId;
    }

    public double getCumulativeQty() {
        return cumulativeQty;
    }

    public void setCumulativeQty(double cumulativeQty) {
        this.cumulativeQty = cumulativeQty;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", clOrdID='" + clOrdID + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", orderType=" + orderType +
                '}';
    }
}
