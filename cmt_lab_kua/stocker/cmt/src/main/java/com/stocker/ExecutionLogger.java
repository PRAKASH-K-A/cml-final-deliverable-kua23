package com.stocker;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ExecutionLogger - Comprehensive Trade Execution Tracing
 * 
 * This class provides detailed logging of the entire execution lifecycle:
 * 1. Order accepted and added to book
 * 2. Trade matched
 * 3. Execution reports generated
 * 4. Trade persisted to database
 * 5. Trade broadcast to UI clients
 * 
 * Used for debugging, audit trail, and performance analysis.
 */
public class ExecutionLogger {
    
    private static final AtomicLong tradeCounter = new AtomicLong(0);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
    
    /**
     * Log a new order entering the system
     */
    public static void logOrderAccepted(Order order) {
        System.out.printf("[TRADE TRACE] %s | ORDER ACCEPTED | ClOrdID: %s | Symbol: %s | Side: %s | Qty: %.0f @ $%.2f%n",
            timestamp(),
            order.getClOrdID(),
            order.getSymbol(),
            (order.getSide() == '1' ? "BUY " : "SELL"),
            order.getQuantity(),
            order.getPrice());
    }
    
    /**
     * Log an order being added to the book
     */
    public static void logOrderBooked(Order order) {
        System.out.printf("[TRADE TRACE] %s | ORDER BOOKED | ClOrdID: %s | Symbol: %s | Price Level: $%.2f | Qty: %.0f%n",
            timestamp(),
            order.getClOrdID(),
            order.getSymbol(),
            order.getPrice(),
            order.getQuantity());
    }
    
    /**
     * Log a trade execution match
     */
    public static void logTradeMatched(Execution execution) {
        long tradeNum = tradeCounter.incrementAndGet();
        System.out.printf("[TRADE TRACE] %s | TRADE #%d MATCHED | Buy: %s | Sell: %s | Symbol: %s | Qty: %.0f @ $%.2f%n",
            timestamp(),
            tradeNum,
            execution.getBuyClOrdId(),
            execution.getSellClOrdId(),
            execution.getSymbol(),
            execution.getExecQty(),
            execution.getExecPrice());
    }
    
    /**
     * Log execution reports being sent
     */
    public static void logExecutionReportSent(String side, String clOrdId, double cumQty, 
                                             double leavesQty, String status, double execPrice) {
        System.out.printf("[TRADE TRACE] %s | EXEC REPORT | %s | ClOrdID: %s | CumQty: %.0f | Leaves: %.0f | Status: %s | Price: $%.2f%n",
            timestamp(),
            side,
            clOrdId,
            cumQty,
            leavesQty,
            status,
            execPrice);
    }
    
    /**
     * Log execution being persisted to database
     */
    public static void logExecutionPersisted(Execution execution) {
        System.out.printf("[TRADE TRACE] %s | DB PERSIST | ExecID: %s | Symbol: %s | Qty: %.0f @ $%.2f%n",
            timestamp(),
            execution.getExecId(),
            execution.getSymbol(),
            execution.getExecQty(),
            execution.getExecPrice());
    }
    
    /**
     * Log execution being broadcast to UI
     */
    public static void logExecutionBroadcast(Execution execution, int clientCount) {
        System.out.printf("[TRADE TRACE] %s | UI BROADCAST | ExecID: %s | Clients: %d%n",
            timestamp(),
            execution.getExecId(),
            clientCount);
    }
    
    /**
     * Log summary of execution flow
     */
    public static void logExecutionSummary(Execution execution, Order buyOrder, Order sellOrder) {
        System.out.println("\n" + "=".repeat(100));
        System.out.printf("EXECUTION COMPLETE | ExecID: %s | Trade #%d%n",
            execution.getExecId(),
            tradeCounter.get());
        System.out.printf("  Buy Order  : %s | CumQty: %.0f | Leaves: %.0f | Status: %s%n",
            buyOrder.getClOrdID(),
            buyOrder.getCumulativeQty(),
            buyOrder.getQuantity(),
            getStatus(buyOrder));
        System.out.printf("  Sell Order : %s | CumQty: %.0f | Leaves: %.0f | Status: %s%n",
            sellOrder.getClOrdID(),
            sellOrder.getCumulativeQty(),
            sellOrder.getQuantity(),
            getStatus(sellOrder));
        System.out.printf("  Trade      : %.0f shares @ $%.2f%n", 
            execution.getExecQty(), 
            execution.getExecPrice());
        System.out.println("=".repeat(100) + "\n");
    }
    
    /**
     * Helper to get status string
     */
    private static String getStatus(Order order) {
        if (order.getQuantity() == 0) {
            return "FILLED";
        } else if (order.getCumulativeQty() > 0) {
            return "PARTIALLY_FILLED";
        } else {
            return "NEW";
        }
    }
    
    /**
     * Get timestamp string in ISO format
     */
    private static String timestamp() {
        return formatter.format(Instant.now());
    }
    
    /**
     * Get total trade count  
     */
    public static long getTotalTrades() {
        return tradeCounter.get();
    }
}
