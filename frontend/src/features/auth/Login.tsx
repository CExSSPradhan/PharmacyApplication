import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ArrowRight, ShieldCheck, Pill } from 'lucide-react';
import { api, type User } from '../../api/client';
import { ErrorBox } from '../../components/ui';
const schema = z.object({ email: z.email(), password: z.string().min(1, 'Enter your password') });
export default function Login({ onLogin }: { onLogin: (u: User) => void }) {
  const [error, setError] = useState<unknown>();
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });
  return (
    <main className="login">
      <section className="login-story">
        <div className="brand">
          <span className="brand-icon">
            <Pill size={25} />
          </span>
          pharmacy<span className="brand-dot">.</span>
        </div>
        <div>
          <span className="story-label">YOUR EVERYDAY PHARMACY WORKSPACE</span>
          <h1>
            A healthier way
            <br />
            to manage
            <br />
            <em>your inventory.</em>
          </h1>
          <p>
            From the first delivery to the last bill.
            <br />
            Keep every medicine, batch, and sale in order.
          </p>
        </div>
        <div className="story-footer">
          <ShieldCheck size={20} /> Accurate stock. A clear audit trail.
        </div>
      </section>
      <section className="login-form">
        <div className="login-card">
          <span className="eyebrow">LET’S GET TO WORK</span>
          <h2>Welcome back</h2>
          <p className="subtitle">Sign in to your pharmacy workspace.</p>
          <form
            onSubmit={handleSubmit(async (values) => {
              setError(null);
              try {
                const result = await api('/auth/login', 'POST', values);
                sessionStorage.setItem('pharmacy-token', result.token);
                onLogin(result.user);
              } catch (e) {
                setError(e);
              }
            })}
          >
            <label>
              Email address
              <input autoComplete="username" type="email" {...register('email')} />
              {errors.email && <small className="field-error">Enter a valid email.</small>}
            </label>
            <label>
              Password
              <input
                type="password"
                autoComplete="current-password"
                placeholder="Enter your password"
                {...register('password')}
              />
              {errors.password && <small className="field-error">{errors.password.message}</small>}
            </label>
            <ErrorBox error={error} />
            <button className="primary full" disabled={isSubmitting}>
              {isSubmitting ? 'Signing in…' : 'Sign in'}
              <ArrowRight size={18} />
            </button>
          </form>
          <p className="login-note">
            Local setup credentials are in the project’s <code>.env</code> file.
          </p>
        </div>
      </section>
    </main>
  );
}
