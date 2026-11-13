import { describe, expect, it } from 'vitest';
import { visibleNav } from './AppShell';

describe('role-aware navigation', () => {
  const labels = (r?: Parameters<typeof visibleNav>[0]) => visibleNav(r).map((n) => n.label);

  it('reviewer sees read-only areas only', () => {
    const l = labels('READ_ONLY_REVIEWER');
    expect(l).toContain('Land records');
    expect(l).toContain('Map view');
    expect(l).not.toContain('Import datasets');
    expect(l).not.toContain('User management');
    expect(l).not.toContain('Audit trail');
  });

  it('operator can import but not manage users', () => {
    const l = labels('DATA_OPERATOR');
    expect(l).toContain('Import datasets');
    expect(l).not.toContain('User management');
  });

  it('administrator sees everything', () => {
    expect(labels('ADMINISTRATOR')).toContain('User management');
  });
});
