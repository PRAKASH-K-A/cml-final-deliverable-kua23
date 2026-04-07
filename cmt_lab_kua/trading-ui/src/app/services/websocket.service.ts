import { Injectable } from '@angular/core';
import { Subject, BehaviorSubject, Observable } from 'rxjs';

/**
 * Order interface matching FIX ExecutionReport fields
 * Includes both order and execution status information
 */
export interface Order {
  // Order Identifiers
  clOrdID: string;
  ordID?: string;
  
  // Security & Side
  symbol: string;
  side: string;  // '1' for BUY, '2' for SELL
  
  // Order Type and Price
  ordType?: string;        // Order Type (1=Market, 2=Limit, etc.)
  price: number;
  quantity: number;
  
  // Execution Report Fields (FIX)
  ordStatus?: string;      // NEW, PARTIALLY_FILLED, FILLED
  execType?: string;       // NEW, PARTIAL_FILL, FILL, TRADE
  cumQty?: number;         // Cumulative Quantity filled
  leavesQty?: number;      // Remaining Quantity
  lastQty?: number;        // Last execution quantity
  lastPx?: number;         // Last execution price
  avgPx?: number;          // Average execution price
  execID?: string;         // Execution ID
  execTime?: string;       // Execution timestamp
}

/**
 * Execution interface - represents a completed trade
 */
export interface Execution {
  execId: string;
  buyOrderId: string;
  sellOrderId: string;
  buyClOrdId: string;
  sellClOrdId: string;
  symbol: string;
  execQty: number;
  execPrice: number;
  execTime: string;
}

/**
 * LAB 11: OptionPrice interface - represents option pricing data
 */
export interface OptionPrice {
  symbol: string;
  spotPrice: number;
  strikePrice: number;
  callPrice: number;
  putPrice: number;
  delta: number;
  gamma: number;
  vega: number;
  theta: number;
  rho: number;
  volatility: number;
  timeToExpiration: number;
  timestamp: string;
  lastTradeQty: number;
  lastTradePrice: number;
}

/**
 * WebSocketService - Manages real-time connection to Java Order Service
 * 
 * This service establishes a persistent WebSocket connection to the backend
 * and provides Observable streams for both orders and executions(trades).
 */
@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private socket: WebSocket | null = null;
  
  // Subjects for different message types
  public orders: Subject<Order> = new Subject<Order>();
  public executions: Subject<Execution> = new Subject<Execution>();
  public optionPrices: Subject<OptionPrice> = new Subject<OptionPrice>();  // LAB 11
  
  // Legacy support
  public messages: Subject<Order> = this.orders;
  
  // Connection status tracking - BehaviorSubject emits current value to new subscribers
  public connectionStatus: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);

  constructor() {
    this.connect();
  }

  /**
   * Establish WebSocket connection to the backend
   */
  private connect(): void {
    // Prevent multiple connection attempts
    if (this.socket && this.socket.readyState === WebSocket.CONNECTING) {
      console.log('[WEBSOCKET] Connection already in progress...');
      return;
    }

    try {
      this.socket = new WebSocket('ws://localhost:8080');

      this.socket.onopen = (event) => {
        console.log('[WEBSOCKET] ✓ Connected to Order Service');
        this.connectionStatus.next(true);
      };

      this.socket.onmessage = (event) => {
        console.log('[WEBSOCKET] Raw data received:', event.data);
        try {
          const data = JSON.parse(event.data);
          
          // Check if this is an execution (trade) message
          if (data.type === 'execution' && data.data) {
            const execution = data.data as Execution;
            console.log('[WEBSOCKET] Parsed execution:', execution);
            this.executions.next(execution);
          } 
          // LAB 11: Check if this is an option price message
          else if (data.type === 'option_price' && data.data) {
            const optionPrice = data.data as OptionPrice;
            console.log('[LAB 11] Parsed option price:', optionPrice);
            this.optionPrices.next(optionPrice);
          }
          else {
            // Treat as order message
            const order = data as Order;
            console.log('[WEBSOCKET] Parsed order:', order);
            this.orders.next(order);
          }
        } catch (error) {
          console.error('[WEBSOCKET] Failed to parse message data:', error);
        }
      };

      this.socket.onerror = (error) => {
        console.error('[WEBSOCKET] ✗ Error:', error);
        this.connectionStatus.next(false);
      };

      this.socket.onclose = (event) => {
        console.log('[WEBSOCKET] ✗ Connection closed. Code:', event.code, 'Reason:', event.reason);
        this.connectionStatus.next(false);
        
        // Only reconnect if not a normal closure (1000) or going away (1001)
        if (event.code !== 1000 && event.code !== 1001) {
          console.log('[WEBSOCKET] Attempting to reconnect in 5 seconds...');
          setTimeout(() => this.connect(), 5000);
        }
      };
    } catch (error) {
      console.error('[WEBSOCKET] Failed to establish connection:', error);
      this.connectionStatus.next(false);
    }
  }

  /**
   * Get the orders Observable
   */
  getMessages(): Observable<Order> {
    return this.orders.asObservable();
  }

  /**
   * Get the executions (trades) Observable
   */
  getExecutions(): Observable<Execution> {
    return this.executions.asObservable();
  }

  /**
   * LAB 11: Get the option prices Observable
   */
  getOptionPrices(): Observable<OptionPrice> {
    return this.optionPrices.asObservable();
  }

  /**
   * Get the connection status Observable
   */
  getConnectionStatus(): Observable<boolean> {
    return this.connectionStatus.asObservable();
  }

  /**
   * Close the WebSocket connection
   */
  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }
}
