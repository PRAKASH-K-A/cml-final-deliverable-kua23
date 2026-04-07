import { Component, signal } from '@angular/core';
import { TradingDashboardComponent } from './components/trading-dashboard/trading-dashboard.component';
import { OptionPricingDashboardComponent } from './components/option-pricing-dashboard/option-pricing-dashboard.component';

@Component({
  selector: 'app-root',
  imports: [TradingDashboardComponent, OptionPricingDashboardComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('trading-ui');
}
