# LAB 10: QUICK REFERENCE GUIDE

## Resilience Implementation Checklist

### ✅ Configuration Changes (order-service.cfg)

```ini
[DEFAULT]
ResetOnLogon=N          # ← CRITICAL: Was 'Y' (WRONG)
ResetOnLogout=N         # ← NEW
ResetOnDisconnect=N     # ← NEW  
UseLocalTime=Y          # ← NEW
FileStorePath=logs/store       # ← Persists sequences
```

### ✅ Database Resilience (DatabaseManager.java)

**New Method:**
```java
getConnectionWithRetry()  // Exponential backoff: 100ms→200ms→400ms→800ms→1600ms
```

**Modified Methods:**
- `insertOrder()` → uses getConnectionWithRetry()
- `testConnection()` → uses getConnectionWithRetry()
- `updateOrderStatus()` → uses getConnectionWithRetry()
- `updateOrderFill()` → uses getConnectionWithRetry()
- `loadSecurityMaster()` → uses getConnectionWithRetry()
- `loadCustomerMaster()` → uses getConnectionWithRetry()

### ✅ Session Recovery Logging (OrderApplication.java)

**Enhanced Methods:**
- `onCreate()` → Logs session store activation
- `onLogon()` → Shows sequence preservation
- `onLogout()` → Logs graceful shutdown
- `fromAdmin()` → Detects and logs ResendRequest

---

## Key Concepts

### FIX Sequence Numbers (MsgSeqNum / Tag 34)

| Scenario | Action | Result |
|---|---|---|
| First message | MsgSeqNum=1 | Establish baseline |
| Continuous stream | MsgSeqNum=2,3,4,... | Sequential tracking |
| Network disconnect | [Preserved to file] | Session persisted |
| Reconnection | Read from file store | Restored MsgSeqNum |
| Gap detected (Got 5, expected 4) | Send ResendRequest | Recover message 4 |

### Database Retry Strategy

```
Attempt 1: Immediate connection
  ├─ Success? → Return connection
  └─ Fail? → Wait 100ms, try again

Attempt 2: Wait 100ms, retry
  ├─ Success? → Return connection
  └─ Fail? → Wait 200ms, try again

Attempt 3: Wait 200ms, retry
  ├─ Success? → Return connection
  └─ Fail? → Wait 400ms, try again

Attempt 4: Wait 400ms, retry
  ├─ Success? → Return connection
  └─ Fail? → Wait 800ms, try again

Attempt 5: Wait 800ms, retry
  ├─ Success? → Return connection
  └─ Fail? → Throw SQLException

[Meanwhile, OrderPersister keeps queuing orders in memory]
```

### Order Processing During Database Outage

```
Timeline:
t=0s    DB UP - Orders accepted and persisted
t=5s    DB DOWN (PostgreSQL crash)
t=6s    Orders 6-10 arrive from client
        → Order validation OK
        → Try to persist: FAIL (DB unavailable)
        → Retry 5 times with backoff
        → Eventually queue in BlockingQueue
        → Client ACK sent (✅ not blocked)

t=15s   DB recovers (PostgreSQL restart)
        → OrderPersister detects connection available
        → Flushes queued orders (6,7,8,9,10) to DB
        → Complete audit trail preserved

Result: ✅ Zero orders lost, clients unaware of outage
```

---

## Testing Commands

### Start the Order Service
```bash
mvn clean compile exec:java -Dexec.mainClass="com.stocker.AppLauncher"
```

### Check Session Recovery Logs
```bash
tail -f logs/sessions/FIX.4.4-EXEC_SERVER-MINIFIX_CLIENT.session
```

### Monitor Database Connection Retries
```bash
grep "Connection attempt" logs/*.txt
```

### Simulate Database Failure
```bash
# Stop PostgreSQL
sudo systemctl stop postgresql

# [Send orders from MiniFix - observe retry logging]
# [Resolution will be delayed 100-1600ms per retry attempt]

# Restart PostgreSQL
sudo systemctl start postgresql

# Orders should flush automatically
```

### Simulate Network Failure
```bash
# Kill Order Service process
ps aux | grep AppLauncher
kill -9 <PID>

# [MiniFix will detect disconnection]
# [QuickFIX logs showing logout]

# Restart Order Service
java -cp ... AppLauncher

# MiniFix reconnects
# [Observe ResendRequest if needed]
# [Sequence recovery completed]
```

### Verify Data Integrity After Crash
```bash
# Check orders table
psql -U postgres -d trading_system -c "SELECT COUNT(*) FROM orders;"

# Check for duplicates (PossDup handling)
psql -U postgres -d trading_system -c \
  "SELECT cl_ord_id, COUNT(*) FROM orders GROUP BY cl_ord_id HAVING COUNT(*) > 1;"
```

---

## Console Output Patterns

### Healthy Startup
```
[SESSION] Created: FIX.4.4:EXEC_SERVER->MINIFIX_CLIENT
[LAB 10] Session store active for correlation tracking (ResetOnLogon=N)
[ORDER SERVICE] ✓ Logon successful
[LAB 10] → Sequence numbers preserved from file store (Fault Tolerance Enabled)
[DATABASE] ✓ Connected to PostgreSQL [version]
[DATABASE] ✓ Security Master loaded: X securities
[ORDER SERVICE] Client connected - Ready to accept orders
```

### Database Retry (Transient Outage)
```
[DATABASE] ⚠ Connection attempt 1 failed: Connection refused | Retrying in 100ms...
[DATABASE] ⚠ Connection attempt 2 failed: Connection refused | Retrying in 200ms...
[DATABASE] ✓ Recovered after 2 attempts
[DATABASE] ✓ Order persisted: CLI001 (GOOG)
```

### ResendRequest Received (Gap Recovery)
```
[LAB 10] ⚠ ResendRequest received: FROM 5 TO 0
[LAB 10] → QuickFIX/J will resend buffered messages from sequence store
```

### Graceful Logout (Disconnect Expected)
```
[ORDER SERVICE] ⚠ LOGOUT: FIX.4.4:EXEC_SERVER->MINIFIX_CLIENT
[LAB 10] → Sequence numbers will persist; awaiting reconnection...
```

---

## Common Issues & Solutions

| Issue | Symptom | Cause | Solution |
|---|---|---|---|
| Orders rejected after crash | "MsgSeqNum too high" | `ResetOnLogon=Y` | Set `ResetOnLogon=N` |
| Database orders lost | Count mismatch | OrderPersister not draining queue | Check if thread is alive; restart service |
| Duplicate orders in DB | `COUNT(*) > 1` for same ClOrdID | PossDup handling issue | QuickFIX/J handles; check for app-level duplicates |
| Long DB recovery time | 1600+ ms delay | Too many retries or slow DB | Adjust `MAX_RETRY_ATTEMPTS` or `MAX_BACKOFF_MS` |
| Orders not persisting | Empty orders table | Both DB down AND broken retry logic | Verify getConnectionWithRetry() is called |

---

## Files Modified for LAB 10

1. **order-service.cfg** 
   - Added: `ResetOnLogon=N`, `ResetOnLogout=N`, `ResetOnDisconnect=N`, `UseLocalTime=Y`

2. **DatabaseManager.java**
   - New: `getConnectionWithRetry()` with exponential backoff
   - Updated: 6 methods to use resilient connection acquisition

3. **OrderApplication.java**
   - Enhanced: `fromAdmin()` to detect ResendRequest
   - Enhanced: `onCreate()`, `onLogon()`, `onLogout()` with LAB 10 logging

4. **LAB10_RESILIENCE_ASSESSMENT.md** (NEW)
   - Complete implementation documentation
   - 5 test scenarios with expected outputs
   - Configuration reference

---

## Key Metrics to Monitor

| Metric | Target | How to Check |
|---|---|---|
| Message recovery time | < 5s | Time from ResendRequest to message delivered |
| DB reconnection time | < 2s | Time from DB restart to first successful insert |
| Order queue depth | < 100 | Pending orders during DB outage |
| Duplicate detection | 0 duplicates | `SELECT COUNT(*) GROUP BY cl_ord_id HAVING COUNT(*) > 1` |
| Session persistence | 100% | All messages recovered after crash |

---

## Escalation Path for Production

```
Level 1: Transient DB connection
  → Automatically retried with backoff
  ✅ Self-heals in < 2s

Level 2: Extended DB outage (30+ sec)
  → Orders queue in memory
  → Alert: Check database health
  → Manual: Restart PostgreSQL or failover

Level 3: Both primary and passive down
  → Orders start failing validation
  → Alert: Critical - No database available
  → Manual: Restore from backup or incident response

Level 4: FIX session fails repeatedly
  → May indicate network issues or client problem
  → Check: Network connectivity, client logs
  → Manual: Investigate client reconnection failures
```

---

## Compliance & Audit

**Regulatory Requirements Met:**
- ✅ Message sequencing preserved (SEC/FCA audit requirement)
- ✅ Complete order audit trail (on recovery)
- ✅ No orders lost without explicit rejection (zero silent drops)
- ✅ Duplicate detection enabled (PossDup handling)
- ✅ Graceful degradation under load

**For Regulators:**
- All FIX messages logged to: `logs/sessions/*.session`
- All database operations logged to: DatabaseManager
- Session recovery events logged to: Console + QuickFIX logs
- Provide: logs/store/* files for message recovery audit

