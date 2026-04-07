import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

/**
 * LAB 11: OptionPriceService - Angular Service for Option Price Data
 * 
 * This service manages real-time option pricing data received from the backend
 * via WebSocket. It maintains a stream of option price updates that Angular
 * components can subscribe to.
 */
@Injectable({
  providedIn: 'root'
})
export class OptionPriceService {
  
  // Subject to emit option price updates as they arrive
  public optionPriceUpdates: Subject<any> = new Subject<any>();
  
  // Store the latest option prices by symbol for quick access
  private latestPrices: Map<string, any> = new Map();
  
  constructor() { }
  
  /**
   * Publish a new option price update to all subscribers
   * Called by the WebSocket service when option price data arrives
   * 
   * @param optionData The option price data from the backend
   */
  public publishOptionUpdate(optionData: any): void {
    // Store in local cache
    this.latestPrices.set(optionData.symbol, optionData);
    
    // Emit to all subscribers
    this.optionPriceUpdates.next(optionData);
    
    console.log('[LAB 11] Option price update received:', optionData);
  }
  
  /**
   * Get the latest option price data for a specific symbol
   * 
   * @param symbol The security symbol
   * @returns The latest OptionPrice object or null if not available
   */
  public getLatestPrice(symbol: string): any {
    return this.latestPrices.get(symbol) || null;
  }
  
  /**
   * Get all accumulated option prices
   * 
   * @returns Map of all symbol -> OptionPrice data
   */
  public getAllPrices(): Map<string, any> {
    return new Map(this.latestPrices);
  }
}
