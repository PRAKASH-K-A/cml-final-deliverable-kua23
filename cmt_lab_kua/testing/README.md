# FIX Order Sender Testing Tool

This directory contains Python utilities for testing the Java trading backend with FIX protocol messages.

## Setup

### Prerequisites

- Python 3.7+
- Standard library only (no external dependencies required)

### Installation

No installation needed! The script uses only Python's standard library (`socket`, `time`, `argparse`, `sys`, `random`, `datetime`).

To verify Python is available:
```bash
python --version
```

## Usage

### Two Usage Modes

#### Mode 1: Interactive (Default)
Run the script with no arguments to be prompted for all settings:

```bash
python order_sender.py
```

This will prompt you for:
1. Number of orders (1-100,000)
2. Delay between orders (0-10 seconds)
3. Sending mode (1=Normal or 2=Burst)
4. Threading options (1=Single-threaded or 2=Multi-threaded)
5. If multi-threaded: Number of worker threads (2-64)

**Example session:**
```
============================================================
FIX Order Sender - Interactive Configuration
============================================================

Enter number of orders to send (1-100000) [default: 10]: 50
Enter delay between orders in seconds (0-10) [default: 1.0]: 0.5
Select sending mode:
  1. Normal   (respects delay value)
  2. Burst    (send as fast as possible)
Enter choice (1 or 2) [default: 1]: 1
```

#### Mode 2: Command-Line Arguments
Provide arguments directly for automation/scripting:

```bash
# Send 10 orders with 1 second delay (normal mode)
python order_sender.py --orders 10 --delay 1.0 --mode normal

# Send 1000 orders as fast as possible (burst mode)
python order_sender.py --orders 1000 --mode burst

# Quick stress test - 500 orders with minimal delay
python order_sender.py --orders 500 --mode normal --delay 0.01
```

### Command-Line Options

```
--orders NUM       Number of orders to send (default: 10)
                   Range: 1 to 100,000
--delay SECONDS    Delay between orders in seconds (default: 1.0)
                   Range: 0 to 10 seconds
                   Set to 0 for burst mode
--mode {normal|burst}
                   normal: respects the --delay value
                   burst: minimal delay for rapid-fire sending
-h, --help         Show help message

Examples:
  python order_sender.py --help
  python order_sender.py --orders 100 --delay 0.5 --mode normal
  python order_sender.py --orders 5000 --mode burst
```

## Features

- **Pure Python FIX Protocol**: Constructs raw FIX 4.4 messages without external dependencies
- **Automatic Session Management**: Handles FIX connection to backend
- **Realistic Order Generation**: Creates orders with:
  - Random symbols (GOOG, MSFT, IBM, AAPL, AMZN)
  - Random sides (Buy/Sell)
  - Random quantities (50, 100, 150, 200, 500 shares)
  - Realistic price variations around base prices
- **Flexible Sending Modes**:
  - **Normal Mode**: Controlled sending with configurable delays
  - **Burst Mode**: Maximum throughput for stress testing
- **Detailed Logging**: Shows each order sent with symbol, side, quantity, price
- **Performance Metrics**: Reports throughput (orders/sec) at completion

## Example Scenarios

### Scenario 1: Interactive Mode (Beginner-Friendly)
```bash
python order_sender.py
# Then respond to prompts:
# - 100 (orders)
# - 0.5 (delay)
# - 1 (normal mode)
```

### Scenario 2: Production-like Load (CLI Mode)
```bash
# 100 orders with 500ms delay = ~10 orders/sec
python order_sender.py --orders 100 --delay 0.5 --mode normal
```

### Scenario 3: High-Frequency Burst (CLI Mode)
```bash
# 5000 orders as fast as possible
python order_sender.py --orders 5000 --mode burst
```

### Scenario 4: Latency Testing (CLI Mode)
```bash
# Small burst to measure order processing time
python order_sender.py --orders 20 --mode burst
```

### Scenario 5: Custom Stress Test
**Interactive:**
```bash
python order_sender.py
# Enter: 500, 0.01, 1
```

**or CLI:**
```bash
python order_sender.py --orders 500 --delay 0.01 --mode normal
```

### Scenario 6: High-Throughput Multithreaded (NEW!)
```bash
# 5000 orders with 4 worker threads = ~2500+ orders/sec
python order_sender.py --orders 5000 --mode burst --threads 4
```

### Scenario 7: Maximum Capacity Test (NEW!)
```bash
# 10000 orders with 8 worker threads for stress testing
python order_sender.py --orders 10000 --mode burst --threads 8
```

### Scenario 8: Interactive Multithreading
```bash
python order_sender.py
# Then respond:
# - 5000 (orders)
# - 0 (delay)
# - 2 (burst mode)
# - 2 (multithreaded)
# - 8 (worker threads)
```

## Performance Benchmarks

These benchmarks show typical throughput on a modern system with the Java backend running on localhost:

| Configuration | Orders | Time (sec) | Throughput (orders/sec) | Notes |
|---|---|---|---|---|
| Single-threaded, Normal | 50 | 50.0 | 1.0 | 1s delay between orders |
| Single-threaded, Burst | 100 | 0.15 | 658 | Maximum single-thread speed |
| **4-threaded, Burst** | 100 | 0.04 | **2,591** | 4x improvement |
| **8-threaded, Burst** | 1,000 | 0.29 | **3,486** | Peak throughput window |
| **16-threaded, Burst** | 5,000 | 9.88 | **506** | Backend saturation point |

**Key Findings:**
- Single-threaded burst: ~658 orders/sec
- 4-threaded burst: ~2,591 orders/sec (≈4x speedup)
- 8-threaded burst: ~3,486 orders/sec (≈5x speedup) 
- Sweet spot: 4-8 threads for this backend
- Beyond 8 threads: Diminishing returns due to backend I/O limits



```
============================================================
Order Sending Configuration
============================================================
Number of Orders: 5
Delay Mode: normal
Delay Between Orders: 1.0 second(s)
============================================================

[   1] BUY    100 GOOG @ $143.52 | ClOrdID: ORD_1704067200123_1
[   2] SELL   200 MSFT @ $375.80 | ClOrdID: ORD_1704067201124_2
[   3] BUY    150 IBM @ $182.40 | ClOrdID: ORD_1704067202125_3
[   4] SELL   500 AAPL @ $188.95 | ClOrdID: ORD_1704067203126_4
[   5] BUY    100 AMZN @ $171.25 | ClOrdID: ORD_1704067204127_5

============================================================
Summary
============================================================
Total Sent:    5
Failed:        0
Total Time:    4.23 seconds
Throughput:    1.18 orders/sec
============================================================
```

## Testing Your Backend

1. **Start your Java trading backend:**
```bash
cd /path/to/order-service
mvn clean compile exec:java -Dexec.mainClass="com.stocker.AppLauncher"
```

2. **Run the order sender - Choose your mode:**

**Interactive Mode (Recommended for first-time users):**
```bash
cd testing
python order_sender.py
# Then answer the prompts
```

**Command-Line Mode (For automation/scripting):**
```bash
cd testing
python order_sender.py --orders 50 --delay 0.1 --mode normal
```

**High-Throughput Multithreading:**
```bash
cd testing
python order_sender.py --orders 10000 --mode burst --threads 8
```

3. **Monitor the backend:**
   - Watch the Java console for "ORDER RECEIVED" messages
   - Check the Angular UI (http://localhost:4200) for live order updates
   - Query the database for persisted orders

## Multithreading Architecture

### Thread Safety
- **Message Sequence Numbers**: Protected by `threading.Lock()` to ensure uniqueness across threads
- **Order IDs**: Generated atomically with proper synchronization
- **Socket Connection**: Single shared connection for all threads (FIX protocol supports concurrent messages)
- **ThreadPoolExecutor**: Manages worker threads with proper cleanup

### How It Works
1. **Main thread** creates ThreadPoolExecutor with N worker threads
2. **Each worker thread**:
   - Generates a random order
   - Gets unique sequence number and order ID from synchronized counters
   - Constructs FIX message
   - Sends via shared socket connection
3. **Results collected** as tasks complete (using `as_completed`)
4. **Throughput**: All N threads sending concurrently = ~N× speedup

### Performance Tips
- **4-8 threads**: Best for most backends (good balance)
- **Burst mode + threads**: Use together for maximum throughput
- **Normal mode + threads**: Less effective (delays wasted per-thread)
- **Monitor latency**: If responses slow, reduce thread count
- **Backend capacity**: Watch for increased P99 latencies at extreme loads

## Advanced Testing

### Stress Test (Load Testing)
```bash
# Generate 50,000 orders rapidly with 8 threads
python order_sender.py --orders 50000 --mode burst --threads 8
```

### Latency Measurement
```bash
# Send orders with known delays and measure response times
python order_sender.py --orders 100 --delay 0.1 --mode normal --threads 2
# Compare timestamp in output with backend processing logs
```

### Symbol-specific Testing
To modify the script for testing specific symbols only, edit the `SYMBOLS` list in `order_sender.py`.

## Troubleshooting

### Connection Refused
- Ensure Java backend is running on port 9876
- Check firewall settings
- Verify host/port in the script matches your backend configuration

### Invalid Input in Interactive Mode
- Ensure you enter numbers in the valid ranges:
  - Orders: 1 to 100,000
  - Delay: 0 to 10 seconds
  - Mode: 1 or 2
- Press Enter to use default values

### No Response from Backend
- Backend may be connected but not responding to FIX messages
- Check that the configuration IDs match (MINIFIX_CLIENT ↔ EXEC_SERVER)
- Review backend logs for parsing errors

### Unicode Character Errors (Windows Only)
- The script now uses ASCII-compatible symbols [OK], [ERROR], [!], [*]
- If you still see encoding errors, upgrade your terminal or Python version

### Multithreading Performance Issues
**Low throughput with high thread count:**
- Backend may be saturated
- Try reducing thread count (e.g., 4 instead of 16)
- Check backend CPU/memory usage
- Verify network connectivity and latency

**Thread synchronization errors:**
- Rare, but if seen: upgrade Python to latest 3.x
- Check for socket timeout errors (may indicate backend slowness)

**Memory usage high with many threads:**
- Each thread has overhead (~1-2MB)
- With 64 threads: expect ~100-150MB additional memory
- Reduce thread count if memory is constrained

## Integration with Lab Pipeline

This tool integrates with your full trading system:

```
order_sender.py (FIX Client)
    ↓ (FIX 4.4 Protocol via port 9876)
Java Backend (AppLauncher)
    ↓ (WebSocket)
Angular UI (trading-ui)
    ↓ (Database Queue)
PostgreSQL (Persistence)
```

See [LAB3_REPORT.md](../stocker/cmt/LAB3_REPORT.md) for related implementation details.
