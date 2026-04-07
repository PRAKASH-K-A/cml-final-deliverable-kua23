# LAB 12: CAPSTONE DEMONSTRATION GUIDE

## OVERVIEW

Lab 12 is the **capstone demonstration** of the complete Capital Market Technology system. This guide provides step-by-step instructions to execute a comprehensive end-to-end demo showcasing all labs working together in a live trading environment.

---

## SYSTEM ARCHITECTURE (Final)

```
┌─────────────────────────────────────────────────────────────┐
│                    MiniFix Simulator                        │
│                   (FIX 4.4 Client)                          │
│        Sends Orders via FIX Protocol (Port 9876)            │
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────▼───────────────┐
         │   FIX Acceptor (Port 9876)    │
         │   QuickFIX/J Engine           │
         │   (Lab 2: Connectivity)       │
         └───────────────┬───────────────┘
                         │
         ┌───────────────▼───────────────────────────┐
         │        Order Service Application          │
         ├───────────────────────────────────────────┤
         │ • Message Ingestion (Lab 3)               │
         │ • Validation (Lab 6: Security Master)     │
         │ • Matching Engine (Lab 7: Core Logic)     │
         │ • Execution Reporting (Lab 8: Feedback)   │
         │ • Performance Telemetry (Lab 9)           │
         │ • Resilience Handling (Lab 10)            │
         │ • Option Pricing (Lab 11: Black-Scholes)   │
         └───────────────┬───────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
    ┌────▼─────┐              ┌─────────▼──────┐
    │ Database  │              │ WebSocket      │
    │ (Lab 5)   │              │ (Lab 4)        │
    │ Persist   │              │ Broadcast      │
    │ Trades    │              │ (Port 8080)    │
    └───────────┘              └─────────┬──────┘
                                         │
                    ┌────────────────────▼────────────────┐
                    │     Angular Trading Dashboard       │
                    │      (Admin + Trading UI)           │
                    │  • Order Grid (Lab 4)               │
                    │  • Trade Blotter (Lab 8)            │
                    │  • Option Pricing (Lab 11)          │
                    │  • Live Updates (Real-time)         │
                    └─────────────────────────────────────┘
```

---

## PRE-DEMO CHECKLIST (30-45 minutes before)

### 1. Environment Verification (5 min)

```bash
# Check Java version
java -version
# Expected: Java 8+

# Check PostgreSQL status
systemctl status postgresql
# Expected: active (running)

# Check Node.js version
node --version
npm --version
# Expected: v14+ LTS

# Check Angular CLI
ng version
# Expected: Angular CLI version X.X.X
```

### 2. Database Verification (10 min)

```bash
# Connect to PostgreSQL
psql -U postgres -d trading_system

# Verify tables exist
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public';

# Expected tables:
# - orders
# - executions
# - security_master
# - customer_master
```

### 3. Project Build Verification (15 min)

```bash
# Terminal 1: Build Java Project
cd stocker/cmt
mvn clean compile
# Expected: BUILD SUCCESS

# Terminal 2: Build Angular Project
cd trading-ui
npm install
ng build
# Expected: BUILD SUCCESS

# Check build artifacts exist
ls -la stocker/cmt/target/
ls -la trading-ui/dist/
```

### 4. Configuration Verification (5 min)

**Check `order-service.cfg`:**
- BeginString=FIX.4.4
- SenderCompID=EXEC_SERVER
- TargetCompID=MINIFIX_CLIENT
- SocketAcceptPort=9876

**Check `DatabaseManager.java`:**
- Database URL points to local PostgreSQL
- Username/password are correct
- trading_system database is selected

---

## DEMO EXECUTION (60-90 minutes)

### PHASE 1: STARTUP (10 minutes)

#### Step 1.1: Start Backend Services
**Terminal 1:**
```bash
cd stocker/cmt
mvn exec:java -Dexec.mainClass="com.stocker.AppLauncher"
```

**Expected Output:**
```
============================================================
 ORDER MANAGEMENT SYSTEM - STARTUP 
============================================================
[STARTUP] ✓ Database queue created (capacity: 10,000 orders)
[ORDER SERVICE] Security Master loaded: 3 valid symbols
[ORDER BOOKS] Exchange initialized - Ready for matching
[LAB 11] Option Pricing Service initialized
[WEBSOCKET] ✓ WebSocket Server started on port 8080
[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...
============================================================
```

**Verification Checklist:**
- ✅ No database connection errors
- ✅ Security Master loaded successfully
- ✅ WebSocket on port 8080
- ✅ FIX on port 9876
- ✅ Option Pricing Service initialized

#### Step 1.2: Start Angular UI
**Terminal 2:**
```bash
cd trading-ui
ng serve
```

**Expected Output:**
```
✔ Compiled successfully.
✔ Browser application bundle generated successfully.

Initial chunks size:
       main.js               X.XX kB
       styles.css            X.XX kB
       polyfills.js          X.XX kB

Application bundle generation complete.
0 info it worked if it ends here
✔ Build at: timestamp
✔ Chunk Names:   main  styles main
```

**Verification:**
- ✅ Navigate to http://localhost:4200
- ✅ Two dashboards load without console errors
- ✅ "🔴 Disconnected" status visible (waiting for backend)

#### Step 1.3: Start MiniFix Simulator
**Terminal 3:**
```
1. Open MiniFix application
2. Configure:
   - Host: localhost or 127.0.0.1
   - Port: 9876
   - SenderCompID: MINIFIX_CLIENT
   - TargetCompID: EXEC_SERVER
   - Protocol: FIX.4.4
3. Click "Connect"
```

**Expected State:**
- ✅ MiniFix shows "Connected" status
- ✅ Backend Terminal 1 shows: "[ORDER SERVICE] ✓ Logon successful"
- ✅ Angular UI shows: "🟢 Connected" status
- ✅ All three systems operational

---

### PHASE 2: BASIC ORDER FLOW DEMO (15 minutes)

**Demonstrate:** Order Ingestion → Validation → Acknowledgment

#### Step 2.1: Send Single Buy Order
**MiniFix:**
1. Create new order
2. Settings:
   - Symbol: GOOG
   - Side: BUY
   - OrderQty: 100
   - Price: 150.00
   - OrdType: Limit
3. Click "Send"

**Expected Output:**
- **Backend Console 1:**
  ```
  [ORDER RECEIVED] ID=<random> | Side: BUY | Symbol: GOOG | Price: $150.00 | Qty: 100.0
  [ORDER SERVICE] Order queued for persistence: <ordID>
  ```

- **Angular UI (Orders Tab):**
  ```
  CLOrdID  | Symbol | Side | Qty  | Price   | Status
  <ID>     | GOOG   | BUY  | 100  | $150.00 | NEW
  ```

- **MiniFix Event Log:**
  ```
  [GREEN] ExecutionReport (MsgType=8) | Status: NEW | Qty: 100
  ```

**Verify:**
- ✅ Order appears in UI grid
- ✅ Status shows "NEW"
- ✅ ACK received in MiniFix (green event)

#### Step 2.2: Send Counter Order (Create Match)
**MiniFix:**
1. Create new order
2. Settings:
   - Symbol: GOOG
   - Side: SELL
   - OrderQty: 100
   - Price: 150.00 (or lower)
3. Click "Send"

**Expected Output:**
- **Backend Console 1:**
  ```
  [ORDER RECEIVED] ID=<random> | Side: SELL | Symbol: GOOG | Price: $150.00 | Qty: 100.0
  [ORDER SERVICE] Order <id> generated 1 execution(s)
  [EXECUTION REPORT] BUY  | ClOrdID: <buy_order> | Filled: 100 | CumQty: 100 | Leaves: 0 | Status: 2
  [EXECUTION REPORT] SELL | ClOrdID: <sell_order> | Filled: 100 | CumQty: 100 | Leaves: 0 | Status: 2
  [LAB 11] Option Update | GOOG | Spot: $150.00 | Call: $12.45 | Put: $3.22 | Delta: 0.7391
  ```

- **Angular UI (Executions Tab):**
  ```
  ExecID | Buy Order | Sell Order | qty | Price  | Time
  EXEC_* | <buyID>   | <sellID>   | 100 | $150.0 | HH:MM:SS
  ```

- **Angular UI (Option Pricing Tab):**
  ```
  Symbol | Spot   | Strike | Call Price | Put Price | Delta
  GOOG   | $150.0 | $150.0 | $12.45     | $3.22     | 0.7391
  ```

- **MiniFix Event Log:**
  ```
  [GREEN] ExecutionReport (MsgType=8) | ExecType: TRADE | Qty: 100 @ $150.00
  ```

**Verify:**
- ✅ Both orders show FILLED status
- ✅ Execution appears in Executions tab
- ✅ Option prices calculated and displayed
- ✅ Database persisted the trade

---

### PHASE 3: MULTI-SYMBOL TRADING DEMO (15 minutes)

**Demonstrate:** Exchange handling multiple symbols with independent order books

#### Step 3.1-3.3: Execute Trades for 3 Symbols

**Trade Sequence:**

| Step | Symbol | BUY/SELL | Qty | Price | Expected Spot |
|------|--------|----------|-----|-------|---------------|
| 1 | MSFT | BUY | 50 | $300 | - |
| 2 | MSFT | SELL | 50 | $300 | $300 |
| 3 | IBM | BUY | 75 | $180 | - |
| 4 | IBM | SELL | 75 | $180 | $180 |
| 5 | GOOG | BUY | 30 | $155 | - |
| 6 | GOOG | SELL | 30 | $155 | $155 |

**Verification After Each Trade:**

1. **Orders appear immediately** (no latency)
2. **Matches execute correctly** (quantity checking)
3. **Execution Reports sent** to both sides
4. **Option prices updated** for each symbol
5. **UI refreshes in real-time** (WebSocket working)
6. **Database persisted** all records

**Expected Dashboard State:**
```
Orders Grid:
- 6 entries total (3 BUY, 3 SELL)
- All status: FILLED

Executions Grid:
- 3 executions (one per symbol)
- Volumes: 50 MSFT, 75 IBM, 30 GOOG
- Total value: (50*$300) + (75*$180) + (30*$155) = $29,550

Option Pricing Grid:
- 3 symbols: MSFT, IBM, GOOG
- Each with spot price, call/put, Greeks
- All shown in real-time

Statistics:
- Total Symbols Priced: 3
- Average Call Price: $(sum of 3) / 3
- Average Put Price: $(sum of 3) / 3
```

---

### PHASE 4: STRESS TEST (20 minutes)

**Demonstrate:** System handling high-frequency order flow

#### Step 4.1: Rapid-Fire Orders

**Configure MiniFix Auto-Generator:**
1. Enable "Auto-generate orders"
2. Settings:
   - Symbols: GOOG, MSFT, IBM (rotate through all)
   - Rate: 10 orders/second
   - Duration: 2 minutes (120 orders)
   - Price range: $150-$160 for each symbol

**Start Stress Test:**
```
Click "Start Auto Orders" in MiniFix
Watch system handle 120 orders without crashing
```

**Monitoring During Test:**

**Terminal 1 (Backend):**
- ✅ Messages flowing without gaps
- ✅ Matching happening correctly
- ✅ Option pricing updating
- ✅ No exceptions in logs
- ✅ Memory usage stable (check: `java.lang.Runtime`)

**Terminal 2 (Angular UI):**
- ✅ Grid scrolling, showing most recent orders
- ✅ No lag or freezing
- ✅ Updates every ~100ms
- ✅ All three symbols trading

**After 2 Minutes:**
```bash
# Expected Console Output:
[PERFORMANCE] Orders processed: 120
[PERFORMANCE] Average latency: 2.3 ms
[PERFORMANCE] Trades executed: 60 (50% hit rate)
[PERFORMANCE] Memory usage: 185 MB (stable)
[LAB 11] Total option updates: 60
```

**Verification Checklist:**
- ✅ No crashes or exceptions
- ✅ All 120 orders processed
- ✅ ~50% match rate (normal for random orders)
- ✅ Memory stable
- ✅ Option prices updated 60 times
- ✅ UI responsive throughout

---

### PHASE 5: ADVANCED SCENARIOS (15 minutes)

#### Scenario 5.1: Partial Fill Demonstration
```
Send: 100 BUY @ $152
Find: 50 SELL @ $151 → Match 50
Show: 100 BUY order now has 50 Leaves, Status = PARTIALLY_FILLED
Then: 50 SELL @ $150 → Match remaining 50 at RESTING price
Show: Complete fill with two executions
```

#### Scenario 5.2: Rejection Handling
```
Send: BUY 100 @ -10.00 (negative price)
Expected: Instant rejection "Invalid Price or Qty"
Show: Order with Status: REJECTED
Verify: Not in executions list
```

#### Scenario 5.3: Greeks Sensitivity
```
Scenario: GOOG currently 50 units filled
1. Execute: BUY 50 @ $145 (spot drops) → Delta decreases
2. Execute: BUY 60 @ $160 (spot rises) → Delta increases
3. Show: Greeks changing as spot moves
```

#### Scenario 5.4: Database Verification
```bash
# Terminal 4: Verify Database Persistence
psql -U postgres -d trading_system

# Count records
SELECT COUNT(*) as order_count FROM orders;
SELECT COUNT(*) as execution_count FROM executions;

# Sample data
SELECT cl_ord_id, symbol, side, status FROM orders LIMIT 5;
SELECT buy_ord_id, sell_ord_id, exec_qty, exec_price FROM executions LIMIT 5;

Expected: All trades from demo are in database with correct data
```

---

## POSTMORTEM & DEMONSTRATION TALKING POINTS (5-10 minutes)

### System Architecture Highlights

**"This 3-tier microservices architecture demonstrates:"**

1. **Scalable Data Ingestion (Labs 1-3)**
   - "FIX protocol handles real-time order flow at high frequency"
   - "Message parsing and validation occur at wire speed"
   - "No data loss, even under 10 orders/second load"

2. **Real-Time Matching (Labs 6-7)**
   - "Order matching happens in <2ms using price-time priority"
   - "Support for partial fills and multi-level order books"
   - "Independent order books per symbol, zero contention"

3. **Live Feedback Loop (Lab 8)**
   - "Execution Reports sent via FIX back to trading client"
   - "WebSocket pushes to Angular UI simultaneously"
   - "Trader sees their fills in real-time, no polling needed"

4. **Persistence & Resilience (Labs 5, 10)**
   - "All trades persisted to PostgreSQL for audit trail"
   - "Asynchronous queue decouples ingestion from storage"
   - "Session recovery preserves order state on reconnect"

5. **Quantitative Finance Integration (Lab 11)**
   - "Black-Scholes formula calculates option prices on each trade"
   - "All Greeks computed for risk management"
   - "Options update automatically as spot price changes"

6. **Observability & Monitoring (Lab 9, 12)**
   - "Health dashboard tracks throughput, latency, memory"
   - "Telemetry visible in console and UI"
   - "Can support 500K orders with stable performance"

### Performance Talking Points

```
"During the stress test with 120 orders in 2 minutes:
- Average latency: 2.3ms per order
- Peak throughput: 60 trades/minute
- Memory stable at 185MB (well under limits)
- Zero data loss
- 100% successful persistence

This demonstrates we can scale to production requirements:
- 500K orders/day = ~5 orders/second average
- Our test: 10 orders/second → 2x capacity buffer
- Proven stability under load"
```

---

## DEMO FAILURE RECOVERY

### If Backend won't start
```bash
# Check PostgreSQL
psql -U postgres -c "SELECT 1"

# Check port conflicts
lsof -i :9876
lsof -i :8080

# Rebuild
cd stocker/cmt
mvn clean compile
```

### If Angular won't connect
```bash
# Clear browser cache
# Hard refresh: Ctrl+Shift+R (Windows) or Cmd+Shift+R (Mac)

# Check WebSocket
# Browser console → F12 → check for ws:// connection

# Rebuild Angular
cd trading-ui
npm install
ng build
```

### If trades not matching
```bash
# Verify order book
# Backend logs should show prices
# Check: Are prices exactly matching (e.g., 150 vs 150)?

# Clear and reconnect MiniFix
# Ensures fresh session
```

### If Database issues
```bash
# Check connection
psql -U postgres -d trading_system

# Verify schema
\dt  # list tables

# If missing tables, run schema script
psql -U postgres -d trading_system < schema.sql
```

---

## ESTIMATED TIMING

| Phase | Duration | Cumulative |
|-------|----------|-----------|
| Startup | 10 min | 10 min |
| Basic Orders | 15 min | 25 min |
| Multi-Symbol | 15 min | 40 min |
| Stress Test | 20 min | 60 min |
| Advanced Scenarios | 15 min | 75 min |
| Database Verification | 5 min | 80 min |
| Q&A & Discussion | 10 min | 90 min |

**Total: 90 minutes for complete capstone demonstration**

---

## PRESENTATION STRUCTURE

### Narrative Arc

1. **Opening (2 min):** "This semester, we built a complete trading system from scratch..."
2. **Architecture (3 min):** "Show the system diagram and each component..."
3. **Live Demo (75 min):** "Let me show you it all working together..."
4. **Performance (5 min):** "Here's what it's capable of under load..."
5. **Closing (5 min):** "Questions? Let's discuss what we could improve..."

### Visual Aids to Show

- ✅ System architecture diagram (ASCII in terminal)
- ✅ Live backend console logs
- ✅ Angular dashboard updating in real-time
- ✅ MiniFix order events
- ✅ Database query results
- ✅ Performance metrics

---

## SUCCESS CRITERIA

✅ **All systems start without errors**  
✅ **Orders flow from FIX → Matching → UI in <3 seconds total**  
✅ **Stress test handles 10 orders/second**  
✅ **All trades visible in database**  
✅ **Option pricing updates correctly**  
✅ **No memory leaks or crashes**  
✅ **All three dashboards display live data**  
✅ **Audience understands 3-tier microservices architecture**  

---

## NOTES FOR DEMONSTRATION

- **Prepare printed reference cards** with commands for quick recovery
- **Have backup data** from prior runs in case of demo issues
- **Test the entire chain 24 hours before** to catch configuration issues
- **Record the demo** for later review/documentation
- **Practice the narrative** to maintain good pacing
- **Have talking points** ready for each system component

---

**Good luck with your capstone demonstration! You've built a production-grade trading system.** 🎉

