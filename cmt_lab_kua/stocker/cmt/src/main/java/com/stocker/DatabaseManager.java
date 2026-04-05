package com.stocker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DatabaseManager - Singleton for PostgreSQL Database Operations
 * 
 * Handles all database interactions for order persistence.
 * Uses JDBC with PostgreSQL driver with resilience retry logic.
 * 
 * LAB 10: FAULT TOLERANCE
 * - Implements exponential backoff for transient DB failures
 * - Continues to queue orders even if DB is temporarily unavailable
 * - Orders are persisted when DB recovers
 * 
 * IMPORTANT: In production, use connection pooling (HikariCP) and 
 * externalize credentials to environment variables.
 */
public class DatabaseManager {
    
    // PostgreSQL Connection Configuration
    private static final String URL = "jdbc:postgresql://localhost:5432/trading_system";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root"; // CHANGE THIS to your PostgreSQL password!
    
    // LAB 10: Database Resilience Configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 100; // Start with 100ms
    private static final long MAX_BACKOFF_MS = 5000;    // Cap at 5 seconds
    
    /**
     * LAB 10: Get a resilient connection with exponential backoff retry logic.
     * If the database is temporarily unreachable, this method will retry with
     * increasing backoff delays before giving up.
     * 
     * This allows the system to recover gracefully from transient DB outages
     * without losing orders (they will be queued and retried later).
     * 
     * @return Connection if successful
     * @throws SQLException if all retry attempts fail
     */
    private static Connection getConnectionWithRetry() throws SQLException {
        long backoffMs = INITIAL_BACKOFF_MS;
        SQLException lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
                
                if (attempt > 1) {
                    System.out.println("[DATABASE] ✓ Recovered after " + attempt + " attempts");
                }
                
                return conn;
                
            } catch (SQLException e) {
                lastException = e;
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    System.err.println("[DATABASE] ⚠ Connection attempt " + attempt + " failed: " + 
                                     e.getMessage() + " | Retrying in " + backoffMs + "ms...");
                    
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted during retry backoff", ie);
                    }
                    
                    // Exponential backoff: double the wait time but cap at MAX_BACKOFF_MS
                    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                } else {
                    System.err.println("[DATABASE] ✗ All " + MAX_RETRY_ATTEMPTS + 
                                     " connection attempts exhausted: " + e.getMessage());
                }
            }
        }
        
        throw lastException;
    }
    
    /**
     * Insert a new order into the PostgreSQL database
     * 
     * This method is called by the OrderPersister worker thread.
     * It uses PreparedStatement to prevent SQL injection.
     * 
     * LAB 10: Uses resilient connection with retry logic to handle transient DB failures.
     * 
     * @param order The Order object to persist
     */
    public static void insertOrder(Order order) {
        String sql = "INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, quantity, order_type, status, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnectionWithRetry();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set parameters (matching Order POJO fields)
            pstmt.setString(1, order.getOrderId());
            pstmt.setString(2, order.getClOrdID());
            pstmt.setString(3, order.getSymbol());
            pstmt.setString(4, String.valueOf(order.getSide()));
            pstmt.setDouble(5, order.getPrice());
            pstmt.setDouble(6, order.getQuantity());
            pstmt.setString(7, String.valueOf(order.getOrderType())); // Order Type (1=Market, 2=Limit, etc.)
            pstmt.setString(8, "NEW"); // Initial status
            pstmt.setTimestamp(9, Timestamp.from(Instant.now()));
            
            // Execute the insert
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println("[DATABASE] ✓ Order persisted: " + order.getClOrdID() + 
                                   " (" + order.getSymbol() + ") Type=" + order.getOrderType());
            }
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to persist order: " + order.getClOrdID());
            System.err.println("[DATABASE] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test database connectivity with retry logic.
     * 
     * Call this at startup to verify PostgreSQL is accessible
     * before accepting any orders. Uses exponential backoff to handle
     * startup timing issues (DB may take time to start).
     */
    public static boolean testConnection() {
        System.out.println("[DATABASE] Testing PostgreSQL connection with resilience...");
        
        try (Connection conn = getConnectionWithRetry()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();
            
            System.out.println("[DATABASE] ✓ Connected to " + dbProduct + " " + dbVersion);
            System.out.println("[DATABASE] ✓ URL: " + URL);
            System.out.println("[DATABASE] ✓ Ready for order persistence");
            
            return true;
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Connection FAILED after all retries!");
            System.err.println("[DATABASE] ✗ Error: " + e.getMessage());
            System.err.println("[DATABASE] ✗ Make sure PostgreSQL is running and credentials are correct");
            
            return false;
        }
    }
    
    /**
     * Update order status (for future use)
     * Uses resilient connection with retry logic.
     *
     * @param clOrdID Client Order ID
     * @param newStatus New status (e.g., FILLED, CANCELLED)
     */
    public static void updateOrderStatus(String clOrdID, String newStatus) {
        String sql = "UPDATE orders SET status = ? WHERE cl_ord_id = ?";
        
        try (Connection conn = getConnectionWithRetry();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, newStatus);
            pstmt.setString(2, clOrdID);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println("[DATABASE] ✓ Order status updated: " + clOrdID + " -> " + newStatus);
            }
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to update order: " + clOrdID);
            e.printStackTrace();
        }
    }
    
    /**
     * Update cumulative quantity and order status based on fills.
     * Uses resilient connection with retry logic.
     * Called after each execution to keep database in sync with memory state
     * 
     * @param clOrdID Client Order ID
     * @param cumulativeQty Total filled quantity so far
     * @param remainingQty Remaining quantity to be filled
     * @param newStatus NEW, PARTIALLY_FILLED, or FILLED
     */
    public static void updateOrderFill(String clOrdID, double cumulativeQty, double remainingQty, String newStatus) {
        String sql = "UPDATE orders SET cumulative_qty = ?, status = ? WHERE cl_ord_id = ?";
        
        try (Connection conn = getConnectionWithRetry();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setDouble(1, cumulativeQty);
            pstmt.setString(2, newStatus);
            pstmt.setString(3, clOrdID);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println(String.format(
                    "[DATABASE] ✓ Order fill updated: %s | CumQty: %.0f | Remaining: %.0f | Status: %s",
                    clOrdID, cumulativeQty, remainingQty, newStatus));
            }
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to update order fill: " + clOrdID);
            e.printStackTrace();
        }
    }
    
    /**
     * Load all securities from security_master into a HashMap for fast in-memory lookup.
     * Called once at startup. O(1) symbol validation thereafter.
     * Uses resilient connection with retry logic.
     *
     * @return Map of symbol -> Security object
     */
    public static Map<String, Security> loadSecurityMaster() {
        Map<String, Security> securities = new HashMap<>();
        String sql = "SELECT symbol, security_type, description, underlying, lot_size FROM security_master";
        
        System.out.println("[DATABASE] Loading Security Master from PostgreSQL...");
        
        try (Connection conn = getConnectionWithRetry();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Security sec = new Security();
                sec.setSymbol(rs.getString("symbol"));
                sec.setSecurityType(rs.getString("security_type"));
                sec.setDescription(rs.getString("description"));
                sec.setUnderlying(rs.getString("underlying"));
                sec.setLotSize(rs.getInt("lot_size"));
                securities.put(sec.getSymbol(), sec);
            }
            
            System.out.println("[DATABASE] ✓ Security Master loaded: " + securities.size() + " securities");
            securities.forEach((k, v) -> System.out.println("  - " + k + " (" + v.getSecurityType() + ")"));
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to load Security Master: " + e.getMessage());
            e.printStackTrace();
        }
        
        return securities;
    }
    
    /**
     * Load all customers from customer_master into a HashMap for fast credit limit checks.
     * Called once at startup. Uses resilient connection with retry logic.
     *
     * @return Map of customerCode -> Customer object
     */
    public static Map<String, Customer> loadCustomerMaster() {
        Map<String, Customer> customers = new HashMap<>();
        String sql = "SELECT customer_code, customer_name, customer_type, credit_limit FROM customer_master";
        
        System.out.println("[DATABASE] Loading Customer Master from PostgreSQL...");
        
        try (Connection conn = getConnectionWithRetry();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Customer cust = new Customer();
                cust.setCustomerCode(rs.getString("customer_code"));
                cust.setCustomerName(rs.getString("customer_name"));
                cust.setCustomerType(rs.getString("customer_type"));
                cust.setCreditLimit(rs.getBigDecimal("credit_limit"));
                customers.put(cust.getCustomerCode(), cust);
            }
            
            System.out.println("[DATABASE] ✓ Customer Master loaded: " + customers.size() + " customers");
            customers.forEach((k, v) -> System.out.println("  - " + k + " (" + v.getCustomerType() + ")"));
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to load Customer Master: " + e.getMessage());
            e.printStackTrace();
        }
        
        return customers;
    }
    
    /**
     * Insert an execution record when an order is matched (Lab 7).
     * Includes retry logic to handle race condition where order hasn't been persisted yet.
     *
     * @param execId   Unique execution ID
     * @param orderId  Server-side order ID (FK to orders table)
     * @param symbol   Instrument symbol
     * @param side     '1' = BUY, '2' = SELL
     * @param execQty  Quantity executed
     * @param execPrice Price at which execution occurred
     */
    public static void insertExecution(String execId, String orderId, String symbol,
                                       char side, int execQty, double execPrice) {
        String sql = "INSERT INTO executions (exec_id, order_id, symbol, side, exec_qty, exec_price) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        int maxRetries = 5;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, execId);
                pstmt.setString(2, orderId);
                pstmt.setString(3, symbol);
                pstmt.setString(4, String.valueOf(side));
                pstmt.setInt(5, execQty);
                pstmt.setDouble(6, execPrice);
                
                pstmt.executeUpdate();
                System.out.println(String.format(
                        "[DB PERSIST] Execution | ExecID: %s | Symbol: %s | Qty: %d @ $%.2f",
                        execId, symbol, execQty, execPrice));
                return; // Success
                
            } catch (SQLException e) {
                if (e.getMessage().contains("foreign key constraint")) {
                    // Order hasn't been persisted yet by OrderPersister worker thread, retry with backoff
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            long waitMs = 50L * retryCount; // Exponential backoff: 50ms, 100ms, 150ms, etc
                            Thread.sleep(waitMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        System.err.println("[DATABASE] ✗ Failed to persist execution after " + maxRetries + " retries: " + execId);
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("[DATABASE] ✗ Failed to persist execution: " + execId);
                    e.printStackTrace();
                    return;
                }
            }
        }
    }
    
    /**
     * Insert execution records for a matched trade (BUY and SELL sides)
     * Creates two audit trail entries: one for the buyer, one for the seller
     * Also updates cumulative_qty and status for both orders
     * 
     * @param execution The Execution object containing trade details
     */
    public static void insertExecution(Execution execution) {
        Order buyOrder = execution.getBuyOrder();
        Order sellOrder = execution.getSellOrder();
        
        // Determine status for buy side
        String buyStatus;
        if (buyOrder.getQuantity() == 0) {
            buyStatus = "FILLED";
        } else if (buyOrder.getCumulativeQty() > 0) {
            buyStatus = "PARTIALLY_FILLED";
        } else {
            buyStatus = "NEW";
        }
        
        // Determine status for sell side
        String sellStatus;
        if (sellOrder.getQuantity() == 0) {
            sellStatus = "FILLED";
        } else if (sellOrder.getCumulativeQty() > 0) {
            sellStatus = "PARTIALLY_FILLED";
        } else {
            sellStatus = "NEW";
        }
        
        // Insert buy side execution
        insertExecution(
            execution.getExecId() + "_BUY",
            execution.getBuyOrderId(),
            execution.getSymbol(),
            '1',  // BUY side
            (int) execution.getExecQty(),
            execution.getExecPrice()
        );
        
        // Insert sell side execution
        insertExecution(
            execution.getExecId() + "_SELL",
            execution.getSellOrderId(),
            execution.getSymbol(),
            '2',  // SELL side
            (int) execution.getExecQty(),
            execution.getExecPrice()
        );
        
        // Update buy order fill status
        updateOrderFill(
            execution.getBuyClOrdId(),
            buyOrder.getCumulativeQty(),
            buyOrder.getQuantity(),
            buyStatus
        );
        
        // Update sell order fill status
        updateOrderFill(
            execution.getSellClOrdId(),
            sellOrder.getCumulativeQty(),
            sellOrder.getQuantity(),
            sellStatus
        );
    }
}
