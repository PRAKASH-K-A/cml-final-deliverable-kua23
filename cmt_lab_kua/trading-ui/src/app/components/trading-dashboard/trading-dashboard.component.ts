import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WebsocketService, Order, Execution } from '../../services/websocket.service';
import { Subscription } from 'rxjs';

/**
 * TradingDashboardComponent - Unified Trading Dashboard
 * 
 * Combines Order Grid and Executions display into a single cohesive interface.
 * Shows real-time orders, executions, and trading metrics.
 */
@Component({
  selector: 'app-trading-dashboard',
  imports: [CommonModule],
  templateUrl: './trading-dashboard.component.html',
  styleUrl: './trading-dashboard.component.css'
})
export class TradingDashboardComponent implements OnInit, OnDestroy {
  // Tab navigation
  activeTab: 'orders' | 'executions' = 'orders';

  // Orders data
  orders: Order[] = [];
  buyCount: number = 0;
  sellCount: number = 0;

  // Executions data
  executions: Execution[] = [];
  totalSharesTraded: number = 0;
  totalTradeValue: number = 0;

  // Connection status
  isConnected: boolean = false;

  // Subscriptions
  private orderSubscription?: Subscription;
  private executionSubscription?: Subscription;
  private connectionSubscription?: Subscription;

  constructor(
    private wsService: WebsocketService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    console.log('[DASHBOARD] Trading Dashboard initialized');

    // Subscribe to orders
    this.orderSubscription = this.wsService.getMessages().subscribe({
      next: (newOrder: Order) => {
        console.log('[DASHBOARD] New order received:', newOrder);
        this.orders = [newOrder, ...this.orders];
        if (newOrder.side === '1') {
          this.buyCount++;
        } else {
          this.sellCount++;
        }
        this.cdr.detectChanges();
      }
    });

    // Subscribe to executions
    this.executionSubscription = this.wsService.getExecutions().subscribe({
      next: (exec: Execution) => {
        console.log('[DASHBOARD] New execution received:', exec);
        this.executions = [exec, ...this.executions];
        this.totalSharesTraded += exec.execQty;
        this.totalTradeValue += exec.execQty * exec.execPrice;
        this.cdr.detectChanges();
      }
    });

    // Subscribe to connection status
    this.connectionSubscription = this.wsService.getConnectionStatus().subscribe({
      next: (status: boolean) => {
        this.isConnected = status;
        console.log('[DASHBOARD] Connection status:', status ? 'Connected' : 'Disconnected');
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.orderSubscription) this.orderSubscription.unsubscribe();
    if (this.executionSubscription) this.executionSubscription.unsubscribe();
    if (this.connectionSubscription) this.connectionSubscription.unsubscribe();
  }

  /**
   * Switch active tab
   */
  switchTab(tab: 'orders' | 'executions'): void {
    this.activeTab = tab;
    this.cdr.detectChanges();
  }

  /**
   * Get side label
   */
  getSideLabel(side: string): string {
    return side === '1' ? 'BUY' : 'SELL';
  }

  /**
   * Get side color
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
   * Get status label
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
   * Get status CSS class
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
   * Format price
   */
  formatPrice(price: number): string {
    return price.toFixed(2);
  }

  /**
   * Format time
   */
  formatTime(timestamp: string): string {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  }

  /**
   * Get average execution price
   */
  getAveragePrice(): number {
    if (this.totalSharesTraded === 0) return 0;
    return this.totalTradeValue / this.totalSharesTraded;
  }

  /**
   * Clear all orders
   */
  clearOrders(): void {
    this.orders = [];
    this.buyCount = 0;
    this.sellCount = 0;
  }

  /**
   * Clear all executions
   */
  clearExecutions(): void {
    this.executions = [];
    this.totalSharesTraded = 0;
    this.totalTradeValue = 0;
  }

  /**
   * Get total notional value of orders
   */
  getTotalOrderNotional(): number {
    return this.orders.reduce((sum, order) => sum + (order.quantity * order.price), 0);
  }
}
