package com.stocker;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * OrderBroadcaster - WebSocket Server for Real-time Order Broadcasting
 * 
 * This server maintains persistent connections with Angular UI clients
 * and pushes order data in JSON format whenever new orders arrive via FIX.
 */
public class OrderBroadcaster extends WebSocketServer {
    
    private final Gson gson;
    
    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
        // Configure Gson with custom serializers to handle Java time types
        this.gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (instant, type, context) -> 
                context.serialize(instant.toString()))
            .create();
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientAddress = conn.getRemoteSocketAddress().toString();
        System.out.println("[WEBSOCKET] ✓ UI Connected: " + clientAddress);
        System.out.println("[WEBSOCKET] Active connections: " + getConnections().size());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        // Generally, we don't expect messages FROM the UI in this lab
        // But we can log them if they arrive
        System.out.println("[WEBSOCKET] Received message from UI: " + message);
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientAddress = conn.getRemoteSocketAddress().toString();
        System.out.println("[WEBSOCKET] ✗ UI Disconnected: " + clientAddress);
        System.out.println("[WEBSOCKET] Reason: " + reason + " (Code: " + code + ")");
        System.out.println("[WEBSOCKET] Active connections: " + getConnections().size());
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WEBSOCKET] ERROR: " + ex.getMessage());
        ex.printStackTrace();
        
        if (conn != null) {
            System.err.println("[WEBSOCKET] Error on connection: " + conn.getRemoteSocketAddress());
        }
    }
    
    @Override
    public void onStart() {
        System.out.println("[WEBSOCKET] ✓ WebSocket Server started on port " + getPort());
        System.out.println("[WEBSOCKET] Ready to accept UI connections on ws://localhost:" + getPort());
    }
    
    /**
     * Broadcast an Order object to all connected UI clients
     * Converts the Order POJO to OrderDTO to JSON and sends to all active connections
     * Uses OrderDTO to ensure proper JSON serialization (excludes non-serializable SessionID)
     * 
     * @param order The Order object to broadcast
     */
    public void broadcastOrder(Order order) {
        try {
            // Convert Order object to OrderDTO (JSON-safe) then to JSON string
            OrderDTO orderDTO = new OrderDTO(order);
            String json = gson.toJson(orderDTO);
            
            // Send to all connected UIs
            broadcast(json);
            
            System.out.println(String.format(
                    "[WS BROADCAST] Order | ClOrdID: %s | Status: %s | Clients: %d",
                    order.getClOrdID(), orderDTO.ordStatus, getConnections().size()));
        } catch (Exception e) {
            System.err.println("[WEBSOCKET] ERROR: Failed to broadcast order - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Broadcast an Order with updated status after execution
     * Used when an order has been partially or fully filled
     * 
     * @param order The Order object with updated quantities
     * @param execution The Execution that caused the update
     */
    public void broadcastOrderUpdate(Order order, Execution execution) {
        try {
            // Convert Order object to OrderDTO with execution context
            OrderDTO orderDTO = new OrderDTO(order, execution, true);
            String json = gson.toJson(orderDTO);
            
            // Send to all connected UIs
            broadcast(json);
            
            System.out.println(String.format(
                    "[WS BROADCAST] Order Update | ClOrdID: %s | CumQty: %.0f | Status: %s | Clients: %d",
                    order.getClOrdID(), order.getCumulativeQty(), orderDTO.ordStatus, getConnections().size()));
        } catch (Exception e) {
            System.err.println("[WEBSOCKET] ERROR: Failed to broadcast order update - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Broadcast an Execution object to all connected UI clients
     * Used to push trade execution events to the UI in real-time
     * 
     * @param execution The Execution object (trade) to broadcast
     */
    public void broadcastExecution(Execution execution) {
        try {
            // Convert Execution object to JSON string with a type marker for UI routing
            String json = "{\"type\":\"execution\"," + 
                         "\"data\":" + gson.toJson(execution) + "}";
            
            // Send to all connected UIs
            broadcast(json);
            
            // Log the broadcast with client count
            ExecutionLogger.logExecutionBroadcast(execution, getConnections().size());
            
            System.out.println(String.format(
                    "[WS BROADCAST] Execution | ExecID: %s | Qty: %.0f @ $%.2f | Clients: %d",
                    execution.getExecId(), execution.getExecQty(), execution.getExecPrice(),
                    getConnections().size()));
        } catch (Exception e) {
            System.err.println("[WEBSOCKET] ERROR: Failed to broadcast execution - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Queue an Execution for asynchronous persistence to the database
     * CRITICAL: This is called from the matching engine path to ensure trades are stored
     * 
     * @param execution The Execution object to persist
     */
    public void queueExecutionForPersistence(Execution execution) {
        // Use a background thread to persist asynchronously (non-blocking)
        new Thread(() -> {
            try {
                // Log before persistence
                ExecutionLogger.logExecutionPersisted(execution);
                
                // Persist to database
                DatabaseManager.insertExecution(execution);
                System.out.println("[PERSISTENCE] ✓ Execution recorded for both counterparties: " + execution.getExecId());
            } catch (Exception e) {
                System.err.println("[PERSISTENCE] ERROR: Failed to persist execution - " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
