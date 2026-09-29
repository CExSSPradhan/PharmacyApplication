import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Row } from '../../api/client';
import { Heading, Table, Badge, Modal, ErrorBox } from '../../components/ui';
export default function Users() {
  const client = useQueryClient();
  const q = useQuery({ queryKey: ['users'], queryFn: () => api<Row[]>('/users') });
  const [open, setOpen] = useState(false),
    [error, setError] = useState<unknown>(),
    [busy, setBusy] = useState(false);
  return (
    <>
      <Heading
        eyebrow="WORKSPACE SETTINGS"
        title="Team"
        description="Create individual owner and cashier accounts for your team."
        action={
          <button className="primary" onClick={() => setOpen(true)}>
            Add team member
          </button>
        }
      />
      <section className="panel">
        <ErrorBox error={q.error} />
        <Table
          rows={q.data || []}
          columns={[
            { label: 'Name', render: (r) => r.name },
            { label: 'Email', render: (r) => r.email },
            { label: 'Role', render: (r) => r.role },
            { label: 'Status', render: (r) => <Badge>{r.status}</Badge> },
          ]}
        />
      </section>
      {open && (
        <Modal title="Add team member" onClose={() => setOpen(false)}>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const data = Object.fromEntries(new FormData(e.currentTarget));
              setBusy(true);
              setError(null);
              try {
                await api('/users', 'POST', data);
                await client.invalidateQueries({ queryKey: ['users'] });
                setOpen(false);
              } catch (e) {
                setError(e);
              } finally {
                setBusy(false);
              }
            }}
          >
            <div className="form-grid">
              <label>
                Name
                <input name="name" required maxLength={100} />
              </label>
              <label>
                Email
                <input name="email" type="email" required maxLength={150} />
              </label>
              <label>
                Initial password
                <input
                  name="password"
                  type="password"
                  required
                  minLength={12}
                  maxLength={72}
                  autoComplete="new-password"
                />
              </label>
              <label>
                Role
                <select name="role">
                  <option>CASHIER</option>
                  <option>OWNER</option>
                </select>
              </label>
            </div>
            <ErrorBox error={error} />
            <div className="form-actions">
              <button type="button" onClick={() => setOpen(false)}>
                Cancel
              </button>
              <button className="primary" disabled={busy}>
                Create account
              </button>
            </div>
          </form>
        </Modal>
      )}
    </>
  );
}
