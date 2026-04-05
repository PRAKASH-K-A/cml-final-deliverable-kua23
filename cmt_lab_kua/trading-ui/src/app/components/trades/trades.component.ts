import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WebsocketService, Execution } from '../../services/websocket.service';
import { Subscription } from 'rxjs';

/**
 * TradesComponent - Live Trade Execution Blotter
 * 
 * Displays real-time trades (executions) received via WebSocket from the backend.
 * Trades appear instantly as orders are matched by the matching engine.
 */
@Component({
  selector: 'app-trades',
  imports: [CommonModule],
  templateUrl: './trades.component.html',
  styleUrl: './trades.component.css'
})
export class TradesComponent implements OnInit, OnDestroy {
  trades: Execution[] = [];
  isConnected: boolean = false;
  totalTradeValue: number = 0;
  totalShares: number = 0;
  
  private executionSubscription?: Subscription;
  private connectionSubscription?: Subscription;

  constructor(
    private wsService: WebsocketService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit(): void {
    console.log('[TRADES-GRID] Component initialized');
    
    // Subscribe to the WebSocket execution stream
    this.executionSubscription = this.wsService.getExecutions().subscribe({
      next: (newExecution: Execution) => {
        console.log('[TRADES-GRID] New execution (trade) received:', newExecution);
        // Add new trade to the top of the list
        this.trades = [newExecution, ...this.trades];
        
        // Update statistics
        this.totalShares += newExecution.execQty;
        this.totalTradeValue += newExecution.execQty * newExecution.execPrice;
        
        console.log('[TRADES-GRID] Total trades:', this.trades.length, 'Shares:', this.totalShares, 
                    'Value: $' + this.totalTradeValue.toFixed(2));
        
        // Manually trigger change detection
        this.cdr.detectChanges();
      },
      error: (error: any) => {
        console.error('[TRADES-GRID] Error receiving execution:', error);
      }
    });

    // Subscribe to connection status
    this.connectionSubscription = this.wsService.getConnectionStatus().subscribe({
      next: (status: boolean) => {
        this.isConnected = status;
        console.log('[TRADES-GRID] Connection status:', status ? 'Connected' : 'Disconnected');
        // Manually trigger change detection
        this.cdr.detectChanges();
      }
    });
  }

  ngOnDestroy(): void {
    // Clean up subscriptions to prevent memory leaks
    if (this.executionSubscription) {
      this.executionSubscription.unsubscribe();
    }
    if (this.connectionSubscription) {
      this.connectionSubscription.unsubscribe();
    }
  }

  /**
   * Format price for display
   */
  formatPrice(price: number): string {
    return '$' + price.toFixed(2);
  }

  /**
   * Format date/time for display
   */
  formatTime(dateString: string): string {
    try {
      const date = new Date(dateString);
      return date.toLocaleTimeString();
    } catch {
      return dateString;
    }
  }

  /**
   * Calculate trade value
   */
  tradeValue(exec: Execution): number {
    return exec.execQty * exec.execPrice;
  }

  /**
   * Clear all trades from the grid
   */
  clearTrades(): void {
    this.trades = [];
    this.totalShares = 0;
    this.totalTradeValue = 0;
  }

  /**
   * Get average trade price
   */
  getAveragePrice(): string {
    if (this.totalShares === 0) return 'N/A';
    return '$' + (this.totalTradeValue / this.totalShares).toFixed(2);
  }

  /**
   * Format total value for display
   */
  formatTotalValue(): string {
    return '$' + this.totalTradeValue.toFixed(2);
  }
}
