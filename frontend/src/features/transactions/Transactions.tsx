import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2, ArrowLeft, Check, Printer, Search } from 'lucide-react';
import { api, params, money, date, today, type Row, type Page, type User } from '../../api/client';
import { Heading, Table, Pager, ErrorBox, Loading, Badge, Modal } from '../../components/ui';
import { MasterForm } from '../catalog/Masters';
type Line = {
  key: number;
  medicineId: number;
  name: string;
  quantity: number;
  batchNumber: string;
  expiryDate: string;
  purchasePrice: string;
  sellingPrice: string;
};
export function Receipt({
  type,
  id,
  user,
  onClose,
}: {
  type: 'sales' | 'purchases';
  id: number;
  user: User;
  onClose: () => void;
}) {
  const client = useQueryClient();
  const result = useQuery({
    queryKey: [type, 'detail', id],
    queryFn: () => api<Row>(`/${type}/${id}`),
  });
  const [reason, setReason] = useState(''),
    [cancel, setCancel] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>();
  const d = result.data;
  return (
    <Modal wide title={type === 'sales' ? 'Sale receipt' : 'Purchase invoice'} onClose={onClose}>
      <ErrorBox error={result.error} />
      {result.isLoading ? (
        <Loading />
      ) : (
        d && (
          <div className="receipt">
            <div className="receipt-header">
              <div>
                <p className="eyebrow">PHARMACY WORKSPACE</p>
                <h2>{type === 'sales' ? 'Sales receipt' : 'Purchase invoice'}</h2>
                <p className="mono wrap">{d.invoice_number}</p>
                <p>{date(d.sale_date || d.purchase_date)}</p>
              </div>
              <Badge>{d.status}</Badge>
            </div>
            <Table
              rows={d.items}
              columns={[
                { label: 'Medicine', render: (r) => r.medicine_name },
                {
                  label: 'Batch / expiry',
                  render: (r) => (
                    <>
                      {r.batch_number}
                      <small>{date(r.expiry_date)}</small>
                    </>
                  ),
                },
                { label: 'Quantity', render: (r) => r.quantity },
                { label: 'Price', render: (r) => money(r.unit_price ?? r.purchase_price) },
                { label: 'Total', render: (r) => money(r.line_total) },
              ]}
            />
            <div className="receipt-total">
              <span>Total amount</span>
              <strong>{money(d.total_amount)}</strong>
            </div>
            {d.cancellation_reason && <p className="notice">Cancelled: {d.cancellation_reason}</p>}
            <ErrorBox error={error} />
            {cancel && (
              <form
                className="cancel-form"
                onSubmit={async (e) => {
                  e.preventDefault();
                  setBusy(true);
                  setError(null);
                  try {
                    await api(`/sales/${id}/cancel`, 'POST', { reason });
                    await client.invalidateQueries();
                    setCancel(false);
                  } catch (e) {
                    setError(e);
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                <label>
                  Cancellation reason
                  <textarea
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    required
                    maxLength={255}
                    placeholder="Why is this sale being cancelled?"
                  />
                </label>
                <p className="hint">
                  This restores the exact batches used by this sale and preserves its audit history.
                </p>
                <button className="danger" disabled={busy || !reason.trim()}>
                  {busy ? 'Restoring stock…' : 'Confirm cancellation'}
                </button>
              </form>
            )}
            <div className="form-actions no-print">
              {type === 'sales' && d.status === 'COMPLETED' && user.role === 'OWNER' && !cancel && (
                <button className="danger subtle" onClick={() => setCancel(true)}>
                  Cancel sale
                </button>
              )}
              <button onClick={() => window.print()}>
                <Printer size={17} />
                Print receipt
              </button>
              <button className="primary" onClick={onClose}>
                Done
              </button>
            </div>
          </div>
        )
      )}
    </Modal>
  );
}
export function History({ type, user }: { type: 'sales' | 'purchases'; user: User }) {
  const [from, setFrom] = useState(''),
    [to, setTo] = useState(''),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<number>();
  const sale = type === 'sales';
  const result = useQuery({
    queryKey: [type, from, to, page],
    queryFn: () => api<Page>(`/${type}?${params({ from, to, page })}`),
  });
  return (
    <>
      <Heading
        eyebrow="TRANSACTIONS"
        title={sale ? 'Sales' : 'Purchases'}
        description={
          sale
            ? 'Every bill, every batch, and a history you can trust.'
            : 'From supplier invoices to stock on the shelf.'
        }
        action={
          (sale || user.role === 'OWNER') && (
            <Link className="primary" to={`/${type}/new`}>
              <Plus size={18} />
              {sale ? 'New sale' : 'Record purchase'}
            </Link>
          )
        }
      />
      <section className="panel">
        <div className="toolbar">
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
            <small>Completed total</small>
            <strong>{money(result.data?.totalAmount)}</strong>
          </div>
        </div>
        <ErrorBox error={result.error} />
        {result.isLoading ? (
          <Loading />
        ) : (
          <Table
            rows={result.data?.content || []}
            columns={[
              {
                label: 'Invoice',
                render: (r) => (
                  <button className="text-link mono" onClick={() => setSelected(r.id)}>
                    {r.invoice_number}
                  </button>
                ),
              },
              { label: 'Date', render: (r) => date(r.sale_date || r.purchase_date) },
              { label: 'Amount', render: (r) => <strong>{money(r.total_amount)}</strong> },
              { label: 'Status', render: (r) => <Badge>{r.status}</Badge> },
              {
                label: '',
                render: (r) => (
                  <button onClick={() => setSelected(r.id)}>
                    View {sale ? 'receipt' : 'invoice'}
                  </button>
                ),
              },
            ]}
          />
        )}
        <Pager data={result.data} page={page} setPage={setPage} />
      </section>
      {selected && (
        <Receipt type={type} id={selected} user={user} onClose={() => setSelected(undefined)} />
      )}
    </>
  );
}
export function TransactionForm({ type, user }: { type: 'sales' | 'purchases'; user: User }) {
  const purchase = type === 'purchases';
  const navigate = useNavigate(),
    client = useQueryClient();
  const [search, setSearch] = useState(''),
    [supplierSearch, setSupplierSearch] = useState(''),
    [supplierId, setSupplierId] = useState(''),
    [invoice, setInvoice] = useState(''),
    [purchaseDate, setPurchaseDate] = useState(today()),
    [lines, setLines] = useState<Line[]>([]),
    [inline, setInline] = useState(false),
    [error, setError] = useState<unknown>(),
    [busy, setBusy] = useState(false),
    [receipt, setReceipt] = useState<number>();
  const medicines = useQuery({
    queryKey: ['medicines-picker', search],
    queryFn: () => api<Page>(`/medicines?${params({ name: search, size: 100 })}`),
  });
  const suppliers = useQuery({
    queryKey: ['suppliers-picker', supplierSearch],
    queryFn: () => api<Page>(`/suppliers?${params({ name: supplierSearch, size: 100 })}`),
    enabled: purchase,
  });
  const stock = useQuery({
    queryKey: ['inventory-picker', search],
    queryFn: () => api<Page>(`/inventory?${params({ name: search, size: 100 })}`),
    enabled: !purchase,
  });
  const add = (m: Row) =>
    setLines((prev) => [
      ...prev,
      {
        key: Date.now() + Math.random(),
        medicineId: m.id,
        name: m.name,
        quantity: 1,
        batchNumber: '',
        expiryDate: '',
        purchasePrice: '0.00',
        sellingPrice: '0.00',
      },
    ]);
  const update = (key: number, change: Partial<Line>) =>
    setLines((prev) => prev.map((l) => (l.key === key ? { ...l, ...change } : l)));
  const total = lines.reduce((sum, l) => sum + l.quantity * Number(l.purchasePrice), 0);
  return (
    <>
      <Link className="back-link" to={`/${type}`}>
        <ArrowLeft size={16} />
        Back to {type}
      </Link>
      <Heading
        eyebrow={purchase ? 'RECEIVE STOCK' : 'POINT OF SALE'}
        title={purchase ? 'Record a purchase' : 'Create a sale'}
        description={
          purchase
            ? 'One supplier invoice. Multiple medicines. Stock updated together.'
            : 'Add medicines to the bill. We’ll select eligible batches by earliest expiry.'
        }
      />
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (!lines.length) {
            setError(new Error('Add at least one medicine.'));
            return;
          }
          setBusy(true);
          setError(null);
          try {
            const items = lines.map((l) =>
              purchase
                ? {
                    medicineId: l.medicineId,
                    quantity: l.quantity,
                    batchNumber: l.batchNumber,
                    expiryDate: l.expiryDate,
                    purchasePrice: l.purchasePrice,
                    sellingPrice: l.sellingPrice,
                  }
                : { medicineId: l.medicineId, quantity: l.quantity },
            );
            const d = await api<Row>(
              `/${type}`,
              'POST',
              purchase
                ? { supplierId: Number(supplierId), invoiceNumber: invoice, purchaseDate, items }
                : { items },
            );
            await client.invalidateQueries();
            setReceipt(d.id);
          } catch (e) {
            setError(e);
          } finally {
            setBusy(false);
          }
        }}
      >
        {purchase && (
          <section className="panel form-panel">
            <h2>Invoice details</h2>
            <div className="form-grid four">
              <label>
                Find supplier
                <input
                  placeholder="Search suppliers…"
                  value={supplierSearch}
                  onChange={(e) => setSupplierSearch(e.target.value)}
                />
              </label>
              <label>
                Supplier
                <select required value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
                  <option value="">Select supplier</option>
                  {suppliers.data?.content
                    .filter((s) => s.status === 'ACTIVE')
                    .map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                </select>
              </label>
              <label>
                Invoice number
                <input
                  required
                  maxLength={100}
                  value={invoice}
                  onChange={(e) => setInvoice(e.target.value)}
                  placeholder="e.g. INV-2026-001"
                />
              </label>
              <label>
                Purchase date
                <input
                  required
                  type="date"
                  max={today()}
                  value={purchaseDate}
                  onChange={(e) => setPurchaseDate(e.target.value)}
                />
              </label>
            </div>
            <ErrorBox error={suppliers.error} />
          </section>
        )}
        <div className="entry-layout">
          <section className="panel medicine-picker">
            <div className="panel-heading">
              <div>
                <h2>Add medicines</h2>
                <p>Search and select your items.</p>
              </div>
              {purchase && (
                <button
                  type="button"
                  className="icon-button"
                  title="Create medicine"
                  aria-label="Create medicine inline"
                  onClick={() => setInline(true)}
                >
                  <Plus size={19} />
                </button>
              )}
            </div>
            <div className="picker-search">
              <label className="search">
                <Search size={18} />
                <input
                  aria-label="Find medicine"
                  placeholder="Find a medicine…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </label>
            </div>
            <ErrorBox error={medicines.error || stock.error} />
            <div className="picker-list">
              {medicines.data?.content
                .filter((m) => m.status === 'ACTIVE')
                .map((m) => {
                  const available = stock.data?.content.find(
                    (s) => s.id === m.id,
                  )?.available_quantity;
                  return (
                    <button type="button" key={m.id} onClick={() => add(m)}>
                      <span>
                        <strong>{m.name}</strong>
                        <small>
                          {m.strength} · {m.dosage_form}
                          {!purchase && available !== undefined ? ` · ${available} available` : ''}
                        </small>
                      </span>
                      <Plus size={17} />
                    </button>
                  );
                })}
              {!medicines.isLoading && !medicines.data?.content.length && (
                <p className="empty">
                  No medicines found.
                  {purchase && (
                    <button type="button" onClick={() => setInline(true)}>
                      Create medicine
                    </button>
                  )}
                </p>
              )}
            </div>
            <p className="hint picker-foot">Showing up to 100 matches. Search to narrow results.</p>
          </section>
          <section className="panel bill-panel">
            <div className="panel-heading">
              <div>
                <h2>{purchase ? 'Purchase items' : 'Current bill'}</h2>
                <p>
                  {lines.length} line{lines.length !== 1 ? 's' : ''} added
                </p>
              </div>
              <Badge kind="green">{purchase ? 'STOCK IN' : 'FEFO ALLOCATION'}</Badge>
            </div>
            {!lines.length ? (
              <div className="empty">
                <Plus size={28} />
                <p>Add medicines from the list to start.</p>
              </div>
            ) : (
              <div className="bill-lines">
                {lines.map((l, i) => (
                  <article className="bill-line" key={l.key}>
                    <div className="line-heading">
                      <span className="line-number">{i + 1}</span>
                      <strong>{l.name}</strong>
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`Remove ${l.name}`}
                        onClick={() => setLines((prev) => prev.filter((x) => x.key !== l.key))}
                      >
                        <Trash2 size={17} />
                      </button>
                    </div>
                    <div className={`form-grid ${purchase ? 'three' : ''}`}>
                      <label>
                        Quantity
                        <input
                          type="number"
                          min={1}
                          max={1000000}
                          step={1}
                          required
                          value={l.quantity}
                          onChange={(e) => update(l.key, { quantity: Number(e.target.value) })}
                        />
                      </label>
                      {purchase && (
                        <>
                          <label>
                            Batch number
                            <input
                              required
                              maxLength={100}
                              value={l.batchNumber}
                              onChange={(e) => update(l.key, { batchNumber: e.target.value })}
                            />
                          </label>
                          <label>
                            Expiry date
                            <input
                              type="date"
                              required
                              min={today()}
                              value={l.expiryDate}
                              onChange={(e) => update(l.key, { expiryDate: e.target.value })}
                            />
                          </label>
                          <label>
                            Purchase price (₹)
                            <input
                              type="number"
                              min={0}
                              max={9999999999.99}
                              step="0.01"
                              required
                              value={l.purchasePrice}
                              onChange={(e) => update(l.key, { purchasePrice: e.target.value })}
                            />
                          </label>
                          <label>
                            Selling price (₹)
                            <input
                              type="number"
                              min={0}
                              max={9999999999.99}
                              step="0.01"
                              required
                              value={l.sellingPrice}
                              onChange={(e) => update(l.key, { sellingPrice: e.target.value })}
                            />
                          </label>
                          <div className="line-total">
                            <small>Line total</small>
                            <strong>{money(l.quantity * Number(l.purchasePrice))}</strong>
                          </div>
                        </>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
            <div className="bill-footer">
              {purchase ? (
                <div className="receipt-total">
                  <span>Invoice total</span>
                  <strong>{money(total)}</strong>
                </div>
              ) : (
                <p className="notice">
                  The final amount is calculated from the selling prices of the allocated batches.
                  Review it on the receipt after completing the sale.
                </p>
              )}
              <ErrorBox error={error} />
              <button className="primary full" disabled={busy || !lines.length || !!receipt}>
                <Check size={18} />
                {busy ? 'Processing…' : purchase ? 'Save purchase & update stock' : 'Complete sale'}
              </button>
              <small className="hint">
                All items are saved together. If one item fails, no stock is changed.
              </small>
            </div>
          </section>
        </div>
      </form>
      {inline && (
        <Modal title="Create medicine" onClose={() => setInline(false)}>
          <MasterForm
            type="medicines"
            onClose={() => setInline(false)}
            onDone={(m) => {
              add(m);
              setInline(false);
            }}
          />
        </Modal>
      )}
      {receipt && (
        <Receipt type={type} id={receipt} user={user} onClose={() => navigate(`/${type}`)} />
      )}
    </>
  );
}
