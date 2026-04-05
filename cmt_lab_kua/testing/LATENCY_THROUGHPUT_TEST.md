# LAB 9: Latency vs. Throughput Performance Test Guide

## Overview

The **latency_throughput_test.py** script automates performance testing and analysis for the CMT trading system. It runs multiple test iterations at different throughput levels (100, 500, and 1000 orders/sec), collects latency metrics, and generates professional performance graphs and reports.

## Features

✅ **Automated Test Execution** — Runs multiple tests with different throughput rates sequentially  
✅ **Metric Collection** — Extracts average, minimum, and maximum latency from each test  
✅ **CSV Export** — Saves results to timestamped CSV for data analysis  
✅ **Excel Report** — Generates formatted Excel workbook with styling and statistics  
✅ **Performance Graphs** — Creates 4-panel visualization:
  - Average Latency vs. Throughput (main metric)
  - Min/Max Latency Range
  - Complete Latency Profile (all three metrics)
  - Summary Statistics Table

## Prerequisites

### Required
- Python 3.7+
- Backend (AppLauncher) running on localhost:9876
- Working order_sender.py client

### Optional (for advanced features)
```bash
pip install matplotlib numpy openpyxl
```

**Without these libraries:**
- CSV export works always ✅
- Excel export skipped if openpyxl missing ⚠️
- Graph generation skipped if matplotlib missing ⚠️

## Installation

1. **Place script in testing directory:**
   ```
   c:\Users\dhruv\Coding\cmt_lab_kua\testing\latency_throughput_test.py
   ```

2. **Install optional dependencies:**
   ```bash
   pip install matplotlib numpy openpyxl
   ```

## Usage

### Step 1: Start Backend
```bash
cd c:\Users\dhruv\Coding\cmt_lab_kua\stocker\cmt
java -cp "target\cmt-1.0-SNAPSHOT.jar;target\dependency\*" com.stocker.AppLauncher
```

Wait for: `[OK] Listening for connections on localhost:9876`

### Step 2: Run Performance Tests
```bash
cd c:\Users\dhruv\Coding\cmt_lab_kua\testing
python latency_throughput_test.py
```

### Step 3: Review Results

Results are saved in: `c:\Users\dhruv\Coding\cmt_lab_kua\testing\performance_results/`

- **CSV File** — Open in Excel or any spreadsheet
- **Excel Report** — Professional formatted workbook
- **PNG Graph** — High-resolution (300 DPI) performance visualization

## Test Configuration

The test suite runs three sequential tests:

| Test | Orders | Mode | Expected Throughput | Cool-down |
|------|--------|------|---------------------|-----------|
| 1 | 100 | burst | ~100 orders/sec | 2 sec |
| 2 | 500 | burst | ~500 orders/sec | 2 sec |
| 3 | 1000 | burst | ~1000 orders/sec | 2 sec |

**Total Expected Runtime:** ~2-3 minutes (depending on system performance)

### Customizing Tests

To modify test parameters, edit the test_configs in latency_throughput_test.py:

```python
test_configs = [
    (100, 'burst'),    # Test 1: 100 orders
    (500, 'burst'),    # Test 2: 500 orders
    (1000, 'burst'),   # Test 3: 1000 orders
]
```

Add or remove tests as needed. Format: `(num_orders, mode)`

Valid modes:
- `burst` — Send all orders as fast as possible
- `delay` — Use 1ms delay between orders

## Output Files

### CSV File Example
```
Test Number,Orders Sent,Mode,Throughput (orders/sec),Avg Latency (µs),Min Latency (µs),Max Latency (µs),Total Time (seconds)
1,100,burst,1250.25,892.15,456.32,2145.67,0.08
2,500,burst,1183.25,945.32,501.12,2289.45,0.42
3,1000,burst,1156.47,1012.45,523.67,2456.89,0.86
```

### Excel Report
- Formatted headers with blue background
- Alternating row colors for readability
- Centered alignment and proper column widths
- Perfect for presentations and documentation

### Performance Graph (4-panel)

**Panel 1: Average Latency vs. Throughput**
- Main performance metric
- Shows how average latency increases with throughput
- Value labels on each data point

**Panel 2: Min/Max Latency Range**
- Shows latency variability
- Shaded area between min and max
- Helps identify consistency

**Panel 3: Complete Latency Profile**
- Overlays all three metrics
- Easy comparison of min/avg/max trends
- Different marker styles for each metric

**Panel 4: Summary Statistics Table**
- Min/Avg/Max for each metric
- Quick reference for key numbers
- Color-coded for easy scanning

## Interpreting Results

### Expected Performance

For a well-tuned system:

| Metric | Expected Value | What It Means |
|--------|---|---|
| Throughput @ 100 orders | ~1200+ orders/sec | System can handle burst |
| Avg Latency @ 100 orders | ~800-1000 µs | Good tick-to-trade time |
| Avg Latency @ 1000 orders | ~1000-1500 µs | Minor increase at load |
| Max Latency | <3000 µs | No catastrophic delays |

### Performance Indicators

✅ **Healthy System:**
- Linear or sub-linear increase in latency with throughput
- Max latency < 3x average latency
- Consistent results across test runs
- Throughput > 1000 orders/sec

⚠️ **Warning Signs:**
- Dramatic latency spike at certain throughput
- Max latency > 5x average latency
- High variance in results
- Throughput drops significantly under load

## Troubleshooting

### "Connection Refused" Error
**Problem:** Backend not running
**Solution:** 
```bash
cd c:\Users\dhruv\Coding\cmt_lab_kua\stocker\cmt
java -cp "target\cmt-1.0-SNAPSHOT.jar;target\dependency\*" com.stocker.AppLauncher
```

### "matplotlib not installed" Warning
**Problem:** Graph generation skipped
**Solution:**
```bash
pip install matplotlib numpy
```

### "openpyxl not installed" Warning
**Problem:** Excel export skipped
**Solution:**
```bash
pip install openpyxl
```

### Test Timeout
**Problem:** Test runs exceed 5 minutes
**Possible Causes:**
- Backend slow or unresponsive
- Network issues
- System resource constraints

**Solution:** 
- Check backend performance: `mvn clean package -DskipTests`
- Reduce order counts in test_configs
- Run on dedicated test machine

### Inconsistent Results
**Problem:** Different results between test runs
**Common Causes:**
- Garbage collection during test
- Other system load
- Network packet loss

**Solution:**
- Run tests multiple times (3-5) and average results
- Close other applications
- Run tests during off-peak hours

## Performance Analysis Workflow

### 1. Baseline Measurement (First Run)
```bash
python latency_throughput_test.py
# Results: baseline_results_*.csv/xlsx/png
```

### 2. Optimization Changes
Make code/configuration changes to improve performance

### 3. Regression Testing (Second Run)
```bash
python latency_throughput_test.py
# Results: new_results_*.csv/xlsx/png
```

### 4. Comparison
- Open both CSV files in Excel
- Create comparison chart
- Calculate percentage improvement

## Advanced Usage

### Scripting the Test Suite
```python
from latency_throughput_test import PerformanceTestSuite

# Custom test configuration
suite = PerformanceTestSuite()
suite.add_test(50, 'burst')      # Very light load
suite.add_test(100, 'burst')     # Light load
suite.add_test(500, 'burst')     # Medium load
suite.add_test(1000, 'burst')    # Heavy load
suite.add_test(2000, 'burst')    # Extreme load

# Run and generate reports
suite.run_all()
suite.export_csv()
suite.export_excel(csv_file)
suite.generate_graphs()
```

### Custom Threshold Analysis
Add thresholds to your analysis:
```python
# After running tests
avg_latencies = [t.avg_latency for t in suite.tests]
throughputs = [t.throughput for t in suite.tests]

critical_threshold = 2000  # µs
for tp, lat in zip(throughputs, avg_latencies):
    if lat > critical_threshold:
        print(f"WARNING: Latency {lat:.2f}µs exceeds threshold at {tp:.2f} orders/sec")
```

## Integration with Lab 9 Evaluation

This test script validates **Lab 9 Performance Engineering & Telemetry** requirements:

✅ **Tick-to-Trade Latency Measurement**
- System.nanoTime() captures ingress timestamp in OrderApplication.fromApp()
- PerformanceMonitor.recordLatency() records at egress (ExecutionReport sent)

✅ **Performance Metrics Tracking**
- Average latency calculated across all orders
- Min/max latency tracked for variability analysis

✅ **10,000+ Order Scalability**
- Can be extended to run 10K or 50K order tests
- Lock-free design handles high volume

✅ **Production-Grade Telemetry**
- Automated periodic reporting (every 1000 orders)
- Final comprehensive report on shutdown
- Atomic operations ensure thread safety

## Reference Data

### Lab 9 Performance Targets

From LAB9_IMPLEMENTATION.md:

| Scenario | Target | Notes |
|----------|--------|-------|
| Single Test Run | 1000 orders | Standard test size |
| Throughput Capability | >1000 orders/sec | Burst mode |
| Avg Latency | 800-1200 µs | Tick-to-trade |
| Latency Variability | Min/Max ratio <2 | Consistency metric |
| System Load | 10,000+ orders | Scalability test |

## Support

For issues or questions:
1. Check the Troubleshooting section above
2. Review backend logs: `stocker/cmt/logs/`
3. Verify FIX session: Check `FIX.4.4-EXEC_SERVER-MINIFIX_CLIENT.session`
4. Profile with VisualVM: Check for GC pauses or thread contention

## Related Documentation

- [LAB 9 Implementation](../stocker/cmt/LAB9_IMPLEMENTATION.md)
- [LAB 9 Assessment Report](../stocker/cmt/LAB9_ASSESSMENT_REPORT.md)
- [Performance Monitor API](../stocker/cmt/LAB9_IMPLEMENTATION_SUMMARY.md)

---

**Last Updated:** 2026-04-01  
**Version:** 1.0  
**Status:** Production Ready ✅
