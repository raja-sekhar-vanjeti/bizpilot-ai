import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/http.util';
import { DashboardSummary, Insight, MonthlyPoint, TransactionView } from '../../core/models';
import { InsightsService } from '../../core/services/cashflow.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { BarChart, ChartGroup } from '../../shared/bar-chart';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageHeader, StatCard, StateBlock, StatusBadge } from '../../shared/ui';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, MoneyPipe, PageHeader, StatCard, StateBlock, StatusBadge, BarChart],
  template: `
    <app-page-header [title]="greeting()" subtitle="Here is how your business is doing.">
      <a routerLink="/invoices/new" class="btn btn-primary">+ New invoice</a>
      <a routerLink="/expenses" class="btn btn-secondary">+ Expense</a>
    </app-page-header>

    <app-state [loading]="loadingSummary()" [error]="summaryError()" (retry)="loadSummary()" />
    @if (summary(); as s) {
      <div class="grid stats-grid">
        <app-stat-card label="Total collected" [value]="s.totalCollected | money" hint="Payments received" tone="good" />
        <app-stat-card label="Total expenses" [value]="s.totalExpenses | money" hint="All recorded expenses" tone="bad" />
        <app-stat-card label="Net cash movement" [value]="s.netCashMovement | money" hint="Collected minus expenses"
          [tone]="s.netCashMovement >= 0 ? 'good' : 'bad'" />
        <app-stat-card label="Outstanding receivables" [value]="s.outstandingReceivables | money"
          [hint]="s.pendingInvoiceCount + ' pending invoice(s)'" tone="info" />
        <app-stat-card label="Overdue receivables" [value]="s.overdueReceivables | money"
          [hint]="s.overdueInvoiceCount + ' overdue invoice(s)'" [tone]="s.overdueInvoiceCount > 0 ? 'warn' : 'default'" />
        <app-stat-card label="Total invoiced" [value]="s.totalInvoiced | money"
          [hint]="s.invoiceCount + ' invoices · ' + s.paidInvoiceCount + ' paid'" />
        <app-stat-card label="Active customers" [value]="'' + s.customerCount" hint="Not archived" />
      </div>
    }

    <div class="grid two-col">
      <section class="card">
        <div class="card-head">
          <h2>Revenue vs expenses</h2>
          <span class="muted small">Last 12 months · collected cash</span>
        </div>
        <app-state [loading]="loadingChart()" [error]="chartError()" (retry)="loadChart()"
          [empty]="!loadingChart() && !chartError() && !hasChartData()" emptyText="No payments or expenses recorded in the last 12 months." />
        @if (hasChartData()) {
          <app-bar-chart [groups]="chartGroups()" [series]="chartSeries" ariaLabel="Monthly revenue and expenses" />
        }
      </section>

      <section class="card">
        <div class="card-head">
          <h2>Top insights</h2>
          <a routerLink="/insights" class="small">View all</a>
        </div>
        <app-state [loading]="loadingInsights()" [error]="insightsError()" (retry)="loadInsights()"
          [empty]="!loadingInsights() && !insightsError() && insights().length === 0" emptyText="No alerts right now — everything looks healthy." />
        <ul class="insight-mini">
          @for (i of insights().slice(0, 4); track i.code + i.title) {
            <li>
              <app-badge [status]="i.severity" />
              <div>
                <strong>{{ i.title }}</strong>
                <p class="muted small">{{ i.suggestedAction }}</p>
              </div>
            </li>
          }
        </ul>
      </section>
    </div>

    <section class="card">
      <div class="card-head">
        <h2>Recent transactions</h2>
        <span class="row-actions small"><a routerLink="/payments">Payments</a> · <a routerLink="/expenses">Expenses</a></span>
      </div>
      <app-state [loading]="loadingTx()" [error]="txError()" (retry)="loadTransactions()"
        [empty]="!loadingTx() && !txError() && transactions().length === 0" emptyText="No payments or expenses yet." />
      @if (transactions().length > 0) {
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>Date</th><th>Type</th><th>Reference</th><th class="num">Amount</th></tr></thead>
            <tbody>
              @for (t of transactions(); track $index) {
                <tr>
                  <td>{{ t.date | date: 'mediumDate' }}</td>
                  <td><app-badge [status]="t.type" /></td>
                  <td>{{ t.reference || '—' }}</td>
                  <td class="num" [class.text-good]="t.type === 'PAYMENT'" [class.text-bad]="t.type === 'EXPENSE'">
                    {{ t.type === 'PAYMENT' ? '+' : '−' }}{{ t.amount | money }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class DashboardPage implements OnInit {
  private readonly dashboard = inject(DashboardService);
  private readonly insightsApi = inject(InsightsService);
  private readonly auth = inject(AuthService);

  protected readonly summary = signal<DashboardSummary | null>(null);
  protected readonly loadingSummary = signal(true);
  protected readonly summaryError = signal<string | null>(null);

  protected readonly chart = signal<MonthlyPoint[]>([]);
  protected readonly loadingChart = signal(true);
  protected readonly chartError = signal<string | null>(null);

  protected readonly transactions = signal<TransactionView[]>([]);
  protected readonly loadingTx = signal(true);
  protected readonly txError = signal<string | null>(null);

  protected readonly insights = signal<Insight[]>([]);
  protected readonly loadingInsights = signal(true);
  protected readonly insightsError = signal<string | null>(null);

  protected readonly chartSeries = [
    { name: 'Revenue (collected)', color: '#16a34a' },
    { name: 'Expenses', color: '#dc2626' },
  ];
  protected readonly chartGroups = computed<ChartGroup[]>(() =>
    this.chart().map((p) => ({ label: monthLabel(p.month), values: [Number(p.revenue), Number(p.expenses)] })),
  );
  protected readonly hasChartData = computed(() =>
    this.chart().some((p) => Number(p.revenue) !== 0 || Number(p.expenses) !== 0),
  );

  protected readonly greeting = computed(() => {
    const name = this.auth.profile()?.fullName?.split(' ')[0];
    return name ? `Welcome back, ${name}` : 'Dashboard';
  });

  ngOnInit(): void {
    this.loadSummary();
    this.loadChart();
    this.loadTransactions();
    this.loadInsights();
  }

  loadSummary(): void {
    this.loadingSummary.set(true);
    this.summaryError.set(null);
    this.dashboard.summary().subscribe({
      next: (s) => { this.summary.set(s); this.loadingSummary.set(false); },
      error: (e) => { this.summaryError.set(errorMessage(e)); this.loadingSummary.set(false); },
    });
  }

  loadChart(): void {
    this.loadingChart.set(true);
    this.chartError.set(null);
    this.dashboard.monthlyChart().subscribe({
      next: (c) => { this.chart.set(c); this.loadingChart.set(false); },
      error: (e) => { this.chartError.set(errorMessage(e)); this.loadingChart.set(false); },
    });
  }

  loadTransactions(): void {
    this.loadingTx.set(true);
    this.txError.set(null);
    this.dashboard.recentTransactions(8).subscribe({
      next: (t) => { this.transactions.set(t); this.loadingTx.set(false); },
      error: (e) => { this.txError.set(errorMessage(e)); this.loadingTx.set(false); },
    });
  }

  loadInsights(): void {
    this.loadingInsights.set(true);
    this.insightsError.set(null);
    this.insightsApi.list().subscribe({
      next: (i) => { this.insights.set(i); this.loadingInsights.set(false); },
      error: (e) => { this.insightsError.set(errorMessage(e)); this.loadingInsights.set(false); },
    });
  }
}

function monthLabel(month: string): string {
  const [y, m] = month.split('-').map(Number);
  if (!y || !m) return month;
  return new Date(y, m - 1, 1).toLocaleString('en-US', { month: 'short' }) + ` ${String(y).slice(2)}`;
}
