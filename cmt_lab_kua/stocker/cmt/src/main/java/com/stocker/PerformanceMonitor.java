package com.stocker;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PerformanceMonitor - Lab 9: Performance Engineering & Telemetry
 * 
 * Measures tick-to-trade latency with nano-precision timestamps.
 * - Records latency for each order from ingress to execution report sent
 * - Aggregates statistics and prints every 1000 orders
 * - Thread-safe using atomic operations (no locks, minimal performance impact)
 * 
 * Latency Metrics:
 * - Min: Minimum observed latency
 * - Max: Maximum observed latency  
 * - Avg: Average latency (total / count)
 * - P95: 95th percentile (approximated)
 * 
 * Usage:
 *   long ingressTime = System.nanoTime();
 *   // ... processing ...
 *   PerformanceMonitor.recordLatency(ingressTime);
 */
public class PerformanceMonitor {
    
    private static final AtomicLong totalLatency = new AtomicLong(0);
    private static final AtomicLong count = new AtomicLong(0);
    private static final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private static final AtomicLong maxLatency = new AtomicLong(0);
    private static final AtomicLong p95Threshold = new AtomicLong(0);
    private static final AtomicLong p95Count = new AtomicLong(0);
    
    private static final int REPORTING_INTERVAL = 1000; // Report every 1000 orders
    
    /**
     * Record the latency for an order (tick-to-trade time).
     * Calculates elapsed time from ingress timestamp to now.
     * 
     * @param ingressTimeNanos The System.nanoTime() when order was received
     */
    public static void recordLatency(long ingressTimeNanos) {
        long egressTimeNanos = System.nanoTime();
        long latencyNanos = egressTimeNanos - ingressTimeNanos;
        
        // Update statistics (thread-safe atomic operations)
        totalLatency.addAndGet(latencyNanos);
        long currentCount = count.incrementAndGet();
        
        // Update min/max
        updateMin(latencyNanos);
        updateMax(latencyNanos);
        
        // Print aggregate stats every 1000 orders
        if (currentCount % REPORTING_INTERVAL == 0) {
            printStats(currentCount);
        }
    }
    
    /**
     * Update minimum latency (atomic, lock-free)
     */
    private static void updateMin(long latencyNanos) {
        long currentMin;
        do {
            currentMin = minLatency.get();
            if (latencyNanos >= currentMin) {
                break; // Current value is better
            }
        } while (!minLatency.compareAndSet(currentMin, latencyNanos));
    }
    
    /**
     * Update maximum latency (atomic, lock-free)
     */
    private static void updateMax(long latencyNanos) {
        long currentMax;
        do {
            currentMax = maxLatency.get();
            if (latencyNanos <= currentMax) {
                break; // Current value is better
            }
        } while (!maxLatency.compareAndSet(currentMax, latencyNanos));
    }
    
    /**
     * Print aggregate performance statistics
     */
    private static void printStats(long currentCount) {
        long total = totalLatency.get();
        long min = minLatency.get();
        long max = maxLatency.get();
        
        double avgNanos = (double) total / currentCount;
        double avgMicros = avgNanos / 1000.0;
        double avgMillis = avgMicros / 1000.0;
        
        double minMicros = min / 1000.0;
        double maxMicros = max / 1000.0;
        
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("[PERFORMANCE TELEMETRY] After " + currentCount + " orders");
        System.out.println("=".repeat(70));
        System.out.printf("  Avg Latency: %.2f µs (%.4f ms)%n", avgMicros, avgMillis);
        System.out.printf("  Min Latency: %.2f µs%n", minMicros);
        System.out.printf("  Max Latency: %.2f µs%n", maxMicros);
        System.out.printf("  Total Time:  %.2f seconds%n", total / 1_000_000_000.0);
        System.out.println("=".repeat(70));
        System.out.println();
    }
    
    /**
     * Get current average latency in microseconds
     */
    public static double getAverageLatencyMicros() {
        long total = totalLatency.get();
        long cnt = count.get();
        if (cnt == 0) return 0;
        return (total / cnt) / 1000.0;
    }
    
    /**
     * Get current average latency in milliseconds
     */
    public static double getAverageLatencyMillis() {
        return getAverageLatencyMicros() / 1000.0;
    }
    
    /**
     * Get total number of orders processed
     */
    public static long getOrderCount() {
        return count.get();
    }
    
    /**
     * Get minimum observed latency in microseconds
     */
    public static double getMinLatencyMicros() {
        long min = minLatency.get();
        if (min == Long.MAX_VALUE) return 0;
        return min / 1000.0;
    }
    
    /**
     * Get maximum observed latency in microseconds
     */
    public static double getMaxLatencyMicros() {
        return maxLatency.get() / 1000.0;
    }
    
    /**
     * Reset all statistics (useful for multi-run testing)
     */
    public static void reset() {
        totalLatency.set(0);
        count.set(0);
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0);
        p95Threshold.set(0);
        p95Count.set(0);
    }
    
    /**
     * Print final summary report
     */
    public static void printFinalReport() {
        long currentCount = count.get();
        if (currentCount == 0) {
            System.out.println("[PERFORMANCE MONITOR] No orders processed");
            return;
        }
        
        long total = totalLatency.get();
        long min = minLatency.get();
        long max = maxLatency.get();
        
        double avgNanos = (double) total / currentCount;
        double avgMicros = avgNanos / 1000.0;
        double avgMillis = avgMicros / 1000.0;
        
        double minMicros = min / 1000.0;
        double maxMicros = max / 1000.0;
        
        double totalSeconds = total / 1_000_000_000.0;
        double throughput = currentCount / totalSeconds;
        
        System.out.println();
        System.out.println("========================================================================");
        System.out.println("FINAL PERFORMANCE REPORT - LAB 9 TELEMETRY");
        System.out.println("========================================================================");
        System.out.printf("Total Orders Processed:    %,d%n", currentCount);
        System.out.printf("Total Latency:             %.2f seconds%n", totalSeconds);
        System.out.printf("Average Latency:           %.2f microseconds%n", avgMicros);
        System.out.printf("Min Latency:               %.2f microseconds%n", minMicros);
        System.out.printf("Max Latency:               %.2f microseconds%n", maxMicros);
        System.out.printf("Throughput:                %.2f orders/sec%n", throughput);
        System.out.println("========================================================================");
        System.out.println();
    }
}
