import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import { toPageResult, toParams } from '../http.util';
import { ApiResponse, PageQuery, PageResult, Payment, PaymentRequest, SpringPage } from '../models';

/** /api/payments — payments cannot be edited or deleted on the backend. */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/payments`;

  list(query: PageQuery = {}): Observable<PageResult<Payment>> {
    return this.http
      .get<ApiResponse<SpringPage<Payment>>>(this.url, { params: toParams(query) })
      .pipe(map((res) => toPageResult(res.data)));
  }

  get(id: string): Observable<Payment> {
    return this.http.get<ApiResponse<Payment>>(`${this.url}/${id}`).pipe(map((res) => res.data));
  }

  record(request: PaymentRequest): Observable<Payment> {
    return this.http.post<ApiResponse<Payment>>(this.url, request).pipe(map((res) => res.data));
  }
}
