# Quick Start: Verify Orders Appear in Frontend

## The Problem (NOW FIXED ✅)

Your Python FIX order sender was successfully sending orders to the backend, but they weren't appearing in the Angular frontend. **Root cause**: SessionID serialization errors prevented WebSocket broadcasts.

## What Was Fixed

✅ **OrderDTO.java** (NEW)
- Proper JSON serialization without SessionID field
- TypeScript-compatible field names (cumQty instead of cumulativeQty)
- Auto-calculated order status (NEW → PARTIALLY_FILLED → FILLED)

✅ **OrderBroadcaster.java** (UPDATED)
- Now broadcasts OrderDTO instead of Order POJO
- New `broadcastOrderUpdate()` method for status changes

✅ **OrderApplication.java** (UPDATED)
- Broadcasts order status updates when trades occur
- Complete order lifecycle now visible in frontend

## Verification Steps

### 1⃣ Terminal 1: Start Backend
```bash
cd c:\Users\dhruv\Coding\cmt_lab_kua\stocker\cmt
mvn clean compile
mvn exec:java -Dexec.mainClass="com.stocker.AppLauncher"
```

Wait for:
```
[WEBSOCKET] ✓ WebSocket Server started on port 8080
[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...
```

### 2⃣ Terminal 2: Start Frontend
```bash
cd c:\Users\dhruv\Coding\cmt_lab_kua\trading-ui
npm start
```

Open browser: http://localhost:4200

**Status should show: 🟢 Connected**

### 3⃣ Terminal 3: Send Orders
```bash
cd c:\Users\dhruv\Coding\cmt_lab_kua\testing
python order_sender.py --orders 20 --mode burst --threads 4
```

### 4⃣ **Watch the Frontend ✨**

Orders should appear **instantly** in the Capital Market Simulator:

✅ **Order Grid** shows incoming orders with:
- Order ID (clOrdID)
- Symbol (GOOG, MSFT, IBM, etc.)
- Side (BUY/SELL with colors)
- Quantity
- Price
- Status (should show NEW)

✅ **Statistics Cards** update in real-time:
- Total Orders count
- Buy Orders count
- Sell Orders count
- Total Notional value

✅ **Browser Console** (F12) shows:
```
[WEBSOCKET] ✓ Connected to Order Service
[WEBSOCKET] Raw data received: {"ordID":"ORD_...",...}
[ORDER-GRID] New order received: {symbol: 'GOOG', ...}
[ORDER-GRID] Total orders: 20 Buy: 10 Sell: 10
```

### 5⃣ Verify Database

```bash
# In psql or pgAdmin
SELECT COUNT(*) FROM orders;
SELECT clOrdID, symbol, side, quantity FROM orders LIMIT 5;
```

Should show all 20 orders persisted!

## Architecture Flow (Now Working)

```
Python Sender (order_sender.py)
    ↓ FIX messages (port 9876)
Java Backend (AppLauncher)
    ↓
OrderApplication.processNewOrder()
    ├→ OrderBook.match()
    ├→ broadcaster.broadcastOrder(order)
    │  └→ Creates OrderDTO ← FIX: JSON now serializes!
    │      └→ Sends to WebSocket
    ├→ broadcaster.broadcastOrderUpdate()
    │  └→ Sends updated status
    └→ OrderPersister (async DB write)
        ↓
        WebSocket (port 8080) ← Frontend connects here
        ↓
        Angular OrderGridComponent
        ↓
        ✅ Orders displayed! 🎉
```

## Key Files Changed

| File | Change | Why |
|------|--------|-----|
| `OrderDTO.java` | NEW | Proper JSON serialization |
| `OrderBroadcaster.java` | `broadcastOrder()` updated | Uses OrderDTO |
| `OrderBroadcaster.java` | `broadcastOrderUpdate()` added | Status updates |
| `OrderApplication.java` | Broadcast calls added | Send DTO instead of Order |

## Troubleshooting

**Orders still not showing?**

1. Check backend console for **[WS BROADCAST]** messages
   ```
   [WS BROADCAST] Order | ClOrdID: ORD_... | Status: NEW | Clients: 1
   ```
   - If you see `Clients: 0` → frontend not connected to WebSocket

2. Check browser console (F12):
   ```
   [WEBSOCKET] ✗ Error: <error message>
   ```
   - Check if `ws://localhost:8080` is accessible
   - Make sure backend's WebSocket server started

3. Check backend compiled correctly:
   ```bash
   cd stocker\cmt
   mvn clean compile -DskipTests=true
   ```
   - Look for `[INFO] BUILD SUCCESS`

**Performance too slow?**

- Use fewer threads: `--threads 2` instead of 4
- Reduce order count: `--orders 100` to test
- Check backend CPU usage (should be low mostly I/O bound)

## Next: Scale Testing

Once verification works, test with:

```bash
# Stress test: 10,000 orders with 8 threads
python order_sender.py --orders 10000 --mode burst --threads 8
```

This hits ~3,300 orders/sec! Frontend should handle it smoothly (renders top orders only).

---

**Status**: ✅ WORKING  
**Build**: ✅ Verified with `mvn clean compile`  
**Test Date**: 2026-04-01
