# Order Persistence & Frontend Display Fix

## Problem Statement

Orders were being sent to the Java backend via FIX protocol (verified with Python multithreaded sender), but:
- ❌ Orders were NOT appearing in the Angular frontend
- ✓ Backend was receiving and processing orders 
- ✓ Database persistence was working
- ❌ WebSocket broadcast was failing silently

## Root Cause Analysis

### Issue #1: JSON Serialization Failure
**Problem**: The `Order` POJO contains a `SessionID sessionId` field from the QuickFIX library.
- Gson couldn't properly serialize `SessionID` objects to JSON
- This caused the `broadcastOrder()` method to fail silently
- Errors were caught but not propagating to the frontend

**Location**: `OrderBroadcaster.broadcastOrder()` trying to serialize `Order` directly

### Issue #2: Field Name Mismatches  
**Problem**: TypeScript interface and Java Order class used different field names:

| TypeScript Interface | Java Order Field | Issue |
|---|---|---|
| `cumQty` | `cumulativeQty` | Name mismatch prevents proper mapping |
| `ordStatus` | ❌ doesn't exist | Status not tracked in Order POJO |
| `execType` | ❌ doesn't exist | Execution type not available |

**Impact**: Even if JSON serialized correctly, frontend wouldn't receive proper data

### Issue #3: Incomplete Order Data
**Problem**: When orders were first received, they only had basic data:
- `clOrdID`, `symbol`, `side`, `price`, `quantity`
- Missing FIX execution report fields like `ordStatus`, `cumQty`, `leavesQty`
- Frontend template expected these fields

## Solution Implemented

### 1. Created OrderDTO (Data Transfer Object)

**File**: `OrderDTO.java` (NEW)

```java
public class OrderDTO {
    // JSON-safe fields with properly mapped names
    public String ordID;        // From Order.orderId
    public String clOrdID;      // ✓ Matches TypeScript
    public String symbol;       // ✓ Matches TypeScript
    public String side;         // ✓ Converted char to String
    public String ordType;      // ✓ Converted char to String
    public double price;        // ✓ Matches TypeScript
    public double quantity;     // ✓ Matches TypeScript
    
    // NEW: Execution Report fields
    public String ordStatus;    // ✓ "NEW", "PARTIALLY_FILLED", "FILLED"
    public String execType;     // ✓ "NEW", "PARTIAL_FILL", "FILL", "TRADE"
    public double cumQty;       // ✓ Matches TypeScript (not cumulativeQty)
    public double leavesQty;    // ✓ NEW: Calculated from quantity - cumQty
    public double lastQty;      // ✓ NEW: Last execution qty
    public double lastPx;       // ✓ NEW: Last execution price
    public double avgPx;        // ✓ NEW: Average execution price
}
```

**Key Benefits**:
- ✅ NO `SessionID` field (prevents JSON serialization errors)
- ✅ TypeScript-compatible field names (`cumQty` not `cumulativeQty`)
- ✅ Includes all FIX execution report fields
- ✅ Smart status calculation based on fill quantities

### 2. Updated OrderBroadcaster

**Method**: `broadcastOrder(Order order)` - UPDATED

```java
public void broadcastOrder(Order order) {
    // BEFORE: gson.toJson(order) ← SessionID causes error
    // AFTER:
    OrderDTO orderDTO = new OrderDTO(order);  // Creates DTO
    String json = gson.toJson(orderDTO);      // Serializes DTO (no SessionID!)
    broadcast(json);
}
```

**New Method**: `broadcastOrderUpdate(Order order, Execution exec)` - ADDED

```java
public void broadcastOrderUpdate(Order order, Execution execution) {
    OrderDTO orderDTO = new OrderDTO(order, execution, true);
    String json = gson.toJson(orderDTO);
    broadcast(json);
    // Sends order with updated status, cumQty, lastPx, etc.
}
```

### 3. Updated OrderApplication

**Location**: `processNewOrder()` method

```java
// Original flow:
broadcastOrder(order);  // ← This was failing silently

// Fixed flow:
broadcastOrder(order);                    // Broadcast initial order state
for (Execution exec : executions) {
    broadcaster.broadcastOrderUpdate(order, exec);  // Broadcast status changes
}
```

## Data Flow (Fixed)

```
FIX Client (Python sender)
    ↓
Backend FIX Engine (port 9876)
    ↓
OrderApplication.processNewOrder()
    ├→ OrderBook.match()           [Matches buy/sell orders]
    ├→ broadcaster.broadcastOrder()    [Sends initial order state]
    ├→ For each Execution:
    │  ├→ Send ExecutionReport (FIX)
    │  └→ broadcaster.broadcastOrderUpdate()  [Updates status]
    ├→ broadcaster.queueExecutionForPersistence()
    └→ dbQueue.offer(order)       [Queue for DB]
        ↓
        OrderBroadcaster (WebSocket port 8080)
        {
            "ordID": "ORD_abc123",
            "clOrdID": "ORD_1672345600000_1",
            "symbol": "GOOG",
            "side": "1",
            "ordType": "2",
            "price": 140.50,
            "quantity": 100,
            "ordStatus": "NEW",        // ← Now populated!
            "cumQty": 0,               // ← Field name fixed!
            "leavesQty": 100,          // ← Now calculated!
            // ... other execution fields
        }
        ↓
        Angular WebSocket Service
        ↓
        OrderGridComponent.orders[]
        ↓
        ✅ Frontend displays order!
```

## Testing the Fix

### Step 1: Start the Backend
```bash
cd stocker\cmt
mvn clean compile
mvn exec:java -Dexec.mainClass="com.stocker.AppLauncher"
```

Expected output:
```
[WEBSOCKET] ✓ WebSocket Server started on port 8080
[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...
```

### Step 2: Start the Angular Frontend
```bash
cd trading-ui
npm start
# Navigate to http://localhost:4200
```

Check browser console for:
```
[WEBSOCKET] ✓ Connected to Order Service
[ORDER-GRID] Component initialized
```

### Step 3: Send Orders via Python Script
```bash
cd testing
python order_sender.py --orders 10 --mode burst --threads 4
```

### Step 4: Verify in Frontend

✅ **In browser** (trading-ui):
- "Capital Market Simulator" page loads
- Status shows "Connected" (green indicator)
- Orders appear in the table in real-time
- Buy/Sell counts increment
- Total Notional updates

✅ **In backend console** (AppLauncher):
```
[ORDER RECEIVED] ID=ORD_1672345600000_1 | Side: BUY | Symbol: GOOG | Price: $140.50 | Qty: 100
[WS BROADCAST] Order | ClOrdID: ORD_1672345600000_1 | Status: NEW | Clients: 1
[ORDER SERVICE] Order queued for persistence: ORD_1672345600000_1
[PERSISTENCE] Order #1 persisted in 245 μs | Queue size: 0 | ClOrdID: ORD_1672345600000_1
```

✅ **In browser console** (F12 → Console):
```
[WEBSOCKET] Raw data received: {"ordID":"ORD_abc123",...}
[WEBSOCKET] Parsed order: {symbol: 'GOOG', side: '1', quantity: 100, ...}
[ORDER-GRID] New order received: {ordID: 'ORD_abc123', ...}
[ORDER-GRID] Total orders: 1 Buy: 1 Sell: 0
```

## Database Verification

Orders should appear in PostgreSQL:

```sql
SELECT clOrdID, symbol, side, quantity, status, timestamp 
FROM orders 
ORDER BY timestamp DESC 
LIMIT 10;
```

**Expected**: All orders from Python sender appear with status='NEW'

## Files Modified

1. **NEW**: `OrderDTO.java` - Data transfer object for JSON
2. **MODIFIED**: `OrderBroadcaster.java` - Added `broadcastOrderUpdate()` method
3. **MODIFIED**: `OrderApplication.java` - Added order update broadcasts after executions
4. **RECOMPILED**: All changes compiled successfully (mvn clean compile)

## Why This Works

| Issue | Fix | Result |
|---|---|---|
| SessionID serialization | Use OrderDTO (excludes SessionID) | ✅ No more JSON errors |
| Field name mismatches | Map Order fields to TypeScript names | ✅ Frontend receives correct data |
| Missing execution fields | Calculate status/quantities in OrderDTO | ✅ Frontend has complete order info |
| Status not updating | Added broadcastOrderUpdate() | ✅ Frontend shows order lifecycle |

## Performance Impact

- ✅ **No performance degradation**: OrderDTO creation is negligible
- ✅ **Reduced network traffic**: DTO is smaller than full Order POJO
- ✅ **Improved compatibility**: TypeScript interface now matches perfectly

## Next Steps (Optional)

1. **Add REST API** endpoint to retrieve historical orders
   - `GET /api/orders` - returns all orders from database
   - Useful for page refresh/recovery

2. **Add order book depth** visualization
   - Show bid/ask levels
   - Animate order matching

3. **Add execution reports** table
   - Show completed trades separately
   - Display fill prices and times

4. **Add WebSocket reconnection** logic
   - Handle network interruptions gracefully

## References

- [Lab 7: Execution Reporting](LAB7_IMPLEMENTATION.md)
- [WebSocket Architecture](OrderBroadcaster.java)
- [Order Processing Flow](OrderApplication.java)
- [FIX Protocol Integration](order_sender.py)

---

**Status**: ✅ FIXED & TESTED  
**Build**: ✅ `mvn clean compile` successful  
**Test Date**: 2026-04-01
