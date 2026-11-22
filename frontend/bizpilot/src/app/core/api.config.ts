/**
 * Base URL of the BizPilot Spring Boot API.
 * - Local development (page served from localhost / 127.0.0.1): call the backend directly at
 *   http://localhost:8080/api. The backend's CORS config allows http://localhost:4200 by default
 *   (CORS_ALLOWED_ORIGINS), so this works with or without the ng serve proxy.
 * - Deployed (e.g. Vercel): same-origin /api, which vercel.json rewrites to the hosted backend.
 */
const LOCAL_HOSTS = ['localhost', '127.0.0.1'];

function resolveApiBaseUrl(): string {
  if (typeof location !== 'undefined' && LOCAL_HOSTS.includes(location.hostname) && location.port !== '8080') {
    return 'http://localhost:8080/api';
  }
  return '/api';
}

export const API_BASE_URL = resolveApiBaseUrl();
