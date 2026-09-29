export type Row = Record<string, any>;
export type Page = {
  content: Row[];
  totalElements: number;
  page: number;
  size: number;
  totalAmount?: number;
};
export type User = { id: number; name: string; email: string; role: 'OWNER' | 'CASHIER' };
export async function api<T = any>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const token = sessionStorage.getItem('pharmacy-token');
  const response = await fetch(`/api${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && !path.includes('/auth/login')) {
      sessionStorage.removeItem('pharmacy-token');
      window.dispatchEvent(new Event('auth-expired'));
    }
    throw new Error(data.detail || `Request failed (${response.status})`);
  }
  return data;
}
export const money = (value: unknown) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(Number(value || 0));
export const date = (value: unknown) =>
  value
    ? new Date(String(value)).toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      })
    : '—';
export const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
export const params = (values: Record<string, unknown>) =>
  new URLSearchParams(
    Object.entries(values)
      .filter(([, v]) => v !== '' && v != null)
      .map(([k, v]) => [k, String(v)]),
  ).toString();
