import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Search, Pencil } from 'lucide-react';
import { api, params, date, type Row, type Page, type User } from '../../api/client';
import { Heading, Modal, Table, Pager, ErrorBox, Loading, Badge } from '../../components/ui';
const schema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(150),
    status: z.enum(['ACTIVE', 'INACTIVE']),
  })
  .passthrough();
export function MasterForm({
  type,
  record,
  onDone,
  onClose,
}: {
  type: 'medicines' | 'suppliers';
  record?: Row;
  onDone: (r: Row) => void;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const [error, setError] = useState<unknown>();
  const defaults: Row =
    type === 'medicines'
      ? {
          name: '',
          manufacturer: '',
          composition: '',
          strength: '',
          dosageForm: 'Tablet',
          barcode: '',
          lowStockThreshold: 10,
          status: 'ACTIVE',
        }
      : {
          name: '',
          contactPerson: '',
          phone: '',
          email: '',
          address: '',
          gstNumber: '',
          status: 'ACTIVE',
        };
  if (record)
    Object.keys(defaults).forEach((k) => {
      const snake = k.replace(/[A-Z]/g, (m) => '_' + m.toLowerCase());
      defaults[k] = record[snake] ?? record[k] ?? defaults[k];
    });
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: defaults });
  const fields =
    type === 'medicines'
      ? [
          ['name', 'Medicine name'],
          ['manufacturer', 'Manufacturer'],
          ['composition', 'Composition'],
          ['strength', 'Strength'],
          ['dosageForm', 'Dosage form'],
          ['barcode', 'Barcode'],
          ['lowStockThreshold', 'Low-stock threshold'],
        ]
      : [
          ['name', 'Supplier name'],
          ['contactPerson', 'Contact person'],
          ['phone', 'Phone'],
          ['email', 'Email'],
          ['address', 'Address'],
          ['gstNumber', 'GST number'],
        ];
  return (
    <form
      onSubmit={handleSubmit(async (value) => {
        setError(null);
        try {
          const result = await api(
            `/${type}${record ? '/' + record.id : ''}`,
            record ? 'PUT' : 'POST',
            value,
          );
          await client.invalidateQueries();
          onDone(result);
        } catch (e) {
          setError(e);
        }
      })}
    >
      <div className="form-grid">
        {fields.map(([key, label]) => (
          <label key={key}>
            {label}
            <input
              {...register(key, key === 'lowStockThreshold' ? { valueAsNumber: true } : {})}
              type={key === 'lowStockThreshold' ? 'number' : key === 'email' ? 'email' : 'text'}
              min={0}
              required={key === 'name'}
              maxLength={
                key === 'name' ? 150 : key === 'phone' ? 20 : key === 'gstNumber' ? 30 : undefined
              }
            />
            {errors[key] && <small className="field-error">{String(errors[key]?.message)}</small>}
          </label>
        ))}
        <label>
          Status
          <select {...register('status')}>
            <option>ACTIVE</option>
            <option>INACTIVE</option>
          </select>
        </label>
      </div>
      <ErrorBox error={error} />
      <div className="form-actions">
        <button type="button" onClick={onClose}>
          Cancel
        </button>
        <button className="primary" disabled={isSubmitting}>
          {isSubmitting
            ? 'Saving…'
            : record
              ? 'Save changes'
              : 'Create ' + (type === 'medicines' ? 'medicine' : 'supplier')}
        </button>
      </div>
    </form>
  );
}
export default function Masters({ type, user }: { type: 'medicines' | 'suppliers'; user: User }) {
  const [name, setName] = useState(''),
    [from, setFrom] = useState(''),
    [to, setTo] = useState(''),
    [page, setPage] = useState(0),
    [editing, setEditing] = useState<Row | null | undefined>();
  const result = useQuery({
    queryKey: [type, name, from, to, page],
    queryFn: () => api<Page>(`/${type}?${params({ name, from, to, page })}`),
  });
  const medicine = type === 'medicines';
  return (
    <>
      <Heading
        eyebrow="MASTER RECORDS"
        title={medicine ? 'Medicines' : 'Suppliers'}
        description={
          medicine
            ? 'A clear, organized home for every medicine you carry.'
            : 'Keep the people behind your stock close at hand.'
        }
        action={
          user.role === 'OWNER' && (
            <button className="primary" onClick={() => setEditing(null)}>
              <Plus size={18} />
              Add {medicine ? 'medicine' : 'supplier'}
            </button>
          )
        }
      />
      <section className="panel">
        <div className="toolbar">
          <label className="search">
            <Search size={18} />
            <input
              aria-label="Search by name"
              placeholder={`Search ${type}…`}
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                setPage(0);
              }}
            />
          </label>
          {medicine && (
            <>
              <label>
                Entry from
                <input
                  aria-label="Entry from"
                  type="date"
                  value={from}
                  onChange={(e) => {
                    setFrom(e.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <label>
                Entry to
                <input
                  aria-label="Entry to"
                  type="date"
                  value={to}
                  onChange={(e) => {
                    setTo(e.target.value);
                    setPage(0);
                  }}
                />
              </label>
            </>
          )}
        </div>
        <ErrorBox error={result.error} />
        {result.isLoading ? (
          <Loading />
        ) : (
          <Table
            rows={result.data?.content || []}
            columns={[
              {
                label: medicine ? 'Medicine' : 'Supplier',
                render: (r) => (
                  <div>
                    <strong>{r.name}</strong>
                    <small>{medicine ? r.composition : r.contact_person}</small>
                  </div>
                ),
              },
              ...(medicine
                ? [
                    {
                      label: 'Strength / form',
                      render: (r: Row) => `${r.strength || '—'} · ${r.dosage_form || '—'}`,
                    },
                    { label: 'Manufacturer', render: (r: Row) => r.manufacturer || '—' },
                    { label: 'Reorder at', render: (r: Row) => `${r.low_stock_threshold} units` },
                  ]
                : [
                    { label: 'Phone', render: (r: Row) => r.phone || '—' },
                    { label: 'Email', render: (r: Row) => r.email || '—' },
                  ]),
              { label: 'Added', render: (r) => date(r.created_at) },
              { label: 'Status', render: (r) => <Badge>{r.status}</Badge> },
              {
                label: '',
                render: (r) =>
                  user.role === 'OWNER' && (
                    <button
                      className="icon-button"
                      aria-label={`Edit ${r.name}`}
                      onClick={() => setEditing(r)}
                    >
                      <Pencil size={17} />
                    </button>
                  ),
              },
            ]}
          />
        )}
        <Pager data={result.data} page={page} setPage={setPage} />
      </section>
      {editing !== undefined && (
        <Modal
          title={`${editing ? 'Edit' : 'Add'} ${medicine ? 'medicine' : 'supplier'}`}
          onClose={() => setEditing(undefined)}
        >
          <MasterForm
            type={type}
            record={editing || undefined}
            onClose={() => setEditing(undefined)}
            onDone={() => setEditing(undefined)}
          />
        </Modal>
      )}
    </>
  );
}
