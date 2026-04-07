# LAB 12: QUICK REFERENCE CARD

## 🎯 ESSENTIAL COMMANDS

### Build the System (10 minutes)

```bash
# Backend
cd stocker/cmt
mvn clean compile                    # Compile Java code
mvn exec:java -Dexec.mainClass="com.stocker.AppLauncher"  # Run

# Database
psql -U postgres -c "CREATE DATABASE IF NOT EXISTS trading_system;"
psql -U postgres -d trading_system < stocker/cmt/src/main/resources/schema.sql

# Frontend
cd trading-ui
npm install                          # Install dependencies
ng build                             # Build Angular
ng serve --open                      # Start dev server
```

### Verify Installation

```bash
# Check each component
java -version                        # Should show Java 8+
mvn -version                         # Should show Maven 3.6+
psql --version                       # Should show PostgreSQL 12+
node --version                       # Should show Node 14+
ng version                           # Should show Angular version
```

---

## 🚀 RUN THE SYSTEM (3 terminals)

### Terminal 1: Start Backend
```bash
cd stocker/cmt
mvn exec:java -Dexec.mainClass="com.stocker.AppLauncher"
```
**Expected Output:**
```
[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...
[WEBSOCKET] ✓ WebSocket Server started on port 8080
```

### Terminal 2: Start Frontend
```bash
cd trading-ui
ng serve
# Opens http://localhost:4200 automatically
```
**Expected Output:**
```
✔ Compiled successfully.
✔ Build at: <timestamp>
```

### Terminal 3: Connect MiniFix Client
1. Launch MiniFix application
2. Settings:
   - Host: localhost (or 127.0.0.1)
   - Port: 9876
   - SenderCompID: MINIFIX_CLIENT
   - TargetCompID: EXEC_SERVER
3. Click "Connect"

**Expected:**
- ✅ MiniFix shows "Connected"
- ✅ Backend shows "[ORDER SERVICE] ✓ Logon successful"
- ✅ Angular UI shows "🟢 Connected"

---

## 📊 FIRST TRADE (30 SECONDS)

**Step 1: Create Buy Order (MiniFix).**
- Symbol: `GOOG`
- Side: `BUY`
- Qty: `100`
- Price: `150.00`
- Click "Send"

**Step 2: Create Sell Order (MiniFix)**
- Symbol: `GOOG`
- Side: `SELL`
- Qty: `100`
- Price: `150.00`
- Click "Send"

**Step 3: Check Results**
- **Backend Console:** Should show matching messages
- **Angular UI:**
  - Orders Tab: Both orders status = "FILLED"
  - Executions Tab: 1 execution (100 @ $150.00)
  - Options Tab: GOOG call ≈ $12.45, put ≈ $3.22

---

## 🧪 STRESS TEST (2 minutes)

**In MiniFix:**
1. Click "Auto-generate orders"
2. Settings:
   - Rate: 10 orders/sec
   - Duration: 2 minutes
   - Symbols: Rotate GOOG/MSFT/IBM

**Expected Results:**
- ✅ 120 total orders generated
- ✅ ~50-60 trades executed
- ✅ Angular UI responsive (no lag)
- ✅ Backend console shows smooth flow
- ✅ Memory stable at 200-250 MB
- ✅ No crashes or exceptions

---

## 🗄️ DATABASE VERIFICATION

```bash
# Connect to database
psql -U postgres -d trading_system

# Count records
SELECT COUNT(*) as orders FROM orders;
SELECT COUNT(*) as executions FROM executions;

# Sample data
SELECT cl_ord_id, symbol, side, exec_status FROM orders LIMIT 3;
SELECT buy_ord_id, exec_qty, exec_price FROM executions LIMIT 3;

# Exit
\q
```

---

## 📱 DASHBOARD URLS

| Dashboard | URL | Purpose |
|-----------|-----|---------|
| Trading | http://localhost:4200 | All 4 components below |
| Orders | Tab 1 | Submitted orders grid |
| Executions | Tab 2 | Matched trades blotter |
| Options | Tab 3 | Option prices + Greeks |
| Health | Tab 4 | System metrics (Lab 12) |

---

## 🔌 PORT REFERENCE

| Service | Port | Protocol | Status |
|---------|------|----------|--------|
| FIX Acceptor | 9876 | TCP/FIX | Backend |
| WebSocket | 8080 | TCP/WS | Backend |
| Angular Dev | 4200 | HTTP | Frontend |
| PostgreSQL | 5432 | TCP | Database |

---

## ⚠️ TROUBLESHOOTING

### Backend won't start
```bash
# Check if port 9876 is free
lsof -i :9876

# Kill process using port (if needed)
kill -9 <PID>

# Rebuild and retry
mvn clean compile
```

### Angular UI shows errors
```bash
# Hard refresh browser
Ctrl+Shift+R (Windows) or Cmd+Shift+R (Mac)

# Or clear and rebuild
cd trading-ui
rm -rf dist node_modules
npm install
ng build
```

### Database connection fails
```bash
# Test PostgreSQL connection
psql -U postgres

# If failed, check if PostgreSQL running
# Windows: services.msc → PostgreSQL → Start
# Mac: brew services start postgresql@12
# Linux: sudo systemctl start postgresql

# Recreate database if needed
psql -U postgres -d template1
DROP DATABASE IF EXISTS trading_system;
CREATE DATABASE trading_system;
\q

# Load schema
psql -U postgres -d trading_system < schema.sql
```

### No trades matching
- Check exact prices (150 vs 150.0)
- Verify symbols are valid (GOOG, MSFT, IBM, etc.)
- Check order quantity > 0
- Check order side in MiniFix (BUY vs SELL)

---

## 📈 PERFORMANCE EXPECTATIONS

| Metric | Value |
|--------|-------|
| Order Latency | 2-3 ms |
| Orders/Second | 10+ |
| Memory Usage | 200-250 MB |
| Database Write | 5-10 ms (async) |
| WebSocket Update | <1 ms |
| Fill Rate | ~50% (depends on pricing) |

---

## 🎯 DEMO SCRIPT (5 minutes)

**"Here's a live trading system handling everything in real-time..."**

1. **Send Buy Order** (in MiniFix)
   - "Order appears in dashboard immediately"
   - Point to UI: Orders grid shows BUY GOOG

2. **Send Sell Order** (in MiniFix)
   - "System matches them automatically"
   - Point to UI: Status changes to FILLED

3. **Check Execution** (Angular UI)
   - "Here's the matched trade"
   - Show Executions tab

4. **View Options** (Angular UI)
   - "System calculates option prices automatically"
   - Show Options tab with Greeks

5. **Check Health** (Angular UI)
   - "System monitoring and health dashboard"
   - Show Health tab

6. **Verify Database** (Terminal)
   - "All trades persisted for audit trail"
   - Run SELECT queries

7. **Explain Architecture** (show diagram in documentation)
   - "This is a 3-tier microservices system"

---

## 📦 KEY FILES TO KNOW

| File | Purpose |
|------|---------|
| `stocker/cmt/order-service.cfg` | FIX configuration |
| `stocker/cmt/pom.xml` | Java dependencies |
| `trading-ui/package.json` | Angular dependencies |
| `trading-ui/src/app/app.ts` | Angular main component |
| `.../schema.sql` | Database schema |
| `LAB12_CAPSTONE_DEMO_GUIDE.md` | Full demo guide (read this!) |
| `LAB12_IMPLEMENTATION_GUIDE.md` | Integration instructions |

---

## ✅ PRE-DEMO CHECKLIST (30 min before)

- [ ] Database running: `psql -U postgres -c "SELECT 1"`
- [ ] Schema loaded: `psql -U postgres -d trading_system -c "\dt"`
- [ ] Maven build succeeds: `cd stocker/cmt && mvn clean compile`
- [ ] Angular builds: `cd trading-ui && ng build`
- [ ] Backend starts without errors (dry run)
- [ ] Angular loads: http://localhost:4200
- [ ] MiniFix configured and ready
- [ ] Test order flow works

---

## 🎓 WHAT YOU'RE DEMONSTRATING

✅ **Lab 1-3:** Order ingestion and validation (FIX protocol)
✅ **Lab 4:** Real-time WebSocket delivery to UI
✅ **Lab 5:** Database persistence (PostgreSQL async queue)
✅ **Lab 6-7:** Order matching with price-time priority
✅ **Lab 8:** Two-way execution reporting
✅ **Lab 9:** Performance telemetry (latency tracking)
✅ **Lab 10:** Session recovery on disconnect
✅ **Lab 11:** Black-Scholes option pricing + Greeks
✅ **Lab 12:** System health monitoring + 3-tier architecture

---

## 🚀 ADVANCED OPTIONS

### Stop All Services
```bash
# Kill backend (Ctrl+C in terminal 1)
# Kill Angular (Ctrl+C in terminal 2)
# Disconnect MiniFix (click Disconnect)
# All gracefully shut down
```

### Reset for Fresh Demo
```bash
# Clear orders from database
psql -U postgres -d trading_system -c "DELETE FROM executions; DELETE FROM orders;"

# Verify cleared
psql -U postgres -d trading_system -c "SELECT COUNT(*) FROM orders;"  # Should show 0
```

### View Detailed Logs
```bash
# Java backend logs go to console (terminal 1)
# FIX logs stored in: stocker/cmt/logs/store/
# Look for: FIX.4.4-EXEC_SERVER-MINIFIX_CLIENT.* files
```

### Performance Benchmark
```bash
# Run Python performance test (requires Python 3)
cd testing
pip install -r requirements.txt
python latency_throughput_test.py
# Generates: performance_results_<timestamp>.csv
```

---

## 📞 QUICK HELP

**"I don't understand the system architecture"**
→ Read: [README_CAPSTONE.md](README_CAPSTONE.md) - System Architecture section

**"How do I run the full demonstration?"**
→ Read: [LAB12_CAPSTONE_DEMO_GUIDE.md](LAB12_CAPSTONE_DEMO_GUIDE.md)

**"How do I integrate SystemHealthMonitor?"**
→ Read: [LAB12_IMPLEMENTATION_GUIDE.md](LAB12_IMPLEMENTATION_GUIDE.md) - Task 12.1-12.4

**"How do I verify everything works?"**
→ Read: [LAB12_SYSTEM_INTEGRATION_CHECKLIST.md](LAB12_SYSTEM_INTEGRATION_CHECKLIST.md)

**"Why did my demo fail?"**
→ Read: [LAB12_CAPSTONE_DEMO_GUIDE.md](LAB12_CAPSTONE_DEMO_GUIDE.md#demo-failure-recovery)

---

## 🎉 YOU'RE READY!

You have everything needed to:
- ✅ Build the entire system
- ✅ Run a live trading demonstration
- ✅ Explain production architecture
- ✅ Show options pricing calculations
- ✅ Demonstrate stress testing
- ✅ Verify database persistence

**Time to demonstrate:** ~5-90 minutes (choose your scope)

Good luck! 🚀

---

**Last Updated:** Lab 12 Complete
**Status:** Ready for Demonstration ✅
**Next Step:** Run the system!

