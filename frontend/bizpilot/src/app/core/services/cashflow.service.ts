import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../api.config';
import {
  ApiResponse,
  CashflowForecast,
  ForecastGranularity,
  ForecastHorizon,
  Insight,
  WhatIfRequest,
  WhatIfResponse,
} from '../models';

@Injectable({ providedIn: 'root' })
export class CashflowService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/cashflow`;

  forecast(days: ForecastHorizon, granularity: ForecastGranularity): Observable<CashflowForecast> {
    return this.http
      .get<ApiResponse<CashflowForecast>>(`${this.url}/forecast`, { params: { days, granularity } })
      .pipe(map((res) => res.data));
  }

  /** Read-only simulation; nothing is persisted. */
  whatIf(request: WhatIfRequest): Observable<WhatIfResponse> {
    return this.http.post<ApiResponse<WhatIfResponse>>(`${this.url}/what-if`, request).pipe(map((res) => res.data));
  }
}

@Injectable({ providedIn: 'root' })
export class InsightsService {
  private readonly http = inject(HttpClient);

  list(): Observable<Insight[]> {
    return this.http.get<ApiResponse<Insight[]>>(`${API_BASE_URL}/insights`).pipe(map((res) => res.data));
  }
}
