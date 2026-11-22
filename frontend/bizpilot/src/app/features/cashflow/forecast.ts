import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/http.util';
import { CashflowForecast, ForecastGranularity, ForecastHorizon } from '../../core/models';
import { CashflowService } from '../../core/services/cashflow.service';
import { BarChart, ChartGroup } from '../../shared/bar-chart';
import { MoneyPipe } from '../../shared/money.pipe';
import { PageHeader, StatCard, StateBlock } from '../../shared/ui';

@Component({
  selector: 'app-forecast',
  imports: [RouterLink, DatePipe, MoneyPipe, PageHeader, StatCard, StateBlock, BarChart],
  template: `
    <div class="page-strip">
      <app-page-header title="Cash-flow forecast" subtitle="Projected cash position from confirmed payments, unpaid invoices and expected spending.">
        <a routerLink="/what-if" class="btn btn-secondary">Run what-if</a>
      </app-page-header>
    </div>

    <div class="toolbar card toolbar-card">
      <div class="segmented" role="group" aria-label="Forecast horizon">
        @for (d of horizons; track d) {
          <button type="button" [class.active]="days() === d" (click)="setDays(d)">{{ d }} days</button>
        }
      </div>
      <div class="segmented" role="group" aria-label="Breakdown">
        <button type="button" [class.active]="granularity() === 'WEEKLY'" (click)="setGranularity('WEEKLY')">Weekly</button>
        <button type="button" [class.active]="granularity() === 'DAILY'" (click)="setGranularity('DAILY')">Daily</button>
      </div>
    </div>

    <app-state [loading]="loading()" [error]="error()" (retry)="load()" />

    @if (!loading() && forecast(); as f) {
      <p class="muted small">
        As of {{ f.asOfDate | date: 'mediumDate' }} · forecasting {{ f.startDate | date: 'mediumDate' }} – {{ f.endDate | date: 'mediumDate' }}
      </p>
      <div class="grid stats-grid">
        <app-stat-card class="kpi-balance" label="Opening balance" [value]="f.openingBalance | money"
          [hint]="'Received ' + (f.confirmedCashIn | money) + ' − spent ' + (f.confirmedCashOut | money)" />
        <app-stat-card class="kpi-balance" label="Projected closing balance" [value]="f.projectedClosingBalance | money"
          [tone]="f.projectedClosingBalance >= 0 ? 'good' : 'bad'" [hint]="'On ' + (f.endDate | date: 'mediumDate')" />
        <app-stat-card label="Lowest projected balance" [value]="f.lowestProjectedBalance | money"
          [tone]="f.lowestProjectedBalance < 0 ? 'bad' : 'warn'" [hint]="f.lowestBalanceDate ? 'On ' + (f.lowestBalanceDate | date: 'mediumDate') : ''" />
        <app-stat-card label="Expected from invoices" [value]="f.totalExpectedInflow | money" tone="info"
          [hint]="'Unpaid — not confirmed cash'" />
        <app-stat-card class="kpi-in" label="Confirmed inflows" [value]="f.totalConfirmedInflow | money" tone="good" hint="Payments already recorded" />
        <app-stat-card class="kpi-out" label="Outflows" [value]="(f.totalScheduledOutflow + f.totalProjectedOutflow) | money" tone="bad"
          [hint]="'Scheduled ' + (f.totalScheduledOutflow | money) + ' + est. ' + (f.averageDailyExpense | money) + '/day'" />
      </div>

      @if (f.lowestProjectedBalance < 0) {
        <div class="alert alert-error">
          Cash is projected to go negative ({{ f.lowestProjectedBalance | money }} on {{ f.lowestBalanceDate | date: 'mediumDate' }}).
          Chase overdue invoices or delay large expenses.
        </div>
      }

      <section class="card chart-card">
        <div class="card-head"><h2>Projected closing balance</h2><span class="muted small">per {{ f.granularity === 'DAILY' ? 'day' : 'week' }}</span></div>
        <app-bar-chart [groups]="balanceGroups()" [series]="balanceSeries" ariaLabel="Projected closing balance" />
      </section>

      <section class="card chart-card">
        <div class="card-head"><h2>Inflows vs outflows</h2></div>
        <app-bar-chart [groups]="flowGroups()" [series]="flowSeries" ariaLabel="Forecast inflows and outflows" />
      </section>

      <section class="card">
        <div class="card-head"><h2>Breakdown</h2></div>
        <div class="table-wrap">
          <table class="table table-compact">
            <thead>
              <tr>
                <th>Period</th><th class="num">Opening</th><th class="num">Confirmed in</th><th class="num">Expected in</th>
                <th class="num">Scheduled out</th><th class="num">Projected out</th><th class="num">Net</th><th class="num">Closing</th>
              </tr>
            </thead>
            <tbody>
              @for (p of f.periods; track p.periodStart) {
                <tr>
                  <td>{{ p.periodStart | date: 'd MMM' }}@if (p.periodEnd !== p.periodStart) { – {{ p.periodEnd | date: 'd MMM' }} }</td>
                  <td class="num">{{ p.openingBalance | money }}</td>
                  <td class="num">{{ p.confirmedInflow | money }}</td>
                  <td class="num">{{ p.expectedInflow | money }}</td>
                  <td class="num">{{ p.scheduledOutflow | money }}</td>
                  <td class="num">{{ p.projectedOutflow | money }}</td>
                  <td class="num" [class.text-good]="p.netChange > 0" [class.text-bad]="p.netChange < 0">{{ p.netChange | money }}</td>
                  <td class="num" [class.text-bad]="p.closingBalance < 0"><strong>{{ p.closingBalance | money }}</strong></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>

      <section class="card">
        <div class="card-head">
          <h2>Expected invoice collections</h2>
          @if (f.expectedInflowBeyondHorizon > 0) {
            <span class="muted small">{{ f.expectedInflowBeyondHorizon | money }} expected after this period</span>
          }
        </div>
        <app-state [empty]="f.expectedInvoices.length === 0" emptyText="No unpaid sent invoices." />
        @if (f.expectedInvoices.length > 0) {
          <div class="table-wrap">
            <table class="table">
              <thead><tr><th>Invoice</th><th>Customer</th><th>Due</th><th>Expected</th><th class="num">Outstanding</th><th></th></tr></thead>
              <tbody>
                @for (i of f.expectedInvoices; track i.invoiceId) {
                  <tr [class.muted-row]="!i.withinHorizon">
                    <td><a [routerLink]="['/invoices', i.invoiceId]">{{ i.invoiceNumber }}</a></td>
                    <td>{{ i.customerName }}</td>
                    <td>{{ i.dueDate | date: 'mediumDate' }}</td>
                    <td>{{ i.expectedDate | date: 'mediumDate' }}</td>
                    <td class="num">{{ i.outstandingAmount | money }}</td>
                    <td>
                      @if (i.overdue) { <span class="badge badge-overdue">Overdue</span> }
                      @if (!i.withinHorizon) { <span class="badge badge-cancelled">After period</span> }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <section class="card">
        <h2>How this forecast works</h2>
        <ul class="assumptions">
          @for (a of f.assumptions; track a) { <li>{{ a }}</li> }
        </ul>
      </section>
    }
  `,
  styles: [`
    .page-strip {
      background: var(--grad-primary);
      color: #fff;
      border-radius: 14px;
      padding: 20px 24px;
      margin-bottom: 20px;
      box-shadow: var(--shadow);
    }
    .page-strip ::ng-deep .page-header { margin-bottom: 0; }
    .page-strip ::ng-deep .page-header p { color: rgba(255, 255, 255, 0.9); }
    .page-strip ::ng-deep .page-header h1 {
      background: none;
      color: #fff;
      -webkit-text-fill-color: #fff;
      -webkit-background-clip: border-box;
      background-clip: border-box;
    }
    .page-strip ::ng-deep .btn-secondary {
      background: rgba(255, 255, 255, 0.2);
      color: #fff;
      border-color: rgba(255, 255, 255, 0.45);
    }
    .kpi-balance ::ng-deep .stat-card {
      --tone: #c026d3;
      background: var(--tint-indigo);
      box-shadow: inset 0 3px 0 var(--tone), var(--shadow);
    }
    .kpi-in ::ng-deep .stat-card {
      --tone: #7c3aed;
      background: var(--tint-emerald);
      box-shadow: inset 0 3px 0 var(--tone), var(--shadow);
    }
    .kpi-out ::ng-deep .stat-card {
      --tone: #a855f7;
      background: var(--tint-rose);
      box-shadow: inset 0 3px 0 var(--tone), var(--shadow);
    }
    .chart-card { position: relative; }
    .chart-card::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 4px;
      border-radius: 14px 14px 0 0;
      background: var(--grad-primary);
    }
  `],
})
export class ForecastPage implements OnInit {
  private readonly api = inject(CashflowService);

  protected readonly horizons: ForecastHorizon[] = [30, 60, 90];
  protected readonly days = signal<ForecastHorizon>(30);
  protected readonly granularity = signal<ForecastGranularity>('WEEKLY');
  protected readonly forecast = signal<CashflowForecast | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly balanceSeries = [{ name: 'Closing balance', color: '#6d28d9' }];
  protected readonly flowSeries = [
    { name: 'Confirmed in', color: '#7c3aed' },
    { name: 'Expected in', color: '#c4b5fd' },
    { name: 'Outflows', color: '#a855f7' },
  ];

  protected readonly balanceGroups = computed<ChartGroup[]>(() =>
    (this.forecast()?.periods ?? []).map((p) => ({ label: shortDate(p.periodStart), values: [Number(p.closingBalance)] })),
  );
  protected readonly flowGroups = computed<ChartGroup[]>(() =>
    (this.forecast()?.periods ?? []).map((p) => ({
      label: shortDate(p.periodStart),
      values: [Number(p.confirmedInflow), Number(p.expectedInflow), -(Number(p.scheduledOutflow) + Number(p.projectedOutflow))],
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  setDays(d: ForecastHorizon): void {
    this.days.set(d);
    this.load();
  }

  setGranularity(g: ForecastGranularity): void {
    this.granularity.set(g);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.forecast(this.days(), this.granularity()).subscribe({
      next: (f) => { this.forecast.set(f); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}

function shortDate(iso: string): string {
  const d = new Date(iso + 'T00:00:00');
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}
