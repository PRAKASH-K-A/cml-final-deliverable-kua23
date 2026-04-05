import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WebsocketService, Order } from '../../services/websocket.service';
import { Subscription } from 'rxjs';

/**
 * OrderGridComponent - Live Order Blotter
 * 
 * Displays real-time order data received via WebSocket from the backend.
 * Orders appear instantly when sent from MiniFix without page refresh.
 */
@Component({
  selector: 'app-order-grid',
  imports: [CommonModule],
  templateUrl: './order-grid.component.html',
  styleUrl: './order-grid.component.css'
})
export class OrderGridComponent implements OnInit, OnDestroy {
  orders: Order[] = [];
  isConnected: boolean = false;
  buyCount: number = 0;
  sellCount: number = 0;
  
  private messageSubscription?: Subscription;
  private connectionSubscription?: Subscription;

  constructor(
    private wsService: WebsocketService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    console.log('[ORDER-GRID] Component initialized');
    
    // Subscribe to the WebSocket message stream
    this.messageSubscription = this.wsService.getMessages().subscribe({
      next: (newOrder: Order) => {
        console.log('[ORDER-GRID] New order received:', newOrder);
        // Add new order to the top of the list
        this.orders = [newOrder, ...this.orders];
        // Update counters
        if (newOrder.side === '1') {
          this.buyCount++;
        } else {
          this.sellCount++;
        }
        console.log('[ORDER-GRID] Total orders:', this.orders.length, 'Buy:', this.buyCount, 'Sell:', this.sellCount);
        // Manually trigger change detection
        this.cdr.detectChanges();
      },
      error: (error: any) => {
        console.error('[ORDER-GRID] Error receiving order:', error);
      }
    });

    // Subscribe to connection status
    this.connectionSubscription = this.wsService.getConnectionStatus().subscribe({
      next: (status: boolean) => {
        this.isConnected = status;
        console.log('[ORDER-GRID] Connection status:', status ? 'Connected' : 'Disconnected');
        // Manually trigger change detection
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy(): void {
    // Clean up subscriptions to prevent memory leaks
    if (this.messageSubscription) {
      this.messageSubscription.unsubscribe();
    }
    if (this.connectionSubscription) {
      this.connectionSubscription.unsubscribe();
    }
  }

  /**
   * Get human-readable side label
   */
  getSideLabel(side: string): string {
    return side === '1' ? 'BUY' : 'SELL';
  }

  /**
   * Get color class for side
   */
  getSideColor(side: string): string {
    return side === '1' ? 'buy' : 'sell';
  }

  /**
   * Get order type label from FIX code
   */
  getOrderTypeLabel(ordType?: string): string {
    if (!ordType) return 'Limit';
    switch(ordType) {
      case '1': return 'Market';
      case '2': return 'Limit';
      case '3': return 'Stop';
      case '4': return 'Stop-Limit';
      default: return ordType;
    }
  }

  /**
   * Get human-readable order status label
   */
  getStatusLabel(status?: string): string {
    if (!status) return 'NEW';
    switch(status) {
      case '0': return 'NEW';
      case '1': return 'PARTIALLY_FILLED';
      case '2': return 'FILLED';
      default: return status;
    }
  }

  /**
   * Get CSS class for order status
   */
  getStatusClass(status?: string): string {
    if (!status) return 'status-new';
    switch(status) {
      case '0': return 'status-new';
      case '1': return 'status-partial';
      case '2': return 'status-filled';
      default: return 'status-new';
    }
  }

  /**
   * Clear all orders from the grid
   */
  clearOrders(): void {
    this.orders = [];
    this.buyCount = 0;
    this.sellCount = 0;
  }

  /**
   * Calculate total notional value
   */
  getTotalNotional(): number {
    return this.orders.reduce((sum, order) => sum + (order.quantity * order.price), 0);
  }
}
