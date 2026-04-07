package com.stocker;

/**
 * LAB 12: CAPSTONE - System Health Monitor
 * 
 * Collects and reports on the health of all system components:
 * - Order ingestion rate
 * - Matching engine performance
 * - Database persistence latency
 * - WebSocket connection health
 * - Memory utilization
 * - Option pricing updates per second
 */
public class SystemHealthMonitor {
    
    private long startTime;
    private volatile long orderCount = 0;
    private volatile long executionCount = 0;
    private volatile long optionUpdateCount = 0;
    private volatile long databasePersistenceTime = 0;
    private volatile long successfulTrades = 0;
    private volatile long rejectedOrders = 0;
    private volatile int activeConnections = 0;
    
    public SystemHealthMonitor() {
        this.startTime = System.currentTimeMillis();
    }
    
    // Increment counters
    public synchronized void recordOrderReceived() {
        orderCount++;
    }
    
    public synchronized void recordExecution() {
        executionCount++;
    }
    
    public synchronized void recordOptionUpdate() {
        optionUpdateCount++;
    }
    
    public synchronized void recordDatabaseLatency(long latencyMs) {
        databasePersistenceTime += latencyMs;
    }
    
    public synchronized void recordSuccessfulTrade() {
        successfulTrades++;
    }
    
    public synchronized void recordRejectedOrder() {
        rejectedOrders++;
    }
    
    public synchronized void setActiveConnections(int count) {
        activeConnections = count;
    }
    
    // Calculate metrics
    public synchronized SystemMetrics getMetrics() {
        long elapsedMs = System.currentTimeMillis() - startTime;
        double elapsedSeconds = elapsedMs / 1000.0;
        
        SystemMetrics metrics = new SystemMetrics();
        metrics.uptime = elapsedSeconds;
        metrics.totalOrders = orderCount;
        metrics.totalExecutions = executionCount;
        metrics.totalOptionUpdates = optionUpdateCount;
        metrics.successfulTrades = successfulTrades;
        metrics.rejectedOrders = rejectedOrders;
        metrics.ordersPerSecond = elapsedSeconds > 0 ? orderCount / elapsedSeconds : 0;
        metrics.executionsPerSecond = elapsedSeconds > 0 ? executionCount / elapsedSeconds : 0;
        metrics.optionUpdatesPerSecond = elapsedSeconds > 0 ? optionUpdateCount / elapsedSeconds : 0;
        metrics.avgDatabaseLatencyMs = executionCount > 0 ? databasePersistenceTime / executionCount : 0;
        metrics.fillRate = orderCount > 0 ? (100.0 * successfulTrades / orderCount) : 0;
        metrics.rejectionRate = orderCount > 0 ? (100.0 * rejectedOrders / orderCount) : 0;
        metrics.activeConnections = activeConnections;
        metrics.memoryUsedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        metrics.memoryMaxMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        
        return metrics;
    }
    
    /**
     * Print formatted health report
     */
    public synchronized void printHealthReport() {
        SystemMetrics metrics = getMetrics();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println(" LAB 12 CAPSTONE - SYSTEM HEALTH REPORT ");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 UPTIME & THROUGHPUT");
        System.out.printf("  Uptime:                    %.1f seconds\n", metrics.uptime);
        System.out.printf("  Orders/sec:                %.1f ops\n", metrics.ordersPerSecond);
        System.out.printf("  Executions/sec:            %.1f ops\n", metrics.executionsPerSecond);
        System.out.printf("  Option Updates/sec:        %.1f ops\n", metrics.optionUpdatesPerSecond);
        
        System.out.println("\n📈 TRANSACTION SUMMARY");
        System.out.printf("  Total Orders Received:     %,d\n", metrics.totalOrders);
        System.out.printf("  Successful Trades:         %,d\n", metrics.successfulTrades);
        System.out.printf("  Total Executions:          %,d\n", metrics.totalExecutions);
        System.out.printf("  Rejected Orders:           %,d\n", metrics.rejectedOrders);
        System.out.printf("  Fill Rate:                 %.2f%%\n", metrics.fillRate);
        System.out.printf("  Rejection Rate:            %.2f%%\n", metrics.rejectionRate);
        
        System.out.println("\n⚙️ PERFORMANCE METRICS");
        System.out.printf("  Avg DB Latency:            %.2f ms\n", metrics.avgDatabaseLatencyMs);
        System.out.printf("  Active WebSocket Conns:    %d\n", metrics.activeConnections);
        System.out.printf("  Option Update Events:      %,d\n", metrics.totalOptionUpdates);
        
        System.out.println("\n💾 MEMORY USAGE");
        System.out.printf("  Current:                   %,d MB (%.1f%%)\n", 
            metrics.memoryUsedMB, 
            (100.0 * metrics.memoryUsedMB / metrics.memoryMaxMB));
        System.out.printf("  Max Available:             %,d MB\n", metrics.memoryMaxMB);
        
        System.out.println("\n" + "=".repeat(80));
    }
    
    /**
     * Data class for health metrics
     */
    public static class SystemMetrics {
        public double uptime;
        public long totalOrders;
        public long totalExecutions;
        public long totalOptionUpdates;
        public long successfulTrades;
        public long rejectedOrders;
        public double ordersPerSecond;
        public double executionsPerSecond;
        public double optionUpdatesPerSecond;
        public double avgDatabaseLatencyMs;
        public double fillRate;
        public double rejectionRate;
        public int activeConnections;
        public long memoryUsedMB;
        public long memoryMaxMB;
    }
}
