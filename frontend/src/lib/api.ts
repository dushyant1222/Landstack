import axios, { AxiosError, type AxiosProgressEvent } from 'axios';
import type { ApiErrorBody } from '@/types/api';

/**
 * The single HTTP client. Session cookie + CSRF header (Spring Security CookieCsrfTokenRepository).
 * Base URL is relative by default so the browser talks to the same origin (dev proxy / nginx).
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 120_000,
});

let csrfReady: Promise<void> | null = null;

/** Ensures the XSRF-TOKEN cookie exists before the first mutating request. */
export function ensureCsrf(): Promise<void> {
  if (!csrfReady) {
    csrfReady = api.get('/auth/csrf').then(() => undefined).catch((e) => {
      csrfReady = null;
      throw e;
    });
  }
  return csrfReady;
}

export function resetCsrf(): void {
  csrfReady = null;
}

api.interceptors.request.use(async (config) => {
  const method = (config.method ?? 'get').toLowerCase();
  if (method !== 'get' && method !== 'head' && !config.url?.endsWith('/auth/csrf')) {
    await ensureCsrf();
  }
  return config;
});

export const UNAUTHORIZED_EVENT = 'landstack:unauthorized';

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    if (status === 401 && !url.endsWith('/auth/login') && !url.endsWith('/auth/me')) {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    // Expired CSRF token: refresh once and retry the request.
    const cfg = error.config as (typeof error.config & { _csrfRetried?: boolean }) | undefined;
    if (status === 403 && error.response?.data?.message?.includes('Security token') && cfg && !cfg._csrfRetried) {
      cfg._csrfRetried = true;
      resetCsrf();
      await ensureCsrf();
      return api.request(cfg);
    }
    return Promise.reject(error);
  },
);

/** Normalised, safe error for display. Never contains stack traces. */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly fieldErrors: { field: string; message: string }[] = [],
  ) {
    super(message);
  }
}

export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  if (axios.isAxiosError(e)) {
    const body = e.response?.data as Partial<ApiErrorBody> | undefined;
    if (e.response && body && typeof body.message === 'string') {
      return new ApiError(body.message, e.response.status, body.code ?? 'ERROR', body.fieldErrors ?? []);
    }
    if (e.response) return new ApiError(defaultMessage(e.response.status), e.response.status, 'HTTP_' + e.response.status);
    if (e.code === 'ECONNABORTED') return new ApiError('The server took too long to respond. Please try again.', 0, 'TIMEOUT');
    return new ApiError('Cannot reach the LandStack server. Check your connection and try again.', 0, 'NETWORK');
  }
  return new ApiError('Something went wrong. Please try again.', 0, 'UNKNOWN');
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Your session has ended. Please sign in again.';
  if (status === 403) return 'You do not have permission to perform this action.';
  if (status === 404) return 'The requested item was not found.';
  if (status === 413) return 'The file is too large.';
  if (status >= 500) return 'The server encountered an error. Please try again later.';
  return 'The request could not be completed.';
}

export function errorMessage(e: unknown): string {
  return toApiError(e).message;
}

export async function get<T>(url: string, params?: object): Promise<T> {
  const r = await api.get<T>(url, { params, paramsSerializer: { indexes: null } });
  return r.data;
}

export async function post<T>(url: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  const r = await api.post<T>(url, body, { headers });
  return r.data;
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  return (await api.put<T>(url, body)).data;
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  return (await api.patch<T>(url, body)).data;
}

export async function del<T>(url: string, params?: object): Promise<T> {
  return (await api.delete<T>(url, { params })).data;
}

export async function upload<T>(url: string, file: File, onProgress?: (pct: number) => void): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  const r = await api.post<T>(url, form, {
    onUploadProgress: (ev: AxiosProgressEvent) => {
      if (onProgress && ev.total) onProgress(Math.round((ev.loaded / ev.total) * 100));
    },
  });
  return r.data;
}

/** Downloads a file response (e.g. CSV export) and saves it via a temporary object URL. */
export async function download(url: string, params: object, fallbackName: string): Promise<void> {
  const r = await api.get<Blob>(url, { params, responseType: 'blob' });
  const disposition = String(r.headers['content-disposition'] ?? '');
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  const name = match?.[1] ?? fallbackName;
  const href = URL.createObjectURL(r.data);
  const a = document.createElement('a');
  a.href = href;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(href);
}

/** Removes empty values so they are not sent as filters. */
export function cleanParams<T extends object>(p: T): Partial<T> {
  const out: Record<string, unknown> = {};
  Object.entries(p).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out as Partial<T>;
}
