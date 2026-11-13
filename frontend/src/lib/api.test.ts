import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { cleanParams, toApiError } from './api';

describe('toApiError', () => {
  it('uses the server envelope message and field errors', () => {
    const headers = new AxiosHeaders();
    const e = new AxiosError('x', 'ERR', { headers } as never, null, {
      status: 422, statusText: '', headers, config: { headers } as never,
      data: { code: 'VALIDATION_FAILED', message: 'One or more fields are invalid.', fieldErrors: [{ field: 'email', message: 'bad' }] },
    });
    const err = toApiError(e);
    expect(err.status).toBe(422);
    expect(err.code).toBe('VALIDATION_FAILED');
    expect(err.fieldErrors[0]?.field).toBe('email');
  });

  it('never exposes raw technical text for network failures', () => {
    const err = toApiError(new AxiosError('connect ECONNREFUSED 127.0.0.1:8080', 'ERR_NETWORK'));
    expect(err.message).toMatch(/Cannot reach the LandStack server/);
    expect(err.message).not.toMatch(/ECONNREFUSED/);
  });

  it('cleanParams drops empty values', () => {
    expect(cleanParams({ a: '', b: undefined, c: 'x', d: 0 })).toEqual({ c: 'x', d: 0 });
  });
});
