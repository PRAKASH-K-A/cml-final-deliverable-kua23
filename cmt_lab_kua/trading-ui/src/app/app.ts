import { Component, signal } from '@angular/core';
import { TradingDashboardComponent } from './components/trading-dashboard/trading-dashboard.component';

@Component({
  selector: 'app-root',
  imports: [TradingDashboardComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('trading-ui');
}
