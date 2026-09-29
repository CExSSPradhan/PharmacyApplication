import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  ArrowUpRight,
  IndianRupee,
  ReceiptText,
  PackageMinus,
  CalendarClock,
  Plus,
  ArrowRight,
  Package,
  TriangleAlert,
} from 'lucide-react';
import { api, money, date, type User, type Row, type Page } from '../../api/client';
import { Heading, ErrorBox, Loading, Badge, Table } from '../../components/ui';
export default function Dashboard({ user }: { user: User }) {
  const summary = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api<Row>('/dashboard/summary'),
  });
  const low = useQuery({
    queryKey: ['low-dashboard'],
    queryFn: () => api<Page>('/inventory/low-stock?size=5'),
  });
  const d = summary.data;
  return (
    <>
      <Heading
        eyebrow="YOUR PHARMACY, AT A GLANCE"
        title="Overview"
        description={`Welcome back, ${user.name}. Here’s where things stand today.`}
        action={
          <Link className="primary" to="/sales/new">
            <Plus size={18} />
            New sale
          </Link>
        }
      />
      <ErrorBox error={summary.error} />
      {summary.isLoading ? (
        <Loading />
      ) : (
        d && (
          <>
            <div className="stats">
              {[
                {
                  label: 'Today’s sales',
                  value: money(d.today_sales),
                  note: 'Completed sales today',
                  icon: IndianRupee,
                  tone: 'teal',
                },
                {
                  label: 'Bills created',
                  value: d.bill_count,
                  note: 'Completed bills today',
                  icon: ReceiptText,
                  tone: 'blue',
                },
                {
                  label: 'Low stock',
                  value: d.low_stock_count,
                  note: 'Medicines to replenish',
                  icon: PackageMinus,
                  tone: 'amber',
                },
                {
                  label: 'Expiring soon',
                  value: d.near_expiry_count,
                  note: 'Batches in the next 30 days',
                  icon: CalendarClock,
                  tone: 'rose',
                },
              ].map((s) => (
                <article className="stat" key={s.label}>
                  <div className="stat-top">
                    <span>{s.label}</span>
                    <span className={`stat-icon ${s.tone}`}>
                      <s.icon size={20} />
                    </span>
                  </div>
                  <strong>{s.value}</strong>
                  <small>{s.note}</small>
                </article>
              ))}
            </div>
            <div className="dashboard-grid">
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Recent sales</h2>
                    <p>Your latest transactions, all in one place.</p>
                  </div>
                  <Link className="text-link" to="/sales">
                    View all
                    <ArrowUpRight size={16} />
                  </Link>
                </div>
                <Table
                  rows={d.recent_sales}
                  empty="Your first sale starts here. Create a sale to see it in your activity."
                  columns={[
                    {
                      label: 'Invoice',
                      render: (r) => <span className="mono">{r.invoice_number.slice(0, 15)}</span>,
                    },
                    { label: 'Date', render: (r) => date(r.sale_date) },
                    { label: 'Amount', render: (r) => <strong>{money(r.total_amount)}</strong> },
                    { label: 'Status', render: (r) => <Badge>{r.status}</Badge> },
                  ]}
                />
              </section>
              <section className="panel attention">
                <div className="panel-heading">
                  <div>
                    <h2>Needs attention</h2>
                    <p>A little check now goes a long way.</p>
                  </div>
                  <span className="amber stat-icon">
                    <TriangleAlert size={18} />
                  </span>
                </div>
                <Link className="attention-row" to="/inventory?view=low-stock">
                  <span className="soft-icon amber">
                    <PackageMinus size={20} />
                  </span>
                  <span>
                    <strong>Low-stock medicines</strong>
                    <small>Review your reorder list</small>
                  </span>
                  <b>{d.low_stock_count}</b>
                  <ArrowRight size={16} />
                </Link>
                <Link className="attention-row" to="/inventory?view=near-expiry">
                  <span className="soft-icon rose">
                    <CalendarClock size={20} />
                  </span>
                  <span>
                    <strong>Near-expiry batches</strong>
                    <small>Prioritize stock by expiry</small>
                  </span>
                  <b>{d.near_expiry_count}</b>
                  <ArrowRight size={16} />
                </Link>
                <Link className="attention-row" to="/inventory?view=expired">
                  <span className="soft-icon muted">
                    <Package size={20} />
                  </span>
                  <span>
                    <strong>Expired batches</strong>
                    <small>Excluded from sellable stock</small>
                  </span>
                  <b>{d.expired_stock_count}</b>
                  <ArrowRight size={16} />
                </Link>
                <div className="inventory-note">
                  <ShieldIllustration />
                  <p>
                    <strong>Every unit accounted for.</strong>
                    <br />
                    Sales allocate the earliest-expiring eligible batch automatically.
                  </p>
                </div>
              </section>
              <section className="panel">
                <div className="panel-heading">
                  <div>
                    <h2>Time to restock</h2>
                    <p>Medicines at or below their reorder threshold.</p>
                  </div>
                  <Link className="text-link" to="/inventory?view=low-stock">
                    Inventory
                    <ArrowUpRight size={16} />
                  </Link>
                </div>
                <ErrorBox error={low.error} />
                <Table
                  rows={low.data?.content || []}
                  empty="Stock levels look healthy."
                  columns={[
                    {
                      label: 'Medicine',
                      render: (r) => (
                        <div className="medicine-cell">
                          <span className="medicine-icon">
                            <Package size={18} />
                          </span>
                          <span>
                            <strong>{r.name}</strong>
                            <small>{r.strength}</small>
                          </span>
                        </div>
                      ),
                    },
                    {
                      label: 'Available',
                      render: (r) => <Badge kind="amber">{r.available_quantity} units</Badge>,
                    },
                    { label: 'Reorder at', render: (r) => `${r.low_stock_threshold} units` },
                  ]}
                />
              </section>
              <section className="quick-card">
                <span className="eyebrow">KEEP THINGS MOVING</span>
                <h2>
                  New delivery?
                  <br />
                  Let’s get it on the shelf.
                </h2>
                <p>Record a supplier invoice and update your batch inventory in one step.</p>
                {user.role === 'OWNER' ? (
                  <Link to="/purchases/new" className="primary">
                    <Plus size={17} />
                    Record purchase
                  </Link>
                ) : (
                  <Link to="/inventory" className="primary">
                    View inventory
                    <ArrowRight size={17} />
                  </Link>
                )}
              </section>
            </div>
          </>
        )
      )}
    </>
  );
}
function ShieldIllustration() {
  return <span className="note-cross">✚</span>;
}
