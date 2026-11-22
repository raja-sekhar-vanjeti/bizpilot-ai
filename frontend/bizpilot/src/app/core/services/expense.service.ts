import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { toPageResult, toParams } from '../http.util';
import { ApiResponse, Expense, ExpenseFilter, ExpenseRequest, PageResult, SpringPage } from '../models';

/** /api/expenses — the date range filter applies only when both from and to are set. */
@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/expenses`;

  list(filter: ExpenseFilter = {}): Observable<PageResult<Expense>> {
    return this.http
      .get<ApiResponse<SpringPage<Expense>>>(this.url, { params: toParams(filter) })
      .pipe(map((res) => toPageResult(res.data)));
  }

  get(id: string): Observable<Expense> {
    return this.http.get<ApiResponse<Expense>>(`${this.url}/${id}`).pipe(map((res) => res.data));
  }

  create(request: ExpenseRequest): Observable<Expense> {
    return this.http.post<ApiResponse<Expense>>(this.url, request).pipe(map((res) => res.data));
  }

  update(id: string, request: ExpenseRequest): Observable<Expense> {
    return this.http.put<ApiResponse<Expense>>(`${this.url}/${id}`, request).pipe(map((res) => res.data));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
