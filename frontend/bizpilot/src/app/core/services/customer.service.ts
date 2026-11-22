import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { toPageResult, toParams } from '../http.util';
import { ApiResponse, Customer, CustomerRequest, PageQuery, PageResult, SpringPage } from '../models';

/** /api/customers — delete archives the customer (soft delete) on the backend. */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/customers`;

  list(query: PageQuery & { search?: string | null } = {}): Observable<PageResult<Customer>> {
    return this.http
      .get<ApiResponse<SpringPage<Customer>>>(this.url, { params: toParams(query) })
      .pipe(map((res) => toPageResult(res.data)));
  }

  /** All active customers (for dropdowns). */
  all(): Observable<Customer[]> {
    return this.list({ page: 0, size: 500, sort: 'name' }).pipe(map((p) => p.items));
  }

  get(id: string): Observable<Customer> {
    return this.http.get<ApiResponse<Customer>>(`${this.url}/${id}`).pipe(map((res) => res.data));
  }

  create(request: CustomerRequest): Observable<Customer> {
    return this.http.post<ApiResponse<Customer>>(this.url, request).pipe(map((res) => res.data));
  }

  update(id: string, request: CustomerRequest): Observable<Customer> {
    return this.http.put<ApiResponse<Customer>>(`${this.url}/${id}`, request).pipe(map((res) => res.data));
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
