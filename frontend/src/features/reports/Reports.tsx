import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, params, date, money, type Page } from '../../api/client';
import { Heading, Table, Pager, ErrorBox, Loading, Badge } from '../../components/ui';
export default function Reports() {
  const [type, setType] = useState('sales'),
    [from, setFrom] = useState(''),
    [to, setTo] = useState(''),
    [page, setPage] = useState(0);
  const result = useQuery({
    queryKey: ['reports', type, from, to, page],
    queryFn: () => api<Page>(`/reports/${type}?${params({ from, to, page })}`),
  });
  return (
    <>
      <Heading
        eyebrow="BUSINESS INSIGHTS"
        title="Reports"
        description="Review your sales, purchases, and current stock with clear date filters."
      />
      <section className="panel">
        <div className="tabs">
          {['sales', 'purchases', 'stock'].map((t) => (
            <button
              className={type === t ? 'active' : ''}
              key={t}
              onClick={() => {
                setType(t);
                setPage(0);
              }}
            >
              {t[0].toUpperCase() + t.slice(1)}
            </button>
          ))}
        </div>
        <div className="toolbar">
          {type !== 'stock' ? (
            <>
              <label>
                From date
                <input
                  type="date"
                  value={from}
                  onChange={(e) => {
                    setFrom(e.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <label>
                To date
                <input
                  type="date"
                  value={to}
                  onChange={(e) => {
                    setTo(e.target.value);
                    setPage(0);
                  }}
                />
              </label>
              <div className="toolbar-total">
                <small>Completed total · excludes cancellations</small>
                <strong>{money(result.data?.totalAmount)}</strong>
              </div>
            </>
          ) : (
            <p className="hint">
              Live stock snapshot. Values include all on-hand units; available quantity excludes
              expired and inactive batches.
            </p>
          )}
        </div>
        <ErrorBox error={result.error} />
        {result.isLoading ? (
          <Loading />
        ) : (
          <Table
            rows={result.data?.content || []}
            columns={
              type === 'stock'
                ? [
                    { label: 'Medicine', render: (r) => r.name },
                    { label: 'Available', render: (r) => r.available_quantity },
                    { label: 'Expired units', render: (r) => r.expired_quantity },
                    { label: 'Stock value', render: (r) => money(r.stock_value) },
                  ]
                : [
                    {
                      label: 'Invoice',
                      render: (r) => <span className="mono">{r.invoice_number}</span>,
                    },
                    { label: 'Date', render: (r) => date(r.sale_date || r.purchase_date) },
                    { label: 'Amount', render: (r) => money(r.total_amount) },
                    { label: 'Status', render: (r) => <Badge>{r.status}</Badge> },
                  ]
            }
          />
        )}
        <Pager data={result.data} page={page} setPage={setPage} />
      </section>
    </>
  );
}
