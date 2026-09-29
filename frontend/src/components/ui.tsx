import { useEffect, useRef, type ReactNode } from 'react';
import { X, ChevronLeft, ChevronRight, PackageOpen, LoaderCircle } from 'lucide-react';
import type { Page, Row } from '../api/client';
export function ErrorBox({ error }: { error: unknown }) {
  return error ? (
    <div className="error" role="alert">
      {error instanceof Error ? error.message : String(error)}
    </div>
  ) : null;
}
export function Loading() {
  return (
    <div className="empty">
      <LoaderCircle className="spin" size={24} /> Loading your workspace…
    </div>
  );
}
export function Badge({ children, kind = '' }: { children: ReactNode; kind?: string }) {
  return (
    <span
      className={`badge ${kind || (children === 'ACTIVE' || children === 'COMPLETED' ? 'green' : 'muted')}`}
    >
      {children}
    </span>
  );
}
export function Modal({
  title,
  onClose,
  children,
  wide = false,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const node = ref.current;
    node?.showModal();
    return () => node?.close();
  }, []);
  return (
    <dialog ref={ref} className={wide ? 'wide' : ''} onCancel={onClose}>
      <div className="modal-head">
        <h2>{title}</h2>
        <button className="icon-button" aria-label="Close dialog" onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function Table({
  rows,
  columns,
  empty = 'No records yet. Add your first record to get started.',
}: {
  rows: Row[];
  columns: { label: string; render: (r: Row) => ReactNode }[];
  empty?: string;
}) {
  if (!rows.length)
    return (
      <div className="empty">
        <PackageOpen size={32} />
        <p>{empty}</p>
      </div>
    );
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            {columns.map((c, i) => (
              <th key={i}>{c.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r.id ?? i}>
              {columns.map((c, j) => (
                <td key={j}>{c.render(r)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function Pager({
  data,
  page,
  setPage,
}: {
  data?: Page;
  page: number;
  setPage: (n: number) => void;
}) {
  return (
    <div className="pager">
      <span>
        {data?.totalElements || 0} records · Page {page + 1}
      </span>
      <div>
        <button aria-label="Previous page" disabled={page === 0} onClick={() => setPage(page - 1)}>
          <ChevronLeft size={16} />
        </button>
        <button
          aria-label="Next page"
          disabled={!data || (page + 1) * data.size >= data.totalElements}
          onClick={() => setPage(page + 1)}
        >
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  );
}
export function Heading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="subtitle">{description}</p>
      </div>
      {action}
    </div>
  );
}
