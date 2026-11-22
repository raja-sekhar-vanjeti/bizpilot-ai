/**
 * Base path of the BizPilot Spring Boot API. Calls are same-origin:
 * - local dev: `ng serve` proxies /api to http://localhost:8080 (proxy.conf.json)
 * - Vercel: vercel.json rewrites /api/* to the hosted backend
 * so the browser never makes cross-origin requests.
 */
export const API_BASE_URL = '/api';
