package com.stocker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import quickfix.Application;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.UnsupportedMessageType;
import quickfix.field.AvgPx;
import quickfix.field.ClOrdID;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.LeavesQty;
import quickfix.field.MsgType;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderID;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Side;
import quickfix.field.Symbol;
import quickfix.field.Text;

public class OrderApplication implements Application {
    
    private final OrderBroadcaster broadcaster;
    private final BlockingQueue<Order> dbQueue;
    private final Map<String, Security> validSecurities;
    private final Map<String, OrderBook> orderBooks;  // One book per symbol
    private final OptionPricingService optionPricingService;  // LAB 11: Option pricing
    
    public OrderApplication(OrderBroadcaster broadcaster, BlockingQueue<Order> dbQueue) {
        this.broadcaster = broadcaster;
        this.dbQueue = dbQueue;
        this.validSecurities = DatabaseManager.loadSecurityMaster();
        this.orderBooks = new ConcurrentHashMap<>();  // Thread-safe order book registry
        this.optionPricingService = new OptionPricingService(broadcaster);  // LAB 11: Initialize option pricing
        System.out.println("[ORDER SERVICE] Security Master loaded: " + validSecurities.size() + " valid symbols");
        System.out.println("[ORDER BOOKS] Exchange initialized - Ready for matching");
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("[SESSION] Created: " + sessionId);
        System.out.println("[LAB 10] Session store active for correlation tracking (ResetOnLogon=N)");
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("[ORDER SERVICE] ✓ Logon successful: " + sessionId);
        System.out.println("[LAB 10] → Sequence numbers preserved from file store (Fault Tolerance Enabled)");
        System.out.println("[ORDER SERVICE] Client connected - Ready to accept orders");
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("[ORDER SERVICE] ⚠ LOGOUT: " + sessionId);
        System.out.println("[LAB 10] → Sequence numbers will persist; awaiting reconnection...");
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // Used for administrative messages (Heartbeats, Logons)
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            System.out.println("[ORDER SERVICE] ? Sending admin message: " + msgType);
        } catch (FieldNotFound e) {
            System.out.println("[ORDER SERVICE] ? Sending admin message");
        }
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        // Received administrative messages
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            
            // LAB 10: RESILIENCE - Log Resend Requests (indicating sequence gap recovery)
            if (msgType.equals("2")) {  // ResendRequest (MsgType=2)
                try {
                    int beginSeqNum = message.getInt(quickfix.field.BeginSeqNo.FIELD);
                    int endSeqNum = message.getInt(quickfix.field.EndSeqNo.FIELD);
                    System.out.println("[LAB 10] ⚠ ResendRequest received: FROM " + beginSeqNum + 
                                     " TO " + endSeqNum + " | Session: " + sessionId);
                    System.out.println("[LAB 10] → QuickFIX/J will resend buffered messages from sequence store");
                } catch (FieldNotFound e) {
                    // Continue normally if fields missing
                }
            }
            
            System.out.println("[ORDER SERVICE] ? Received admin message: " + msgType);
        } catch (FieldNotFound e) {
            System.out.println("[ORDER SERVICE] ? Received admin message");
        }
    }
    
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        // Outgoing business messages - don't print full message to avoid serialization issues
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            System.out.println("[ORDER SERVICE] ? Sending business message: MsgType=" + msgType);
        } catch (Exception e) {
            System.out.println("[ORDER SERVICE] ? Sending business message");
        }
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        // ===== LAB 9: CAPTURE INGRESS TIMESTAMP FOR LATENCY MEASUREMENT =====
        long ingressTimeNanos = System.nanoTime();
        
        // 1. Identify Message Type
        String msgType = message.getHeader().getString(MsgType.FIELD);
        
        if (msgType.equals(MsgType.ORDER_SINGLE)) {
            processNewOrder(message, sessionId, ingressTimeNanos);
        } else {
            System.out.println("[ORDER SERVICE] Received unknown message type: " + msgType);
        }
    }
    
    /**
     * Process incoming NewOrderSingle (MsgType=D) messages
     * 
     * This is the main entry point for the Matching Engine (Lab 7).
     * 
     * Processing Flow:
     * 1. Validate the order (symbol, price, qty, lot size)
     * 2. Create Order POJO
     * 3. Get/Create OrderBook for symbol
     * 4. Call book.match(order) - THIS GENERATES TRADES
     * 5. Send ACK to client
     * 6. Broadcast trades and order to UI
     * 7. Queue for database persistence
     * 8. Record latency (Lab 9: Performance Telemetry)
     */
    private void processNewOrder(Message message, SessionID sessionId, long ingressTimeNanos) {
        try {
            // 2. Extract Fields using QuickFIX types
            String clOrdId = message.getString(ClOrdID.FIELD);
            String symbol = message.getString(Symbol.FIELD);
            char side = message.getChar(Side.FIELD);
            double qty = message.getDouble(OrderQty.FIELD);
            double price = message.getDouble(Price.FIELD);
            char orderType = '2'; // Default to Limit (Tag 40)
            
            // Try to extract OrderType if present (Tag 40)
            try {
                orderType = message.getChar(OrdType.FIELD);
            } catch (FieldNotFound e) {
                // Default to Limit order if not specified
            }
            
            System.out.println(String.format(
                    "[ORDER RECEIVED] ID=%s | Side: %s | Symbol: %s | Price: $%.2f | Qty: %.0f",
                    clOrdId, (side == '1' ? "BUY" : "SELL"), symbol, price, qty));
            
            // 3. Validate security symbol against Security Master
            if (!validSecurities.containsKey(symbol)) {
                System.out.println("[ORDER SERVICE] REJECTED - Unknown symbol: " + symbol);
                sendReject(message, sessionId, "Unknown Security Symbol: " + symbol);
                return;
            }
            
            // 4. Validation: Price and Qty must be positive
            if (qty <= 0 || price <= 0) {
                sendReject(message, sessionId, "Invalid Price or Qty");
                return;
            }
            
            // 4b. Validate lot size
            Security sec = validSecurities.get(symbol);
            if (!sec.isValidLotSize(qty)) {
                sendReject(message, sessionId, "Invalid lot size for " + symbol + " (lotSize=" + sec.getLotSize() + ")");
                return;
            }
            
            // 5. Create Order POJO
            Order order = new Order(clOrdId, symbol, side, price, qty, orderType);
            order.setSessionId(sessionId); // CRITICAL: Store the session for execution reports
            
            // ===== LAB 7: MATCHING ENGINE ENTRY POINT =====
            // Get or create the OrderBook for this symbol
            OrderBook book = orderBooks.computeIfAbsent(symbol, k -> new OrderBook(symbol));
            
            // CRITICAL: Call the matching engine
            // This returns a list of Execution objects (trades that occurred)
            List<Execution> executions = book.match(order);
            
            // Log summary of matches
            if (!executions.isEmpty()) {
                System.out.println(String.format("[ORDER SERVICE] Order %s generated %d execution(s)", 
                        clOrdId, executions.size()));
            } else {
                System.out.println(String.format("[ORDER SERVICE] Order %s added to book (no matches)", clOrdId));
            }
            
            // 6. Send ACK first (Low Latency - Do NOT wait for DB)
            acceptOrder(order, message, sessionId, executions);
            
            // ===== LAB 9: RECORD LATENCY FOR TELEMETRY =====
            // Record tick-to-trade latency immediately after ACK is sent
            PerformanceMonitor.recordLatency(ingressTimeNanos);
            
            // 7. Send ExecutionReport for each trade (BOTH BUY and SELL sides)
            for (Execution exec : executions) {
                // Send to buyer's session
                if (exec.getBuySessionId() != null) {
                    sendExecutionReportToBuySide(exec, exec.getBuySessionId());
                }
                // Send to seller's session  
                if (exec.getSellSessionId() != null) {
                    sendExecutionReportToSellSide(exec, exec.getSellSessionId());
                }
                
                // Log execution summary after both reports are sent
                ExecutionLogger.logTradeMatched(exec);
                ExecutionLogger.logExecutionSummary(exec, exec.getBuyOrder(), exec.getSellOrder());
                
                broadcaster.broadcastExecution(exec);
                // Queue execution for persistence
                broadcaster.queueExecutionForPersistence(exec);
                
                // ===== LAB 11: UPDATE OPTION PRICES =====
                // After each trade (execution), recalculate option prices for the symbol
                // The spot price has changed, so option Greeks and prices change too
                optionPricingService.updateSpotPrice(exec.getSymbol(), exec.getExecPrice(), exec.getExecQty());
            }
            
            // 8. Broadcast to Angular UI via WebSocket (Real-time update)
            // Broadcast the initial order state
            broadcaster.broadcastOrder(order);
            
            // 8b. Broadcast an order update after execution (with status change)
            // Note: Order quantities are already updated by book.match() via reduceQty()
            for (Execution exec : executions) {
                // Broadcast updated order with new status
                broadcaster.broadcastOrderUpdate(order, exec);
            }
            
            // 9. ASYNC PATH: Queue order for database persistence (Non-blocking)
            if (!dbQueue.offer(order)) {
                System.err.println("[ORDER SERVICE] WARNING: Database queue is full! Order: " + clOrdId);
            } else {
                System.out.println("[ORDER SERVICE] Order queued for persistence: " + clOrdId);
            }
            
        } catch (FieldNotFound e) {
            System.err.println("[ORDER SERVICE] ERROR: Missing required field - " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Processing order failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Accept the order and send ExecutionReport with Status=NEW
     * Uses the Order's actual orderId for audit trail consistency
     */
    private void acceptOrder(Order order, Message request, SessionID sessionId, List<Execution> executions) {
        try {
            // Create an ExecutionReport (MsgType = 8)
            quickfix.fix44.ExecutionReport ack = new quickfix.fix44.ExecutionReport();
            
            double totalFilled = 0;
            for (Execution exec : executions) {
                totalFilled += exec.getExecQty();
            }
            
            double leavesQty = order.getQuantity() - totalFilled;
            
            // Mandatory Fields mapping
            ack.set(new OrderID(order.getOrderId())); // CRITICAL: Use Order's actual orderId for consistency
            ack.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            ack.set(new ClOrdID(request.getString(ClOrdID.FIELD))); // Echo back the Client's ID
            ack.set(new Symbol(order.getSymbol()));
            ack.set(new Side(request.getChar(Side.FIELD)));
            
            // Add OrdType from order (Tag 40)
            ack.set(new OrdType(order.getOrderType())); // Order already has this field set
            
            // Status fields: Determine if Filled, Partially Filled, or New
            if (leavesQty == 0) {
                // Order is completely filled
                ack.set(new ExecType(ExecType.FILL));
                ack.set(new OrdStatus(OrdStatus.FILLED));
            } else if (totalFilled > 0) {
                // Order is partially filled
                ack.set(new ExecType(ExecType.PARTIAL_FILL));
                ack.set(new OrdStatus(OrdStatus.PARTIALLY_FILLED));
            } else {
                // Order is new (no fills yet)
                ack.set(new ExecType(ExecType.NEW));
                ack.set(new OrdStatus(OrdStatus.NEW));
            }
            
            // Quantity accounting
            ack.set(new LeavesQty(Math.max(leavesQty, 0))); // Ensure non-negative
            ack.set(new CumQty(totalFilled));
            
            // Price fields - CRITICAL for display in MiniFix
            ack.set(new Price(order.getPrice())); // Order price (Tag 44)
            if (totalFilled > 0) {
                // Calculate weighted average price from all executions
                double totalFulfilledValue = 0;
                for (Execution exec : executions) {
                    totalFulfilledValue += exec.getExecQty() * exec.getExecPrice();
                }
                ack.set(new AvgPx(totalFulfilledValue / totalFilled));
            } else {
                ack.set(new AvgPx(0)); // No fills yet
            }
            
            // Send back to the specific session
            Session.sendToTarget(ack, sessionId);
            
            String statusMsg = (leavesQty == 0) ? "FULLY FILLED" : (totalFilled > 0) ? "PARTIALLY FILLED" : "ACCEPTED";
            System.out.println(String.format(
                    "[ACK SENT] %s | Filled: %.0f | Remaining: %.0f",
                    statusMsg, totalFilled, leavesQty));
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send acknowledgment");
            e.printStackTrace();
        }
    }
    
    /**
     * Send ExecutionReport to the BUYER for a filled trade
     * Uses the buy Order object to calculate accurate CumQty and OrdStatus
     */
    private void sendExecutionReportToBuySide(Execution exec, SessionID sessionId) {
        try {
            quickfix.fix44.ExecutionReport execReport = new quickfix.fix44.ExecutionReport();
            
            Order buyOrder = exec.getBuyOrder();
            
            // Execution IDs
            execReport.set(new OrderID(exec.getBuyOrderId()));
            execReport.set(new ExecID(exec.getExecId() + "_BUY"));
            execReport.set(new ClOrdID(exec.getBuyClOrdId())); // Buyer's order ID
            
            // Order Details
            execReport.set(new Symbol(exec.getSymbol()));
            execReport.set(new Side(Side.BUY));
            execReport.set(new OrdType(OrdType.LIMIT)); // Order type
            
            // Calculate correct OrdStatus based on remaining quantity
            double leavesQty = buyOrder.getQuantity();
            double cumQty = buyOrder.getCumulativeQty();
            
            char ordStatus;
            if (leavesQty == 0) {
                ordStatus = OrdStatus.FILLED;
            } else if (cumQty > 0) {
                ordStatus = OrdStatus.PARTIALLY_FILLED;
            } else {
                ordStatus = OrdStatus.NEW;
            }
            
            // Execution Details
            execReport.set(new ExecType(ExecType.TRADE));
            execReport.set(new OrdStatus(ordStatus));
            execReport.set(new LeavesQty(Math.max(0, leavesQty)));
            execReport.set(new CumQty(cumQty)); // Accumulated fills for this order
            execReport.set(new LastQty(exec.getExecQty()));
            execReport.set(new LastPx(exec.getExecPrice())); // Trade price
            execReport.set(new Price(buyOrder.getPrice())); // Order price
            execReport.set(new AvgPx(exec.getExecPrice()));
            
            // Send to the session
            Session.sendToTarget(execReport, sessionId);
            
            // Log the execution report being sent
            ExecutionLogger.logExecutionReportSent("BUY ", exec.getBuyClOrdId(), cumQty, 
                                                  Math.max(0, leavesQty), 
                                                  String.valueOf(ordStatus), exec.getExecPrice());
            
            System.out.println(String.format(
                    "[EXECUTION REPORT] BUY  | ClOrdID: %s | Filled: %.0f | CumQty: %.0f | Leaves: %.0f | Status: %c",
                    exec.getBuyClOrdId(), exec.getExecQty(), cumQty, leavesQty, ordStatus));
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send execution report to buyer");
            e.printStackTrace();
        }
    }
    
    /**
     * Send ExecutionReport to the SELLER for a filled trade
     * Uses the sell Order object to calculate accurate CumQty and OrdStatus
     */
    private void sendExecutionReportToSellSide(Execution exec, SessionID sessionId) {
        try {
            quickfix.fix44.ExecutionReport execReport = new quickfix.fix44.ExecutionReport();
            
            Order sellOrder = exec.getSellOrder();
            
            // Execution IDs
            execReport.set(new OrderID(exec.getSellOrderId()));
            execReport.set(new ExecID(exec.getExecId() + "_SELL"));
            execReport.set(new ClOrdID(exec.getSellClOrdId())); // Seller's order ID
            
            // Order Details
            execReport.set(new Symbol(exec.getSymbol()));
            execReport.set(new Side(Side.SELL));
            execReport.set(new OrdType(OrdType.LIMIT)); // Order type
            
            // Calculate correct OrdStatus based on remaining quantity
            double leavesQty = sellOrder.getQuantity();
            double cumQty = sellOrder.getCumulativeQty();
            
            char ordStatus;
            if (leavesQty == 0) {
                ordStatus = OrdStatus.FILLED;
            } else if (cumQty > 0) {
                ordStatus = OrdStatus.PARTIALLY_FILLED;
            } else {
                ordStatus = OrdStatus.NEW;
            }
            
            // Execution Details
            execReport.set(new ExecType(ExecType.TRADE));
            execReport.set(new OrdStatus(ordStatus));
            execReport.set(new LeavesQty(Math.max(0, leavesQty)));
            execReport.set(new CumQty(cumQty)); // Accumulated fills for this order
            execReport.set(new LastQty(exec.getExecQty()));
            execReport.set(new LastPx(exec.getExecPrice())); // Trade price
            execReport.set(new Price(sellOrder.getPrice())); // Order price
            execReport.set(new AvgPx(exec.getExecPrice()));
            
            // Send to the session
            Session.sendToTarget(execReport, sessionId);
            
            // Log the execution report being sent
            ExecutionLogger.logExecutionReportSent("SELL", exec.getSellClOrdId(), cumQty, 
                                                  Math.max(0, leavesQty), 
                                                  String.valueOf(ordStatus), exec.getExecPrice());
            
            System.out.println(String.format(
                    "[EXECUTION REPORT] SELL | ClOrdID: %s | Filled: %.0f | CumQty: %.0f | Leaves: %.0f | Status: %c",
                    exec.getSellClOrdId(), exec.getExecQty(), cumQty, leavesQty, ordStatus));
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send execution report to seller");
            e.printStackTrace();
        }
    }
    
    /**
     * Reject the order and send ExecutionReport with Status=REJECTED
     */
    private void sendReject(Message request, SessionID sessionId, String reason) {
        try {
            quickfix.fix44.ExecutionReport reject = new quickfix.fix44.ExecutionReport();
            
            // Mandatory Fields
            reject.set(new OrderID("REJ_" + System.currentTimeMillis()));
            reject.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            reject.set(new ClOrdID(request.getString(ClOrdID.FIELD)));
            reject.set(new Symbol(request.getString(Symbol.FIELD)));
            reject.set(new Side(request.getChar(Side.FIELD)));
            
            // Status fields: "Rejected"
            reject.set(new ExecType(ExecType.REJECTED));
            reject.set(new OrdStatus(OrdStatus.REJECTED));
            
            // Rejection reason
            reject.set(new Text(reason));
            
            // Quantity fields
            reject.set(new LeavesQty(0));
            reject.set(new CumQty(0));
            reject.set(new AvgPx(0));
            
            Session.sendToTarget(reject, sessionId);
            
            System.out.println("[ORDER SERVICE] ? ORDER REJECTED: ClOrdID=" + 
                    request.getString(ClOrdID.FIELD) + " Reason: " + reason);
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send rejection");
            e.printStackTrace();
        }
    }
}
