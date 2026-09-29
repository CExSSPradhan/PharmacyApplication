import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Search, Layers } from 'lucide-react';
import { api, params, date, money, type Page, type Row } from '../../api/client';
import { Heading, Table, Pager, ErrorBox, Loading, Badge, Modal } from '../../components/ui';
function BatchDetail({ medicine, onClose }: { medicine: Row; onClose: () => void }) {
  const batches = useQuery({
    queryKey: ['batches', medicine.id],
    queryFn: () => api<Row[]>(`/inventory/medicines/${medicine.id}/batches`),
  });
  const [batch, setBatch] = useState<number>();
  const movements = useQuery({
    queryKey: ['movements', batch],
    queryFn: () => api<Row[]>(`/inventory/batches/${batch}/movements`),
    enabled: !!batch,
  });
  return (
    <Modal wide title={`${medicine.name} · Batch inventory`} onClose={onClose}>
      <ErrorBox error={batches.error} />
      <Table
        rows={batches.data || []}
        columns={[
          { label: 'Batch', render: (r) => r.batch_number },
          { label: 'Expiry', render: (r) => date(r.expiry_date) },
          { label: 'On hand', render: (r) => r.current_quantity },
          {
            label: 'Cost / sell',
            render: (r) => `${money(r.purchase_price)} / ${money(r.selling_price)}`,
          },
          {
            label: 'Audit',
            render: (r) => <button onClick={() => setBatch(r.id)}>View movements</button>,
          },
        ]}
      />
      {batch && (
        <>
          <h3 className="section-title">Stock movements · Batch #{batch}</h3>
          <ErrorBox error={movements.error} />
          <Table
            rows={movements.data || []}
            columns={[
              { label: 'Movement', render: (r) => r.movement_type },
              { label: 'Quantity', render: (r) => r.quantity },
              { label: 'By', render: (r) => r.user_name },
              { label: 'Date', render: (r) => date(r.created_at) },
              {
                label: 'Reference / notes',
                render: (r) => `${r.reference_type} #${r.reference_id} ${r.notes || ''}`,
              },
            ]}
          />
        </>
      )}
    </Modal>
  );
}
export default function Inventory() {
  const [query, setQuery] = useSearchParams();
  const view = query.get('view') || 'current';
  const [name, setName] = useState(''),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<Row>();
  const batchView = view === 'near-expiry' || view === 'expired';
  const result = useQuery({
    queryKey: ['inventory', view, name, page],
    queryFn: () => api<Page>(`/inventory/${view}?${params({ name, page })}`),
  });
  return (
    <>
      <Heading
        eyebrow="STOCK CONTROL"
        title="Inventory"
        description="Know what’s on the shelf, what’s running low, and what’s next to expire."
      />
      <section className="panel">
        <div className="tabs">
          {[
            ['current', 'All stock'],
            ['low-stock', 'Low stock'],
            ['out-of-stock', 'Out of stock'],
            ['near-expiry', 'Near expiry'],
            ['expired', 'Expired'],
          ].map(([key, label]) => (
            <button
              className={view === key ? 'active' : ''}
              key={key}
              onClick={() => {
                setQuery({ view: key });
                setPage(0);
              }}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="toolbar">
          <label className="search">
            <Search size={18} />
            <input
              placeholder="Search inventory…"
              aria-label="Search inventory"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                setPage(0);
              }}
            />
          </label>
          <span className="hint">
            {batchView
              ? 'Batch quantities · expiry based on calendar date'
              : 'Sellable stock excludes expired and inactive batches'}
          </span>
        </div>
        <ErrorBox error={result.error} />
        {result.isLoading ? (
          <Loading />
        ) : (
          <Table
            rows={result.data?.content || []}
            empty="No stock matches this view."
            columns={[
              {
                label: 'Medicine',
                render: (r) => (
                  <div>
                    <strong>{r.name}</strong>
                    <small>{r.strength}</small>
                  </div>
                ),
              },
              ...(batchView
                ? [
                    { label: 'Batch', render: (r: Row) => r.batch_number },
                    {
                      label: 'Expiry',
                      render: (r: Row) => (
                        <Badge kind={view === 'expired' ? 'rose' : 'amber'}>
                          {date(r.expiry_date)}
                        </Badge>
                      ),
                    },
                    { label: 'On hand', render: (r: Row) => r.current_quantity },
                    { label: 'Selling price', render: (r: Row) => money(r.selling_price) },
                  ]
                : [
                    {
                      label: 'Available',
                      render: (r: Row) => (
                        <Badge
                          kind={r.available_quantity <= r.low_stock_threshold ? 'amber' : 'green'}
                        >
                          {r.available_quantity} units
                        </Badge>
                      ),
                    },
                    { label: 'Expired units', render: (r: Row) => r.expired_quantity },
                    { label: 'Reorder at', render: (r: Row) => r.low_stock_threshold },
                    { label: 'On-hand value', render: (r: Row) => money(r.stock_value) },
                    {
                      label: 'Batches',
                      render: (r: Row) => (
                        <button className="subtle" onClick={() => setSelected(r)}>
                          <Layers size={16} />
                          View batches
                        </button>
                      ),
                    },
                  ]),
            ]}
          />
        )}
        <Pager data={result.data} page={page} setPage={setPage} />
      </section>
      {selected && <BatchDetail medicine={selected} onClose={() => setSelected(undefined)} />}
    </>
  );
}
