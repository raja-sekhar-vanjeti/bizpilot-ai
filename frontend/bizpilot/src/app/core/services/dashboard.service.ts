import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { ApiResponse, DashboardSummary, MonthlyPoint, TransactionView } from '../models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/dashboard`;

  summary(): Observable<DashboardSummary> {
    return this.http.get<ApiResponse<DashboardSummary>>(`${this.url}/summary`).pipe(map((res) => res.data));
  }

  recentTransactions(limit = 8): Observable<TransactionView[]> {
    return this.http
      .get<ApiResponse<TransactionView[]>>(`${this.url}/recent-transactions`, { params: { limit } })
      .pipe(map((res) => res.data));
  }

  /** Last 12 months of collected revenue and expenses (same data as /expense-chart). */
  monthlyChart(): Observable<MonthlyPoint[]> {
    return this.http.get<ApiResponse<MonthlyPoint[]>>(`${this.url}/revenue-chart`).pipe(map((res) => res.data));
  }
}
