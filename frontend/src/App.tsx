import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route, NavLink, Navigate, useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  LayoutDashboard,
  Pill,
  Truck,
  ShoppingBag,
  Boxes,
  ReceiptText,
  ChartNoAxesCombined,
  LogOut,
  Users,
  CircleHelp,
  Menu,
  X,
} from 'lucide-react';
import { api, type User } from './api/client';
import Login from './features/auth/Login';
import Dashboard from './features/dashboard/Dashboard';
import Masters from './features/catalog/Masters';
import Inventory from './features/inventory/Inventory';
import { History, TransactionForm } from './features/transactions/Transactions';
import Reports from './features/reports/Reports';
import Team from './features/users/Users';
import { Loading, ErrorBox } from './components/ui';
const navigation = [
  { path: '/dashboard', label: 'Overview', icon: LayoutDashboard },
  { path: '/medicines', label: 'Medicines', icon: Pill },
  { path: '/suppliers', label: 'Suppliers', icon: Truck },
  { path: '/purchases', label: 'Purchases', icon: ShoppingBag },
  { path: '/inventory', label: 'Inventory', icon: Boxes },
  { path: '/sales', label: 'Sales', icon: ReceiptText },
  { path: '/reports', label: 'Reports', icon: ChartNoAxesCombined },
];
function Workspace({ user, onLogout }: { user: User; onLogout: () => void }) {
  const location = useLocation();
  const [menu, setMenu] = useState(false),
    [error, setError] = useState<unknown>();
  const client = useQueryClient();
  useEffect(() => {
    setMenu(false);
  }, [location.pathname]);
  return (
    <div className="workspace">
      <aside className={menu ? 'sidebar open' : 'sidebar'}>
        <div className="brand">
          <span className="brand-icon">
            <Pill size={23} />
          </span>
          pharmacy<span className="brand-dot">.</span>
          <button
            className="mobile-close icon-button"
            aria-label="Close navigation"
            onClick={() => setMenu(false)}
          >
            <X size={20} />
          </button>
        </div>
        <div className="workspace-label">
          <span className="workspace-avatar">P</span>
          <div>
            <strong>My pharmacy</strong>
            <small>Inventory workspace</small>
          </div>
          <span className="status-dot" />
        </div>
        <p className="nav-label">WORKSPACE</p>
        <nav>
          {navigation.map((n) => (
            <NavLink to={n.path} key={n.path}>
              <n.icon size={19} />
              {n.label}
            </NavLink>
          ))}
          {user.role === 'OWNER' && (
            <NavLink to="/team">
              <Users size={19} />
              Team
            </NavLink>
          )}
        </nav>
        <div className="sidebar-bottom">
          <div className="help-card">
            <CircleHelp size={19} />
            <strong>A little clarity, every day.</strong>
            <p>Manage your pharmacy with confidence, one batch at a time.</p>
          </div>
          <div className="profile">
            <span className="avatar">{user.name.slice(0, 2).toUpperCase()}</span>
            <div>
              <strong>{user.name}</strong>
              <small>{user.role === 'OWNER' ? 'Owner' : 'Cashier'}</small>
            </div>
            <button
              className="icon-button"
              aria-label="Sign out"
              onClick={async () => {
                try {
                  await api('/auth/logout', 'POST');
                  client.clear();
                  onLogout();
                } catch (e) {
                  setError(e);
                }
              }}
            >
              <LogOut size={18} />
            </button>
          </div>
        </div>
      </aside>
      <div className="main-area">
        <header className="topbar">
          <button
            className="mobile-menu icon-button"
            aria-label="Open navigation"
            onClick={() => setMenu(true)}
          >
            <Menu size={22} />
          </button>
          <span>
            Workspace <span className="breadcrumb-slash">/</span>{' '}
            <strong>
              {navigation.find((n) => location.pathname.startsWith(n.path))?.label || 'Team'}
            </strong>
          </span>
          <div>
            <span className="today-date">
              {new Date().toLocaleDateString('en-IN', {
                weekday: 'short',
                day: 'numeric',
                month: 'short',
                year: 'numeric',
              })}
            </span>
            <span className="topbar-separator" />
            <span className="avatar small">{user.name.slice(0, 2).toUpperCase()}</span>
          </div>
        </header>
        <main className="content">
          <ErrorBox error={error} />
          <Routes>
            <Route path="/dashboard" element={<Dashboard user={user} />} />
            <Route
              path="/medicines"
              element={<Masters key="medicines" type="medicines" user={user} />}
            />
            <Route
              path="/suppliers"
              element={<Masters key="suppliers" type="suppliers" user={user} />}
            />
            <Route path="/inventory" element={<Inventory />} />
            <Route path="/sales" element={<History key="sales" type="sales" user={user} />} />
            <Route
              path="/sales/new"
              element={<TransactionForm key="sale-new" type="sales" user={user} />}
            />
            <Route
              path="/purchases"
              element={<History key="purchases" type="purchases" user={user} />}
            />
            <Route
              path="/purchases/new"
              element={
                user.role === 'OWNER' ? (
                  <TransactionForm key="purchase-new" type="purchases" user={user} />
                ) : (
                  <Navigate to="/purchases" replace />
                )
              }
            />
            <Route path="/reports" element={<Reports />} />
            <Route
              path="/team"
              element={user.role === 'OWNER' ? <Team /> : <Navigate to="/dashboard" replace />}
            />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
          <footer className="page-footer">
            <span>Pharmacy workspace</span>
            <span>Organized stock. Better care.</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
export default function App() {
  const [user, setUser] = useState<User>(),
    [loading, setLoading] = useState(!!sessionStorage.getItem('pharmacy-token'));
  const client = useQueryClient();
  useEffect(() => {
    let active = true;
    const expire = () => {
      setUser(undefined);
      client.clear();
    };
    window.addEventListener('auth-expired', expire);
    if (sessionStorage.getItem('pharmacy-token'))
      api<User>('/auth/me')
        .then((u) => {
          if (active) setUser(u);
        })
        .catch(() => sessionStorage.removeItem('pharmacy-token'))
        .finally(() => {
          if (active) setLoading(false);
        });
    return () => {
      active = false;
      window.removeEventListener('auth-expired', expire);
    };
  }, [client]);
  if (loading) return <Loading />;
  return (
    <BrowserRouter>
      {user ? (
        <Workspace
          user={user}
          onLogout={() => {
            sessionStorage.removeItem('pharmacy-token');
            setUser(undefined);
          }}
        />
      ) : (
        <Routes>
          <Route path="/login" element={<Login onLogin={setUser} />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      )}
    </BrowserRouter>
  );
}
