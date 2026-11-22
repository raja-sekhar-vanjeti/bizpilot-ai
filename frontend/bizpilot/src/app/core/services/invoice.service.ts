import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { toPageResult, toParams } from '../http.util';
import { ApiResponse, Invoice, InvoiceFilter, InvoiceRequest, PageResult, Payment, SpringPage } from '../models';

/**
 * /api/invoices. The backend applies only one list filter at a time, in this order:
 * status, then customerId, then the from/to date range.
 */
@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/invoices`;

  list(filter: InvoiceFilter = {}): Observable<PageResult<Invoice>> {
    return this.http
      .get<ApiResponse<SpringPage<Invoice>>>(this.url, { params: toParams(filter) })
      .pipe(map((res) => toPageResult(res.data)));
  }

  get(id: string): Observable<Invoice> {
    return this.http.get<ApiResponse<Invoice>>(`${this.url}/${id}`).pipe(map((res) => res.data));
  }

  create(request: InvoiceRequest): Observable<Invoice> {
    return this.http.post<ApiResponse<Invoice>>(this.url, request).pipe(map((res) => res.data));
  }

  update(id: string, request: InvoiceRequest): Observable<Invoice> {
    return this.http.put<ApiResponse<Invoice>>(`${this.url}/${id}`, request).pipe(map((res) => res.data));
  }

  send(id: string): Observable<Invoice> {
    return this.http.post<ApiResponse<Invoice>>(`${this.url}/${id}/send`, null).pipe(map((res) => res.data));
  }

  cancel(id: string): Observable<Invoice> {
    return this.http.post<ApiResponse<Invoice>>(`${this.url}/${id}/cancel`, null).pipe(map((res) => res.data));
  }

  payments(id: string): Observable<Payment[]> {
    return this.http.get<ApiResponse<Payment[]>>(`${this.url}/${id}/payments`).pipe(map((res) => res.data));
  }
}
