import { useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import {
  AlertCircle,
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Eye,
  KeyRound,
  LogOut,
  Mail,
  Menu,
  Search,
  ShieldCheck,
  Sparkles,
  UsersRound,
  X,
  XCircle,
} from 'lucide-react';
import {
  getGetAdminSessionQueryKey,
  getGetAdminSummaryQueryKey,
  getListAdminUsersQueryKey,
  useAdminLogin,
  useAdminLogout,
  useApproveAdminUser,
  useDeclineAdminUser,
  useGetAdminSession,
  useGetAdminSummary,
  useListAdminUsers,
} from '@workspace/api-client-react';
import { ErrorBoundary } from '@/components/error-boundary';
import { Toaster } from '@/components/ui/toaster';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Router as WouterRouter, Route, Switch, useLocation } from 'wouter';

const queryClient = new QueryClient();
const filters = ['ALL', 'PENDING', 'APPROVED', 'DECLINED'] as const;
type Filter = (typeof filters)[number];
type Plan = 'ONE_DAY' | 'TWO_DAYS' | 'THREE_DAYS' | 'LIFETIME';

const planLabels: Record<Plan, string> = {
  ONE_DAY: '1 day',
  TWO_DAYS: '2 days',
  THREE_DAYS: '3 days',
  LIFETIME: 'Lifetime',
};

function formatDate(value: string | null, includeTime = false) {
  if (!value) return 'No expiry';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    ...(includeTime ? { hour: 'numeric', minute: '2-digit' } : {}),
  }).format(date);
}

function initials(email: string) {
  return email.slice(0, 2).toUpperCase();
}

function StatusPill({ status, userId }: { status: string; userId: string }) {
  const style = {
    PENDING: 'border-amber-300 bg-amber-50 text-amber-800',
    APPROVED: 'border-emerald-300 bg-emerald-50 text-emerald-800',
    DECLINED: 'border-rose-300 bg-rose-50 text-rose-800',
  }[status] ?? 'border-stone-200 bg-stone-50 text-stone-700';
  return (
    <span data-testid={`status-user-${status.toLowerCase()}-${userId}`} className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-extrabold tracking-[.12em] ${style}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {status}
    </span>
  );
}

function LoadingBars() {
  return (
    <div className="space-y-4 animate-pulse" aria-label="Loading">
      <div className="h-24 rounded-2xl bg-stone-200/70" />
      <div className="h-64 rounded-2xl bg-stone-200/70" />
    </div>
  );
}

function LoginPage({ onLoggedIn }: { onLoggedIn: () => void }) {
  const login = useAdminLogin();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [localError, setLocalError] = useState('');

  function submit(event: FormEvent) {
    event.preventDefault();
    setLocalError('');
    if (!email.trim() || password.length < 6) {
      setLocalError('Enter an email and a password with at least 6 characters.');
      return;
    }
    login.mutate({ data: { email: email.trim(), password } }, {
      onSuccess: () => onLoggedIn(),
      onError: () => setLocalError('Those details did not match an administrator account.'),
    });
  }

  return (
    <main className="min-h-[100dvh] bg-[#f3efe6] text-[#25343b]">
      <div className="grid min-h-[100dvh] lg:grid-cols-[minmax(420px,0.92fr)_1.08fr]">
        <section className="relative hidden overflow-hidden bg-[#203942] px-12 py-12 text-[#f7f3e9] lg:flex lg:flex-col lg:justify-between xl:px-20">
          <div className="absolute -right-28 -top-28 h-80 w-80 rounded-full border border-[#8acdb3]/20" />
          <div className="absolute -right-16 -top-16 h-56 w-56 rounded-full border border-[#8acdb3]/20" />
          <div className="absolute bottom-[-110px] left-[-90px] h-72 w-72 rounded-full bg-[#e9bc73]/10 blur-3xl" />
          <BrandMark light />
          <div className="relative max-w-md">
            <p className="mb-5 font-mono-ui text-[11px] tracking-[.24em] text-[#8acdb3]">PRIVATE OPERATIONS CONSOLE</p>
            <h1 className="text-6xl font-extrabold leading-[.98] tracking-[-.055em] xl:text-7xl">
              Keep the<br /><span className="text-[#8acdb3]">door</span> clear.
            </h1>
            <p className="mt-7 max-w-sm text-[15px] leading-7 text-[#cad5d1]">
              A quiet place to review access, approve subscriptions, and keep Altuuuuu moving in the right direction.
            </p>
          </div>
          <div className="flex items-center justify-between border-t border-white/10 pt-5 text-[11px] text-[#94aaa8]">
            <span>ALTUUUUU / ADMIN</span>
            <span className="font-mono-ui">v1.0.4</span>
          </div>
        </section>
        <section className="flex items-center justify-center px-5 py-10 sm:px-10">
          <div className="w-full max-w-[430px]">
            <div className="mb-10 lg:hidden"><BrandMark /></div>
            <div className="mb-8">
              <div className="mb-5 inline-flex h-11 w-11 items-center justify-center rounded-2xl bg-[#dcefe5] text-[#24755c]">
                <ShieldCheck size={22} strokeWidth={1.8} />
              </div>
              <p className="mb-2 font-mono-ui text-[11px] tracking-[.2em] text-[#6b817c]">ADMIN ACCESS</p>
              <h2 className="text-3xl font-extrabold tracking-[-.04em] text-[#25343b]">Welcome back.</h2>
              <p className="mt-2 text-sm leading-6 text-[#6c7776]">Sign in to the Altuuuuu operations room.</p>
            </div>
            <form onSubmit={submit} className="space-y-5" noValidate>
              <label className="block">
                <span className="mb-2 block text-xs font-extrabold uppercase tracking-[.14em] text-[#536361]">Email address</span>
                <span className="relative block">
                  <Mail className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[#81908b]" size={17} />
                  <input data-testid="input-email" value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="email" placeholder="you@altuuuuu.com" className="h-14 w-full rounded-xl border border-[#d8d7cc] bg-[#faf8f3] pl-11 pr-4 text-sm text-[#25343b] shadow-sm placeholder:text-[#a4aaa5] focus:border-[#379875] focus:outline-none focus:ring-4 focus:ring-[#8acdb3]/20" />
                </span>
              </label>
              <label className="block">
                <span className="mb-2 block text-xs font-extrabold uppercase tracking-[.14em] text-[#536361]">Password</span>
                <span className="relative block">
                  <KeyRound className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[#81908b]" size={17} />
                  <input data-testid="input-password" value={password} onChange={(event) => setPassword(event.target.value)} type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="At least 6 characters" className="h-14 w-full rounded-xl border border-[#d8d7cc] bg-[#faf8f3] pl-11 pr-14 text-sm text-[#25343b] shadow-sm placeholder:text-[#a4aaa5] focus:border-[#379875] focus:outline-none focus:ring-4 focus:ring-[#8acdb3]/20" />
                  <button data-testid="button-toggle-password" type="button" onClick={() => setShowPassword((value) => !value)} className="absolute right-3 top-1/2 -translate-y-1/2 rounded-lg p-2 text-[#71817d] hover:bg-[#e9e7dc] hover:text-[#25343b]" aria-label={showPassword ? 'Hide password' : 'Show password'}>
                    <Eye size={17} />
                  </button>
                </span>
              </label>
              {(localError || login.isError) && (
                <div data-testid="status-login-error" role="alert" className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs leading-5 text-rose-800">
                  <AlertCircle size={16} className="mt-0.5 shrink-0" /> <span>{localError || 'Unable to sign in right now. Try again.'}</span>
                </div>
              )}
              <button data-testid="button-submit-login" disabled={login.isPending} className="group flex h-14 w-full items-center justify-center gap-3 rounded-xl bg-[#23775d] text-sm font-extrabold text-[#f7f3e9] shadow-[0_8px_16px_rgba(35,119,93,.18)] hover:-translate-y-0.5 hover:bg-[#1c644e] disabled:cursor-wait disabled:opacity-60">
                {login.isPending ? 'Checking access…' : 'Enter operations room'}
                {!login.isPending && <ArrowRight size={17} className="transition-transform group-hover:translate-x-1" />}
              </button>
            </form>
            <p className="mt-8 text-center text-[11px] leading-5 text-[#87918e]">Restricted to Altuuuuu administrators.<br />Your session is secured with an HttpOnly cookie.</p>
          </div>
        </section>
      </div>
    </main>
  );
}

function BrandMark({ light = false }: { light?: boolean }) {
  return (
    <div data-testid="brand-mark" className={`flex items-center gap-3 ${light ? 'text-[#f7f3e9]' : 'text-[#25343b]'}`}>
      <div className="relative flex h-10 w-10 items-center justify-center rounded-[13px] bg-[#8acdb3] text-[#203942]">
        <span className="absolute h-4 w-4 rounded-full border-[3px] border-current" />
        <span className="absolute h-1.5 w-1.5 translate-x-[5px] rounded-full bg-current" />
      </div>
      <div>
        <div className="text-lg font-extrabold tracking-[-.06em]">altuuuuu</div>
        <div className={`font-mono-ui text-[9px] tracking-[.2em] ${light ? 'text-[#91aaa7]' : 'text-[#83908c]'}`}>CONTROL ROOM</div>
      </div>
    </div>
  );
}

function Sidebar({ adminEmail, onLogout }: { adminEmail: string; onLogout: () => void }) {
  return (
    <aside className="hidden w-[252px] shrink-0 flex-col justify-between bg-[#203942] px-5 py-6 text-[#f7f3e9] md:flex">
      <div>
        <BrandMark light />
        <div className="mt-14">
          <p className="mb-3 px-3 font-mono-ui text-[10px] tracking-[.2em] text-[#7f9d9a]">WORKSPACE</p>
          <div data-testid="nav-approval-workspace" className="flex items-center gap-3 rounded-xl bg-[#36545a] px-3 py-3 text-sm font-bold text-[#f7f3e9]">
            <CheckCircle2 size={17} className="text-[#8acdb3]" /> Approval workspace
          </div>
        </div>
        <div className="mt-10 rounded-2xl border border-[#466269] bg-[#29474f] p-4">
          <div className="mb-3 flex items-center justify-between">
            <span className="font-mono-ui text-[10px] tracking-[.15em] text-[#8da6a3]">SYSTEM STATUS</span>
            <span className="relative flex h-2.5 w-2.5"><span className="absolute inline-flex h-full w-full animate-soft-pulse rounded-full bg-[#8acdb3]" /><span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-[#8acdb3]" /></span>
          </div>
          <p className="text-sm font-bold">Everything is calm.</p>
          <p className="mt-1 text-xs leading-5 text-[#a9bcba]">Requests are syncing normally.</p>
        </div>
      </div>
      <div className="border-t border-[#3c5960] pt-5">
        <div className="mb-4 flex items-center gap-3 px-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#8acdb3] text-xs font-extrabold text-[#203942]">{initials(adminEmail)}</div>
          <div className="min-w-0"><p className="truncate text-xs font-bold">{adminEmail}</p><p className="mt-0.5 font-mono-ui text-[9px] tracking-[.1em] text-[#8da6a3]">ADMINISTRATOR</p></div>
        </div>
        <button data-testid="button-logout" onClick={onLogout} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-bold text-[#adc0bc] hover:bg-[#29474f] hover:text-[#f7f3e9]"><LogOut size={16} /> Sign out</button>
      </div>
    </aside>
  );
}

function MobileHeader({ onLogout, onMenu }: { onLogout: () => void; onMenu: () => void }) {
  return (
    <header className="flex items-center justify-between border-b border-[#dcd9cf] bg-[#f7f3e9] px-5 py-4 md:hidden">
      <BrandMark />
      <div className="flex items-center gap-1">
        <button data-testid="button-mobile-menu" onClick={onMenu} className="rounded-lg p-2 text-[#536361] hover:bg-[#e6e3d9]" aria-label="Open menu"><Menu size={20} /></button>
        <button data-testid="button-mobile-logout" onClick={onLogout} className="rounded-lg p-2 text-[#536361] hover:bg-[#e6e3d9]" aria-label="Sign out"><LogOut size={18} /></button>
      </div>
    </header>
  );
}

function SummaryCard({ label, value, tone, icon }: { label: string; value: number; tone: string; icon: ReactNode }) {
  return (
    <div data-testid={`summary-${label.toLowerCase()}`} className="relative overflow-hidden rounded-2xl border border-[#dedbd1] bg-[#faf8f3] p-5 shadow-[0_5px_16px_rgba(32,57,66,.035)]">
      <div className={`absolute right-0 top-0 h-20 w-20 -translate-y-7 translate-x-7 rounded-full ${tone} opacity-20`} />
      <div className="relative flex items-start justify-between"><p className="font-mono-ui text-[10px] font-medium tracking-[.17em] text-[#76827f]">{label}</p><span className="text-[#4b6d6b]">{icon}</span></div>
      <p className="relative mt-5 text-3xl font-extrabold tracking-[-.06em] text-[#25343b]">{value}</p>
    </div>
  );
}

function ApprovalModal({ user, onClose, onApprove, pending }: { user: any; onClose: () => void; onApprove: (plan: Plan) => void; pending: boolean }) {
  const [plan, setPlan] = useState<Plan>('THREE_DAYS');
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-[#203942]/45 p-0 backdrop-blur-sm sm:items-center sm:p-5" role="dialog" aria-modal="true" aria-labelledby="approval-title">
      <div className="w-full max-w-[470px] rounded-t-[26px] border border-[#dfdcd2] bg-[#faf8f3] p-6 shadow-2xl sm:rounded-[26px] sm:p-7 animate-rise-in">
        <div className="mb-6 flex items-start justify-between">
          <div><p className="mb-2 font-mono-ui text-[10px] tracking-[.18em] text-[#75827e]">SUBSCRIPTION APPROVAL</p><h2 id="approval-title" className="text-2xl font-extrabold tracking-[-.045em] text-[#25343b]">Choose access window</h2></div>
          <button data-testid="button-close-approval" onClick={onClose} className="rounded-xl p-2 text-[#7a8783] hover:bg-[#e9e6dd] hover:text-[#25343b]" aria-label="Close approval dialog"><X size={19} /></button>
        </div>
        <div className="mb-6 flex items-center gap-3 rounded-2xl border border-[#dfe0d6] bg-[#f3f1e9] p-3.5">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#d8ede2] text-xs font-extrabold text-[#26775d]">{initials(user.email)}</div>
          <div className="min-w-0"><p className="truncate text-sm font-extrabold text-[#25343b]">{user.email}</p><p className="mt-0.5 font-mono-ui text-[10px] text-[#7d8985]">Requested {formatDate(user.createdAt)}</p></div>
        </div>
        <label className="block"><span className="mb-2 block text-xs font-extrabold uppercase tracking-[.14em] text-[#536361]">Subscription plan</span>
          <span className="relative block"><select data-testid="select-subscription-plan" value={plan} onChange={(event) => setPlan(event.target.value as Plan)} className="h-14 w-full appearance-none rounded-xl border border-[#d5d6cb] bg-[#faf8f3] px-4 text-sm font-bold text-[#25343b] outline-none focus:border-[#379875] focus:ring-4 focus:ring-[#8acdb3]/20">{Object.entries(planLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><ChevronDown className="pointer-events-none absolute right-4 top-1/2 -translate-y-1/2 text-[#788782]" size={17} /></span>
        </label>
        <div className="mt-6 grid grid-cols-2 gap-3">
          <button data-testid="button-cancel-approval" onClick={onClose} className="h-12 rounded-xl border border-[#d5d6cb] text-sm font-extrabold text-[#536361] hover:bg-[#ece9df]">Not yet</button>
          <button data-testid="button-confirm-approval" disabled={pending} onClick={() => onApprove(plan)} className="h-12 rounded-xl bg-[#23775d] text-sm font-extrabold text-[#f7f3e9] hover:bg-[#1c644e] disabled:opacity-60">{pending ? 'Approving…' : `Approve for ${planLabels[plan]}`}</button>
        </div>
      </div>
    </div>
  );
}

function Workspace({ adminEmail, onLogout }: { adminEmail: string; onLogout: () => void }) {
  const client = useQueryClient();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [search, setSearch] = useState('');
  const [selectedUser, setSelectedUser] = useState<any>(null);
  const [mobileMenu, setMobileMenu] = useState(false);
  const summary = useGetAdminSummary({ query: { queryKey: getGetAdminSummaryQueryKey() } });
  const users = useListAdminUsers({ status: filter }, { query: { queryKey: getListAdminUsersQueryKey({ status: filter }) } });
  const approve = useApproveAdminUser();
  const decline = useDeclineAdminUser();

  const visibleUsers = useMemo(() => (users.data ?? []).filter((user) => user.email.toLowerCase().includes(search.toLowerCase().trim())), [users.data, search]);

  function refresh() {
    client.invalidateQueries({ queryKey: getGetAdminSummaryQueryKey() });
    client.invalidateQueries({ queryKey: getListAdminUsersQueryKey({ status: filter }) });
    client.invalidateQueries({ queryKey: getListAdminUsersQueryKey() });
  }
  function approveUser(plan: Plan) {
    if (!selectedUser) return;
    approve.mutate({ userId: selectedUser.id, data: { plan } }, { onSuccess: () => { setSelectedUser(null); refresh(); } });
  }
  function declineUser(userId: string) {
    if (!window.confirm('Decline this access request?')) return;
    decline.mutate({ userId }, { onSuccess: refresh });
  }
  const summaryData = summary.data ?? { total: 0, pending: 0, approved: 0, declined: 0 };

  return (
    <div className="flex min-h-[100dvh] bg-[#f3efe6] text-[#25343b]">
      <Sidebar adminEmail={adminEmail} onLogout={onLogout} />
      <div className="min-w-0 flex-1">
        <MobileHeader onLogout={onLogout} onMenu={() => setMobileMenu((value) => !value)} />
        {mobileMenu && <div className="border-b border-[#dcd9cf] bg-[#203942] px-5 py-4 text-sm font-bold text-[#f7f3e9] md:hidden"><div className="flex items-center gap-3 rounded-xl bg-[#36545a] px-3 py-3"><CheckCircle2 size={17} className="text-[#8acdb3]" /> Approval workspace</div></div>}
        <main className="mx-auto max-w-[1400px] px-5 py-7 sm:px-8 sm:py-10 lg:px-12">
          <header className="mb-8 flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
            <div><p className="mb-2 font-mono-ui text-[10px] tracking-[.22em] text-[#71807c]">THURSDAY, 24 OCTOBER 2024 / 09:42 UTC</p><h1 className="text-3xl font-extrabold tracking-[-.055em] sm:text-4xl">Good morning, <span className="text-[#287c61]">team.</span></h1><p className="mt-2 text-sm text-[#6f7c79]">Review access requests and keep the community moving.</p></div>
            <div className="hidden items-center gap-2 rounded-full border border-[#d8d9cf] bg-[#faf8f3] px-3 py-2 text-xs font-bold text-[#65736f] sm:flex"><span className="h-2 w-2 rounded-full bg-[#4ca77f]" /> Live workspace</div>
          </header>
          {summary.isLoading ? <LoadingBars /> : summary.isError ? <ErrorMessage onRetry={() => summary.refetch()} /> : (
            <>
              <section className="mb-8 grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
                <SummaryCard label="Total requests" value={summaryData.total} tone="bg-[#8acdb3]" icon={<UsersRound size={17} />} />
                <SummaryCard label="Awaiting review" value={summaryData.pending} tone="bg-[#e9bc73]" icon={<Clock3 size={17} />} />
                <SummaryCard label="Approved" value={summaryData.approved} tone="bg-[#79bc9e]" icon={<CheckCircle2 size={17} />} />
                <SummaryCard label="Declined" value={summaryData.declined} tone="bg-[#de8d83]" icon={<XCircle size={17} />} />
              </section>
              <section className="overflow-hidden rounded-[22px] border border-[#dedbd1] bg-[#faf8f3] shadow-[0_5px_16px_rgba(32,57,66,.035)]">
                <div className="border-b border-[#e4e1d8] px-5 pb-5 pt-5 sm:px-6">
                  <div className="flex flex-col justify-between gap-5 lg:flex-row lg:items-center">
                    <div><div className="flex items-center gap-3"><h2 className="text-lg font-extrabold tracking-[-.035em]">Access requests</h2><span data-testid="text-request-count" className="rounded-full bg-[#e7eee9] px-2.5 py-1 font-mono-ui text-[10px] text-[#36725c]">{visibleUsers.length} shown</span></div><p className="mt-1 text-xs text-[#7c8783]">Every account gets a considered yes or no.</p></div>
                    <div className="relative w-full sm:w-[245px]"><Search className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#88938f]" size={16} /><input data-testid="input-search-users" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search email" className="h-10 w-full rounded-xl border border-[#dadbd1] bg-[#f5f3ed] pl-10 pr-3 text-xs text-[#25343b] outline-none focus:border-[#379875] focus:ring-4 focus:ring-[#8acdb3]/20" /></div>
                  </div>
                  <div className="mt-5 flex gap-1 overflow-x-auto pb-0.5">{filters.map((item) => <button data-testid={`button-filter-${item.toLowerCase()}`} key={item} onClick={() => setFilter(item)} className={`whitespace-nowrap rounded-lg px-3 py-2 font-mono-ui text-[10px] tracking-[.08em] ${filter === item ? 'bg-[#203942] text-[#f7f3e9]' : 'text-[#7a8682] hover:bg-[#edeae1] hover:text-[#25343b]'}`}>{item === 'ALL' ? 'All requests' : item[0] + item.slice(1).toLowerCase()}</button>)}</div>
                </div>
                {users.isLoading ? <div className="p-6"><div className="h-40 animate-pulse rounded-xl bg-[#eeece4]" /></div> : users.isError ? <ErrorMessage onRetry={() => users.refetch()} /> : visibleUsers.length === 0 ? <EmptyState search={Boolean(search)} /> : (
                  <div>
                    <div className="hidden grid-cols-[minmax(220px,1.4fr)_130px_150px_140px_120px] gap-4 px-6 py-3 font-mono-ui text-[9px] tracking-[.16em] text-[#89938f] lg:grid"><span>ACCOUNT</span><span>STATUS</span><span>REQUESTED</span><span>SUBSCRIPTION</span><span className="text-right">ACTION</span></div>
                    <div className="divide-y divide-[#e7e4dc]">{visibleUsers.map((user) => <UserRow key={user.id} user={user} onApprove={() => setSelectedUser(user)} onDecline={() => declineUser(user.id)} busy={approve.isPending || decline.isPending} />)}</div>
                  </div>
                )}
              </section>
              <div className="mt-5 flex items-center justify-between px-1 font-mono-ui text-[10px] tracking-[.08em] text-[#8b9590]"><span>ALTUUUUU / ACCESS CONTROL</span><span>DATA REFRESHES ON ACTION</span></div>
            </>
          )}
        </main>
      </div>
      {selectedUser && <ApprovalModal user={selectedUser} onClose={() => setSelectedUser(null)} onApprove={approveUser} pending={approve.isPending} />}
    </div>
  );
}

function UserRow({ user, onApprove, onDecline, busy }: { user: any; onApprove: () => void; onDecline: () => void; busy: boolean }) {
  const isPending = user.approvalStatus === 'PENDING';
  return (
    <div data-testid={`row-user-${user.id}`} className="grid gap-4 px-5 py-4 transition-colors hover:bg-[#f5f3ed] sm:px-6 lg:grid-cols-[minmax(220px,1.4fr)_130px_150px_140px_120px] lg:items-center lg:gap-4">
      <div className="flex min-w-0 items-center gap-3"><div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-xs font-extrabold ${isPending ? 'bg-[#f7e4bf] text-[#8a6326]' : 'bg-[#dcebe5] text-[#28775d]'}`}>{initials(user.email)}</div><div className="min-w-0"><p data-testid={`text-email-${user.id}`} className="truncate text-sm font-extrabold text-[#304148]">{user.email}</p><p className="mt-1 flex items-center gap-1.5 font-mono-ui text-[9px] tracking-[.04em] text-[#8a9591]"><span className="hidden lg:inline">ID</span> {user.id.slice(0, 12)}…</p></div></div>
      <div><span className="mb-1 block font-mono-ui text-[9px] tracking-[.15em] text-[#9aa39f] lg:hidden">STATUS</span><StatusPill status={user.approvalStatus} userId={user.id} /></div>
      <div><span className="mb-1 block font-mono-ui text-[9px] tracking-[.15em] text-[#9aa39f] lg:hidden">REQUESTED</span><p className="text-xs font-semibold text-[#5e6d69]">{formatDate(user.createdAt, true)}</p></div>
      <div><span className="mb-1 block font-mono-ui text-[9px] tracking-[.15em] text-[#9aa39f] lg:hidden">PLAN</span><p className="text-xs font-semibold text-[#5e6d69]">{user.subscriptionPlan === 'NONE' ? '—' : planLabels[user.subscriptionPlan as Plan]}</p>{user.subscriptionExpiresAt && <p className="mt-1 text-[10px] text-[#8a9591]">until {formatDate(user.subscriptionExpiresAt)}</p>}</div>
      <div className="flex items-center justify-end gap-2 border-t border-[#ebe8df] pt-3 lg:border-0 lg:pt-0">{isPending ? <><button data-testid={`button-approve-${user.id}`} disabled={busy} onClick={onApprove} className="flex h-9 flex-1 items-center justify-center gap-1.5 rounded-lg bg-[#23775d] px-3 text-[11px] font-extrabold text-[#f7f3e9] hover:bg-[#1c644e] disabled:opacity-50 sm:flex-none"><Check size={14} /> Approve</button><button data-testid={`button-decline-${user.id}`} disabled={busy} onClick={onDecline} className="flex h-9 items-center justify-center rounded-lg border border-[#e0d7d1] px-3 text-[11px] font-extrabold text-[#a5584d] hover:border-[#d69a91] hover:bg-[#fff3f0] disabled:opacity-50" aria-label={`Decline ${user.email}`}><X size={15} /></button></> : <span className="font-mono-ui text-[10px] text-[#9aa39f]">REVIEWED</span>}</div>
    </div>
  );
}

function EmptyState({ search }: { search: boolean }) {
  return <div data-testid="empty-users" className="flex flex-col items-center justify-center px-6 py-20 text-center"><div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-[#e7eee9] text-[#4d8d74]"><Sparkles size={23} /></div><h3 className="text-base font-extrabold">{search ? 'No matching requests' : 'Nothing needs your attention'}</h3><p className="mt-2 max-w-xs text-sm leading-6 text-[#7b8783]">{search ? 'Try a different email address or clear the search.' : 'This view is clear for now. New requests will appear here.'}</p></div>;
}

function ErrorMessage({ onRetry }: { onRetry: () => void }) {
  return <div data-testid="status-data-error" className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-center"><AlertCircle className="mx-auto mb-3 text-rose-600" size={22} /><p className="text-sm font-extrabold text-rose-900">Could not load the workspace</p><p className="mt-1 text-xs text-rose-700">The service may be taking a moment.</p><button data-testid="button-retry" onClick={onRetry} className="mt-4 rounded-lg bg-[#203942] px-4 py-2 text-xs font-extrabold text-[#f7f3e9] hover:bg-[#2b4c55]">Try again</button></div>;
}

function Home() {
  const client = useQueryClient();
  const session = useGetAdminSession({ query: { queryKey: getGetAdminSessionQueryKey(), retry: false } });
  const logout = useAdminLogout();
  const [loggedIn, setLoggedIn] = useState(false);

  if (session.isLoading && !loggedIn) return <div className="min-h-[100dvh] bg-[#f3efe6] p-5 sm:p-10"><div className="mx-auto max-w-6xl"><div className="mb-12 h-10 w-40 animate-pulse rounded-lg bg-[#dedbd1]" /><LoadingBars /></div></div>;
  if (session.data?.admin || loggedIn) {
    const email = session.data?.admin.email ?? 'admin@altuuuuu.com';
    return <Workspace adminEmail={email} onLogout={() => logout.mutate(undefined, { onSuccess: () => { setLoggedIn(false); client.setQueryData(getGetAdminSessionQueryKey(), undefined); client.invalidateQueries({ queryKey: getGetAdminSessionQueryKey() }); } })} />;
  }
  return <LoginPage onLoggedIn={() => { setLoggedIn(true); client.invalidateQueries({ queryKey: getGetAdminSessionQueryKey() }); client.invalidateQueries({ queryKey: getGetAdminSummaryQueryKey() }); }} />;
}

function Router() {
  return <Switch><Route path="/" component={Home} /><Route component={() => <div className="flex min-h-[100dvh] items-center justify-center bg-[#f3efe6] text-[#25343b]"><div className="text-center"><h1 className="text-5xl font-extrabold">404</h1><p className="mt-2 text-sm text-[#6f7c79]">This room does not exist.</p></div></div>} /></Switch>;
}

function App() {
  return <QueryClientProvider client={queryClient}><TooltipProvider><WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, '')}><ErrorBoundary><Router /></ErrorBoundary></WouterRouter><Toaster /></TooltipProvider></QueryClientProvider>;
}

export default App;
