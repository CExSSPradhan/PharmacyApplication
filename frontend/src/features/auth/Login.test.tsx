import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import Login from './Login';
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
describe('Login', () => {
  it('shows server errors without navigating', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue({
          ok: false,
          status: 401,
          json: async () => ({ detail: 'Invalid email or password' }),
        }),
    );
    const onLogin = vi.fn();
    render(<Login onLogin={onLogin} />);
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password'),
    );
    expect(onLogin).not.toHaveBeenCalled();
  });
  it('rejects an empty password before calling the API', async () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    render(<Login onLogin={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(screen.getByText('Enter your password')).toBeVisible());
    expect(fetch).not.toHaveBeenCalled();
  });
});
