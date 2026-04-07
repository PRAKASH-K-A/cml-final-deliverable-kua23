import { Component, OnInit, OnDestroy } from '@angular/core';
import { WebsocketService, OptionPrice } from '../../services/websocket.service';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

/**
 * LAB 11: OptionPricingDashboardComponent
 * 
 * Displays real-time option pricing data for securities being traded.
 * 
 * Features:
 * - Live update of call and put option prices
 * - Display of Greeks (Delta, Gamma, Vega, Theta, Rho)
 * - Spot price and strike price comparison
 * - Real-time calculation updates triggered by trade executions
 * - Color-coded display of price movements
 */
@Component({
  selector: 'app-option-pricing-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="option-dashboard-container">
      <div class="dashboard-header">
        <h2>📊 LAB 11: Option Pricing Dashboard (Black-Scholes)</h2>
        <p class="subtitle">Real-time option prices updated on each trade execution</p>
      </div>

      <!-- Connection Status Indicator -->
      <div class="connection-status">
        <span [ngClass]="{'connected': isConnected, 'disconnected': !isConnected}">
          {{ isConnected ? '🟢 Connected' : '🔴 Disconnected' }}
        </span>
      </div>

      <!-- Option Pricing Table -->
      <div class="table-container">
        <table class="option-table" *ngIf="optionPrices.length > 0">
          <thead>
            <tr>
              <th colspan="8" class="section-header">Option Pricing Data</th>
            </tr>
            <tr class="header-row">
              <th>Symbol</th>
              <th>Spot Price</th>
              <th>Strike</th>
              <th>Call Price</th>
              <th>Put Price</th>
              <th>Call/Put Ratio</th>
              <th>Last Trade</th>
            </tr>
            <tr class="greeks-header">
              <th colspan="7">Greeks (Sensitivity Metrics)</th>
            </tr>
            <tr class="header-row secondary">
              <th>Symbol</th>
              <th>Delta</th>
              <th>Gamma</th>
              <th>Vega</th>
              <th>Theta (per day)</th>
              <th>Rho</th>
              <th>Volatility</th>
            </tr>
          </thead>
          <tbody>
            <ng-container *ngFor="let option of optionPrices">
              <!-- First row: Option Prices -->
              <tr class="data-row">
                <td class="symbol-cell" [ngClass]="{'itm': isInTheMoney(option), 'otm': !isInTheMoney(option)}">
                  <strong>{{ option.symbol }}</strong>
                  <span class="moneyness">{{ getMoneyness(option) }}</span>
                </td>
                <td class="price-cell">
                  <span class="spot-price">${{ option.spotPrice.toFixed(2) }}</span>
                </td>
                <td class="price-cell">${{ option.strikePrice.toFixed(2) }}</td>
                <td class="call-cell">
                  <span class="price-value">${{ option.callPrice.toFixed(2) }}</span>
                </td>
                <td class="put-cell">
                  <span class="price-value">${{ option.putPrice.toFixed(2) }}</span>
                </td>
                <td class="ratio-cell">
                  {{ (option.callPrice / option.putPrice).toFixed(2) }}x
                </td>
                <td class="trade-info">
                  {{ option.lastTradeQty.toFixed(0) }} @ ${{ option.lastTradePrice.toFixed(2) }}
                </td>
              </tr>

              <!-- Second row: Greeks -->
              <tr class="greeks-row">
                <td><strong>{{ option.symbol }}</strong></td>
                <td class="greek-delta">
                  <span [ngClass]="{'positive': option.delta > 0, 'negative': option.delta < 0}">
                    {{ option.delta.toFixed(4) }}
                  </span>
                </td>
                <td>{{ option.gamma.toFixed(6) }}</td>
                <td>{{ option.vega.toFixed(4) }}</td>
                <td [ngClass]="{'negative': option.theta < 0}">{{ option.theta.toFixed(4) }}</td>
                <td>{{ option.rho.toFixed(4) }}</td>
                <td>{{ (option.volatility * 100).toFixed(1) }}%</td>
              </tr>
            </ng-container>
          </tbody>
        </table>

        <!-- No Data Message -->
        <div class="no-data" *ngIf="optionPrices.length === 0">
          <p>⏳ Waiting for trade execution data...</p>
          <p class="hint">Execute a trade to see option prices update in real-time</p>
        </div>
      </div>

      <!-- Greeks Explanation -->
      <div class="greeks-explanation">
        <h3>The Greeks - Understanding Option Sensitivity</h3>
        <div class="greek-info">
          <div class="greek-item">
            <strong>📈 Delta (Δ):</strong> Rate of change of option price relative to spot price. 
            Call Delta range: 0-1. Long calls have positive delta.
          </div>
          <div class="greek-item">
            <strong>📊 Gamma (Γ):</strong> Rate of change of delta. Measures acceleration of delta changes.
            High gamma = high convexity (gains/losses accelerate).
          </div>
          <div class="greek-item">
            <strong>💨 Vega (ν):</strong> Sensitivity to volatility changes. Positive for both calls and puts.
            Higher volatility increases option prices.
          </div>
          <div class="greek-item">
            <strong>⏱️ Theta (Θ):</strong> Time decay. Negative for long positions (time erodes value daily).
            Approaches strike price as expiration nears.
          </div>
          <div class="greek-item">
            <strong>💰 Rho (ρ):</strong> Sensitivity to interest rate changes. Typically small for short-dated options.
          </div>
        </div>
      </div>

      <!-- Black-Scholes Formula Info -->
      <div class="formula-info">
        <h3>Black-Scholes Model Assumptions</h3>
        <ul>
          <li><strong>Volatility:</strong> 20% per annum (annualized standard deviation)</li>
          <li><strong>Time to Expiration:</strong> 90 days (0.25 years)</li>
          <li><strong>Risk-Free Rate:</strong> 2% per annum</li>
          <li><strong>Option Type:</strong> European-style (exercise at expiration only)</li>
          <li><strong>No Dividends:</strong> Simplified model (real stocks pay dividends)</li>
        </ul>
      </div>

      <!-- Real-Time Stats -->
      <div class="stats-panel">
        <h3>Statistics</h3>
        <div class="stat">
          <span>Total Symbols Priced:</span>
          <strong>{{ optionPrices.length }}</strong>
        </div>
        <div class="stat">
          <span>Average Call Price:</span>
          <strong>${{ getAverageCallPrice().toFixed(2) }}</strong>
        </div>
        <div class="stat">
          <span>Average Put Price:</span>
          <strong>${{ getAveragePutPrice().toFixed(2) }}</strong>
        </div>
        <div class="stat">
          <span>Last Update:</span>
          <strong>{{ lastUpdateTime | date:'HH:mm:ss' }}</strong>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .option-dashboard-container {
      padding: 20px;
      font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
      background: #f5f5f5;
    }

    .dashboard-header {
      margin-bottom: 20px;
    }

    .dashboard-header h2 {
      color: #2c3e50;
      margin: 0;
      font-size: 24px;
    }

    .subtitle {
      color: #7f8c8d;
      margin: 8px 0 0 0;
      font-size: 14px;
    }

    .connection-status {
      margin-bottom: 20px;
      padding: 10px;
      background: white;
      border-radius: 4px;
      border-left: 4px solid #95a5a6;
    }

    .connection-status span {
      font-weight: bold;
      font-size: 14px;
    }

    .connected {
      color: #27ae60;
    }

    .disconnected {
      color: #e74c3c;
    }

    .table-container {
      background: white;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      overflow-x: auto;
      margin-bottom: 20px;
    }

    .option-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    .option-table thead {
      background: #34495e;
      color: white;
    }

    .section-header {
      text-align: center;
      padding: 12px;
      font-weight: bold;
      background: #2c3e50;
    }

    .header-row th {
      padding: 12px;
      text-align: left;
      border-bottom: 1px solid #bdc3c7;
      font-weight: 600;
    }

    .header-row.secondary th {
      background: #34495e;
      padding: 10px 12px;
      font-size: 12px;
    }

    .greeks-header {
      background: #34495e;
    }

    .greeks-header th {
      padding: 10px 12px;
      text-align: center;
      border-bottom: 1px solid #95a5a6;
    }

    .data-row {
      border-bottom: 1px solid #ecf0f1;
      background: #fff;
      transition: background-color 0.2s;
    }

    .data-row:hover {
      background: #f8f9fa;
    }

    .data-row td {
      padding: 12px;
      vertical-align: middle;
    }

    .greeks-row {
      background: #f9f9f9;
      border-bottom: 2px solid #ecf0f1;
    }

    .greeks-row td {
      padding: 10px 12px;
      font-size: 12px;
      text-align: center;
      border-right: 1px solid #ecf0f1;
    }

    .greeks-row td:last-child {
      border-right: none;
    }

    .symbol-cell {
      font-weight: bold;
      color: #2c3e50;
      min-width: 80px;
    }

    .itm::after {
      content: ' (ITM)';
      font-size: 11px;
      color: #27ae60;
      margin-left: 4px;
    }

    .otm::after {
      content: ' (OTM)';
      font-size: 11px;
      color: #e74c3c;
      margin-left: 4px;
    }

    .moneyness {
      display: block;
      font-size: 11px;
      font-weight: normal;
    }

    .price-cell {
      text-align: right;
      min-width: 90px;
    }

    .spot-price {
      font-weight: bold;
      color: #2980b9;
    }

    .call-cell, .put-cell {
      text-align: right;
      min-width: 90px;
    }

    .call-cell {
      background: rgba(46, 204, 113, 0.05);
    }

    .put-cell {
      background: rgba(231, 76, 60, 0.05);
    }

    .price-value {
      font-weight: 600;
    }

    .ratio-cell {
      text-align: center;
      min-width: 70px;
      color: #7f8c8d;
    }

    .trade-info {
      font-size: 12px;
      color: #7f8c8d;
      min-width: 110px;
    }

    .greek-delta {
      font-weight: 600;
      min-width: 70px;
    }

    .positive {
      color: #27ae60;
    }

    .negative {
      color: #e74c3c;
    }

    .no-data {
      padding: 40px;
      text-align: center;
      color: #7f8c8d;
    }

    .no-data p {
      margin: 8px 0;
    }

    .hint {
      font-size: 13px;
      color: #95a5a6;
      font-style: italic;
    }

    .greeks-explanation {
      background: white;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .greeks-explanation h3 {
      margin-top: 0;
      color: #2c3e50;
      border-bottom: 2px solid #3498db;
      padding-bottom: 10px;
    }

    .greek-info {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 15px;
    }

    .greek-item {
      padding: 12px;
      background: #f9f9fa;
      border-left: 4px solid #3498db;
      border-radius: 4px;
      font-size: 13px;
      line-height: 1.5;
    }

    .formula-info {
      background: white;
      padding: 20px;
      border-radius: 8px;
      margin-bottom: 20px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .formula-info h3 {
      margin-top: 0;
      color: #2c3e50;
      border-bottom: 2px solid #9b59b6;
      padding-bottom: 10px;
    }

    .formula-info ul {
      margin: 10px 0;
      padding-left: 20px;
    }

    .formula-info li {
      margin: 8px 0;
      font-size: 13px;
      color: #34495e;
    }

    .stats-panel {
      background: white;
      padding: 20px;
      border-radius: 8px;
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .stats-panel h3 {
      margin-top: 0;
      color: #2c3e50;
      border-bottom: 2px solid #e74c3c;
      padding-bottom: 10px;
    }

    .stat {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid #ecf0f1;
      font-size: 14px;
    }

    .stat:last-child {
      border-bottom: none;
    }

    .stat strong {
      color: #2c3e50;
    }

    @media (max-width: 768px) {
      .option-table {
        font-size: 11px;
      }

      .header-row th, .data-row td, .greeks-row td {
        padding: 8px 4px;
      }

      .greek-info {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class OptionPricingDashboardComponent implements OnInit, OnDestroy {
  
  optionPrices: OptionPrice[] = [];
  isConnected = false;
  lastUpdateTime = new Date();
  
  private destroy$ = new Subject<void>();

  constructor(private websocketService: WebsocketService) {}

  ngOnInit(): void {
    // Subscribe to connection status
    this.websocketService.getConnectionStatus()
      .pipe(takeUntil(this.destroy$))
      .subscribe(status => {
        this.isConnected = status;
      });

    // Subscribe to option price updates
    this.websocketService.getOptionPrices()
      .pipe(takeUntil(this.destroy$))
      .subscribe(optionPrice => {
        this.handleOptionPriceUpdate(optionPrice);
      });
  }

  /**
   * Handle incoming option price data
   * Update or add to the table
   */
  private handleOptionPriceUpdate(optionPrice: OptionPrice): void {
    const index = this.optionPrices.findIndex(opt => opt.symbol === optionPrice.symbol);
    
    if (index >= 0) {
      // Update existing entry
      this.optionPrices[index] = optionPrice;
    } else {
      // Add new entry
      this.optionPrices.push(optionPrice);
    }
    
    this.lastUpdateTime = new Date();
  }

  /**
   * Check if option is In The Money (ITM)
   */
  isInTheMoney(option: OptionPrice): boolean {
    return option.spotPrice > option.strikePrice;
  }

  /**
   * Get moneyness description
   */
  getMoneyness(option: OptionPrice): string {
    const diff = option.spotPrice - option.strikePrice;
    if (Math.abs(diff) < option.strikePrice * 0.02) {
      return 'ATM';  // At The Money
    }
    return option.spotPrice > option.strikePrice ? 'ITM' : 'OTM';
  }

  /**
   * Calculate average call price
   */
  getAverageCallPrice(): number {
    if (this.optionPrices.length === 0) return 0;
    const sum = this.optionPrices.reduce((acc, opt) => acc + opt.callPrice, 0);
    return sum / this.optionPrices.length;
  }

  /**
   * Calculate average put price
   */
  getAveragePutPrice(): number {
    if (this.optionPrices.length === 0) return 0;
    const sum = this.optionPrices.reduce((acc, opt) => acc + opt.putPrice, 0);
    return sum / this.optionPrices.length;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
