import { describe, expect, it } from 'vitest';
import { validateFile } from './UploadPage';

const file = (name: string, size: number) => new File([new Uint8Array(size)], name);

describe('validateFile (client-side convenience checks)', () => {
  it('accepts supported types', () => {
    expect(validateFile(file('a.csv', 10))).toBeNull();
    expect(validateFile(file('b.XLSX', 10))).toBeNull();
    expect(validateFile(file('c.pdf', 10))).toBeNull();
  });
  it('rejects unsupported, legacy, empty and oversized files', () => {
    expect(validateFile(file('a.exe', 10))).toMatch(/Unsupported/);
    expect(validateFile(file('a.xls', 10))).toMatch(/xlsx/);
    expect(validateFile(file('a.csv', 0))).toMatch(/empty/);
    expect(validateFile(file('a.csv', 21 * 1024 * 1024))).toMatch(/larger/);
  });
});
