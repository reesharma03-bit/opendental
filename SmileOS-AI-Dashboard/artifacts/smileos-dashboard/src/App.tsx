import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, BarChart3,
  Bell, BookOpen, CalendarDays, Check, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight,
  CircleDollarSign, Clock3, CreditCard, LayoutDashboard, BellRing,
  Lightbulb, Menu, MessageSquareText, MoreHorizontal, Search, Settings, ShieldCheck,
  RefreshCw, Sparkles, Stethoscope, UserRoundPlus, Users, Wallet, X, Zap, KeyRound, LogOut, UserCog,
} from 'lucide-react';
import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import AppointmentsScreen from './AppointmentsScreen';
import ApiCatalogScreen from './ApiCatalogScreen';
import AllergiesScreen from './AllergiesScreen';
import AllergyDefinitionsScreen from './AllergyDefinitionsScreen';
import DiseaseDefinitionsScreen from './DiseaseDefinitionsScreen';
import PatientsScreen from './PatientsScreen';
import SubscriptionScreen from './SubscriptionScreen';
import DatabaseResourceScreen from './DatabaseResourceScreen';
import HomeDashboard from './HomeDashboard';
import AssistantPanel from './AssistantPanel';
import UsersScreen from './UsersScreen';
import { useAuth } from './auth/AuthContext';
import { ChangePasswordForm } from './auth/SignInScreens';
import { getCapabilities } from './lib/databaseResources';
import type { Permission } from './lib/backendAuth';
import { getSyncStatus, startForceSync, type SyncStatus } from './lib/backendSync';

const initialResource = (() => {
  const name = new URLSearchParams(window.location.search).get('resource');
  return name && apiNavigationGroups.some((g) => (g.resources as readonly string[]).includes(name)) ? name : null;
})();
import { apiNavigationGroups, formatApiResourceName } from './apiNavigation';

/** Each item shows only for roles with its permission (none: everyone signed in). */
const navItems: { label: string; icon: typeof Users; badge?: string; permission?: Permission }[] = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Patients', icon: Users, permission: 'PATIENTS_READ' },
  { label: 'Appointments', icon: CalendarDays, permission: 'APPOINTMENTS_READ' },
  { label: 'Subscription', icon: BellRing, permission: 'SYNC_MANAGE' },
  { label: 'API Catalog', icon: BookOpen },
  { label: 'Users', icon: UserCog, permission: 'USERS_MANAGE' },
];

const initialsOf = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('') || '?';

/** Catalog entries the signed-in user may open (from the backend's view of their role). */
function useVisibleResources(): Set<string> | null {
  const { user } = useAuth();
  const [visible, setVisible] = useState<Set<string> | null>(null);
  useEffect(() => {
    let live = true;
    getCapabilities()
      .then((caps) => { if (live) setVisible(new Set(caps.keys())); })
      .catch(() => { if (live) setVisible(new Set()); });
    return () => { live = false; };
  }, [user?.id, user?.role]);
  return visible;
}

function syncSummary(status: SyncStatus | null): string {
  if (!status || status.state === 'idle') return 'Copies all Open Dental data into the database.';
  if (status.state === 'running') {
    const progress = status.total ? ` (${status.done ?? 0}/${status.total})` : '';
    return `Syncing${progress}${status.currentStep ? ` · ${status.currentStep}` : ''}`;
  }
  if (status.state === 'failed') return `Sync failed: ${status.error ?? 'unknown error'}`;
  const records = (status.records ?? 0).toLocaleString();
  const failures = status.failures ?? 0;
  return failures
    ? `Done · ${records} records · ${failures} resource${failures === 1 ? '' : 's'} failed`
    : `Done · ${records} records`;
}

function ForceSyncButton() {
  const [status, setStatus] = useState<SyncStatus | null>(null);
  const [error, setError] = useState('');
  const running = status?.state === 'running';

  // Picks up a sync that is already running (nightly run, another tab).
  useEffect(() => {
    getSyncStatus().then(setStatus).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!running) return undefined;
    const timer = window.setInterval(() => {
      getSyncStatus().then(setStatus).catch((e: Error) => setError(e.message));
    }, 3000);
    return () => window.clearInterval(timer);
  }, [running]);

  const start = async () => {
    setError('');
    try {
      setStatus(await startForceSync());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const failed = status?.results?.filter((r) => r.status === 'failed') ?? [];
  const summary = error || syncSummary(status);

  return (
    <div className="mb-2">
      <button
        type="button"
        onClick={start}
        disabled={running}
        title={failed.length ? failed.map((r) => `${r.resource}: ${r.error ?? 'failed'}`).join('\n') : summary}
        data-testid="button-force-refresh"
        aria-busy={running}
        className="flex min-h-9 w-full items-center gap-2 rounded-lg px-3 text-left text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-slate-900 disabled:cursor-wait disabled:text-blue-700"
      >
        <RefreshCw size={14} className={`shrink-0 ${running ? 'animate-spin' : ''}`} />
        <span>{running ? 'Syncing…' : 'Force Sync'}</span>
      </button>
      <p
        data-testid="text-force-sync-status"
        aria-live="polite"
        className={`px-3 text-[10px] leading-snug ${error || status?.state === 'failed' ? 'text-rose-600' : failed.length ? 'text-amber-600' : 'text-slate-400'}`}
      >
        {summary}
      </p>
    </div>
  );
}

function Sidebar({
  collapsed, onToggle, activeNav, setActiveNav, mobileOpen, onClose,
  selectedResource, onSelectResource,
}: {
  collapsed: boolean; onToggle: () => void; activeNav: string; setActiveNav: (name: string) => void;
  mobileOpen: boolean; onClose: () => void;
  selectedResource: string | null; onSelectResource: (name: string) => void;
}) {
  const [expandedGroups, setExpandedGroups] = useState<string[]>(() =>
    apiNavigationGroups.filter((group) => (group.resources as readonly string[]).includes(selectedResource ?? '')).map((group) => group.label));

  const toggleGroup = (label: string) => {
    setExpandedGroups((groups) => groups.includes(label)
      ? groups.filter((group) => group !== label)
      : [...groups, label]);
  };
  const { user, can } = useAuth();
  const visibleResources = useVisibleResources();
  const canOpen = (resource: string) => resource === 'ChartModules'
    ? can('CLINICAL_READ')
    : Boolean(visibleResources?.has(resource.toLowerCase()));

  return (
    <>
      {mobileOpen && <button aria-label="Close navigation" data-testid="button-close-mobile-nav" onClick={onClose} className="fixed inset-0 z-40 bg-slate-950/30 backdrop-blur-[2px] md:hidden" />}
      <aside className={`fixed inset-y-0 left-0 z-50 flex w-[258px] flex-col border-r border-slate-200/80 bg-white transition-all duration-300 md:translate-x-0 ${collapsed ? 'md:w-[82px]' : ''} ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}`} data-testid="sidebar-navigation">
        <div className={`flex h-[84px] items-center ${collapsed ? 'md:justify-center md:px-0' : 'justify-between px-6'} px-6`}>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-gradient-to-br from-blue-600 to-indigo-700 text-white shadow-lg shadow-blue-900/15">
              <div className="relative"><Activity size={20} strokeWidth={2.6} /><span className="absolute -right-1 -top-1 h-1.5 w-1.5 rounded-full bg-cyan-200" /></div>
            </div>
            <div className={`${collapsed ? 'md:hidden' : ''}`}>
              <div className="font-[Manrope] text-[17px] font-extrabold tracking-[-0.6px] text-slate-900">smile<span className="text-blue-600">OS</span></div>
              <div className="mt-[-2px] text-[10px] font-semibold uppercase tracking-[1.65px] text-slate-400">AI practice suite</div>
            </div>
          </div>
          <button data-testid="button-collapse-sidebar" onClick={onToggle} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} className={`hidden h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 md:flex ${collapsed ? 'md:hidden' : ''}`}>
            <ChevronLeft size={16} />
          </button>
          <button data-testid="button-close-sidebar" onClick={onClose} aria-label="Close navigation" className="ml-auto flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 md:hidden"><X size={18} /></button>
        </div>
        {collapsed && <button data-testid="button-expand-sidebar" onClick={onToggle} aria-label="Expand sidebar" className="hidden h-8 w-8 items-center justify-center self-center rounded-lg text-slate-400 hover:bg-slate-100 md:flex"><ChevronRight size={16} /></button>}
        <div className={`min-h-0 flex-1 overflow-y-auto px-4 pb-3 pt-3 ${collapsed ? 'md:px-3' : ''}`}>
          <p className={`mb-3 px-3 text-[10px] font-bold uppercase tracking-[1.5px] text-slate-400 ${collapsed ? 'md:hidden' : ''}`}>Workspace</p>
          <nav className="space-y-1" aria-label="Main navigation">
            {navItems.filter((item) => !item.permission || can(item.permission)).map(({ label, icon: Icon, badge }) => (
              <button key={label} onClick={() => { setActiveNav(label); onClose(); }} data-testid={`nav-${label.toLowerCase().replaceAll(' ', '-')}`} aria-current={activeNav === label ? 'page' : undefined} title={collapsed ? label : undefined}
                className={`group flex h-[44px] w-full items-center gap-3 rounded-xl px-3 text-left text-[13px] font-semibold transition ${activeNav === label ? 'bg-blue-50 text-blue-700 shadow-[inset_2px_0_0_#315fe7]' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800'} ${collapsed ? 'md:justify-center md:px-0' : ''}`}>
                <Icon size={18} strokeWidth={activeNav === label ? 2.25 : 1.8} className="shrink-0" />
                <span className={collapsed ? 'md:hidden' : ''}>{label}</span>
                {badge && <span className={`ml-auto rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500 ${collapsed ? 'md:hidden' : ''}`}>{badge}</span>}
              </button>
            ))}
          </nav>
          <div className={`mt-5 border-t border-slate-100 pt-4 ${collapsed ? 'md:hidden' : ''}`}>
            <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[1.5px] text-slate-400">Open Dental API</p>
            {can('SYNC_MANAGE') && <ForceSyncButton />}
            <nav aria-label="Open Dental API resources" className="space-y-1">
              {apiNavigationGroups.map(({ label, resources: allResources }) => {
                const resources = allResources.filter(canOpen);
                if (resources.length === 0) return null;
                const expanded = expandedGroups.includes(label);
                const groupId = `api-nav-group-${label.toLowerCase().replaceAll(/[^a-z0-9]+/g, '-')}`;
                return (
                  <section key={label}>
                    <button
                      type="button"
                      onClick={() => toggleGroup(label)}
                      aria-expanded={expanded}
                      aria-controls={groupId}
                      data-testid={`button-api-group-${groupId.replace('api-nav-group-', '')}`}
                      className="flex min-h-9 w-full items-center gap-2 rounded-lg px-3 text-left text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50 hover:text-slate-900"
                    >
                      <ChevronRight size={14} className={`shrink-0 text-slate-400 transition-transform ${expanded ? 'rotate-90' : ''}`} />
                      <span className="min-w-0 flex-1">{label}</span>
                      <span className="text-[9px] font-medium text-slate-400">{resources.length}</span>
                    </button>
                    {expanded && (
                      <ul id={groupId} className="mb-2 ml-[17px] border-l border-slate-200 pl-2">
                        {resources.map((resource) => (
                          <li key={resource}>
                            <button
                              type="button"
                              onClick={() => { onSelectResource(resource); onClose(); }}
                              aria-current={selectedResource === resource && activeNav === 'API Catalog' ? 'page' : undefined}
                              data-testid={`nav-api-resource-${resource}`}
                              title={resource}
                              className={`min-h-8 w-full truncate rounded-md px-2 text-left text-[10px] font-medium transition ${selectedResource === resource && activeNav === 'API Catalog' ? 'bg-blue-50 text-blue-700' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800'}`}
                            >
                              {formatApiResourceName(resource)}
                            </button>
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                );
              })}
            </nav>
          </div>
        </div>
        <div className="mt-auto px-4 pb-4">
          <div className={`mb-4 rounded-2xl border border-blue-100 bg-gradient-to-br from-blue-50 to-indigo-50 p-4 ${collapsed ? 'md:hidden' : ''}`}>
            <div className="flex items-center gap-2 text-blue-700"><ShieldCheck size={16} /><span className="text-[11px] font-bold">Practice health</span><span className="ml-auto h-2 w-2 rounded-full bg-emerald-500 ring-4 ring-emerald-100" /></div>
            <p className="mt-2 text-[11px] leading-[1.55] text-slate-600">Your team is on track. 3 items need attention today.</p>
            <button onClick={() => setActiveNav('Reports')} data-testid="button-practice-health" className="mt-2 flex items-center gap-1 text-[11px] font-bold text-blue-700 hover:text-blue-900">View practice report <ArrowRight size={12} /></button>
          </div>
          <div className={`flex items-center gap-3 rounded-xl px-2 py-3 ${collapsed ? 'md:justify-center md:px-0' : ''}`}>
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#e4edf9] text-xs font-bold text-[#395781]">{initialsOf(user?.fullName ?? '')}</div>
            <div className={`min-w-0 ${collapsed ? 'md:hidden' : ''}`}><p className="truncate text-[12px] font-bold text-slate-800" data-testid="text-signed-in-name">{user?.fullName}</p><p className="truncate text-[10px] text-slate-400">{user?.roleLabel}</p></div>
            <MoreHorizontal size={17} className={`ml-auto text-slate-400 ${collapsed ? 'md:hidden' : ''}`} />
          </div>
        </div>
      </aside>
    </>
  );
}

function App() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [activeNav, setActiveNav] = useState(initialResource ? 'API Catalog' : 'Dashboard');
  const [selectedApiResource, setSelectedApiResource] = useState<string | null>(initialResource);
  // Chart modules are views Open Dental computes per patient; they are not stored, so
  // the catalog shows their documentation instead of a data screen.
  const databaseResource = selectedApiResource && selectedApiResource !== 'ChartModules'
    ? selectedApiResource : null;
  const [search, setSearch] = useState('');
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [toast, setToast] = useState('');
  const [createPatientRequest, setCreatePatientRequest] = useState(0);
  const [createAppointmentRequest, setCreateAppointmentRequest] = useState(0);
  const [aiOpen, setAiOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const { user, can, signOut } = useAuth();
  useEffect(() => {
    const restoreResource = () => {
      const value = new URLSearchParams(window.location.search).get('resource');
      const resource = value && apiNavigationGroups.some((group) => (group.resources as readonly string[]).includes(value)) ? value : null;
      setSelectedApiResource(resource);
      setActiveNav(resource ? 'API Catalog' : 'Dashboard');
    };
    window.addEventListener('popstate', restoreResource);
    return () => window.removeEventListener('popstate', restoreResource);
  }, []);

  const announce = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 3200);
  };
  const clearPatientCreateRequest = useCallback(() => setCreatePatientRequest(0), []);
  const runQuickAction = (name: string) => {
    setActiveNav(name);
    if (name === 'API Catalog') setSelectedApiResource(null);
    window.history.pushState(null, '', name === 'Dashboard' ? '/' : window.location.pathname);
  };
  const openApiResource = (name: string) => {
    setSelectedApiResource(name);
    setActiveNav('API Catalog');
    window.history.pushState(null, '', `/?resource=${encodeURIComponent(name)}`);
  };

  return (
    <div className="min-h-[100dvh] bg-[#f5f7fb] text-slate-800">
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((value) => !value)} activeNav={activeNav} setActiveNav={runQuickAction} mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} selectedResource={selectedApiResource} onSelectResource={openApiResource} />
      <main className={`min-h-[100dvh] transition-[margin] duration-300 md:ml-[258px] ${collapsed ? 'md:ml-[82px]' : ''}`}>
        <header className="sticky top-0 z-30 flex h-[72px] items-center gap-3 border-b border-slate-200/70 bg-[#f8f9fc]/95 px-4 backdrop-blur-md sm:px-6 lg:px-9">
          <button onClick={() => setMobileOpen(true)} data-testid="button-open-mobile-nav" aria-label="Open navigation" className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-100 md:hidden"><Menu size={20} /></button>
          <div className="relative w-full max-w-[340px]">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search patients, appointments..." aria-label="Search dashboard" data-testid="input-global-search" className="h-[38px] w-full rounded-xl border border-slate-200 bg-white pl-9 pr-12 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" />
            <kbd className="absolute right-2.5 top-1/2 hidden -translate-y-1/2 rounded border border-slate-200 bg-slate-50 px-1.5 py-0.5 text-[9px] text-slate-400 sm:inline">⌘ K</kbd>
          </div>
          <div className="ml-auto flex shrink-0 items-center gap-2 sm:gap-3">
            <div className="relative">
              <button onClick={() => setNoticeOpen((value) => !value)} data-testid="button-notifications" aria-label="Notifications" className="relative flex h-9 w-9 items-center justify-center rounded-xl text-slate-500 transition hover:bg-white hover:text-slate-800"><Bell size={18} strokeWidth={1.8} /><span className="absolute right-[8px] top-[7px] h-2 w-2 rounded-full border-2 border-[#f8f9fc] bg-rose-500" /></button>
              {noticeOpen && <div className="absolute right-0 top-12 z-50 w-[280px] rounded-2xl border border-slate-200 bg-white p-4 shadow-xl" data-testid="panel-notifications"><div className="flex items-center justify-between"><p className="text-sm font-bold text-slate-800">Notifications</p><button onClick={() => { setNoticeOpen(false); announce('All caught up.'); }} className="text-[10px] font-semibold text-blue-600" data-testid="button-mark-read">Mark all read</button></div><div className="mt-4 space-y-3"><p className="flex gap-2 text-[11px] leading-5 text-slate-600"><span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" />Nisha Patel confirmed her 9:45 appointment.</p><p className="flex gap-2 text-[11px] leading-5 text-slate-600"><span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />3 invoices are due for follow-up today.</p></div></div>}
            </div>
            {can('ASSISTANT_USE') && <button onClick={() => setAiOpen(true)} aria-haspopup="dialog" aria-expanded={aiOpen} data-testid="button-ai-assistant" className="hidden h-[37px] items-center gap-2 rounded-xl bg-[#315fe7] px-3.5 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.2)] transition hover:bg-[#244fcf] sm:flex"><Sparkles size={15} /> AI Assistant</button>}
            <div className="relative">
              <button onClick={() => setProfileOpen((open) => !open)} data-testid="button-user-profile" aria-label="Open profile menu" aria-haspopup="menu" aria-expanded={profileOpen} className="flex items-center gap-2 rounded-xl py-1 pl-1 pr-1.5 transition hover:bg-white">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#e4edf9] text-[10px] font-bold text-[#395781]">{initialsOf(user?.fullName ?? '')}</span><span className="hidden text-left sm:block"><span className="block text-[11px] font-bold leading-4 text-slate-700">{user?.fullName}</span><span className="block text-[9px] text-slate-400">{user?.roleLabel}</span></span><ChevronDown size={13} className="hidden text-slate-400 sm:block" />
              </button>
              {profileOpen && (
                <div role="menu" className="absolute right-0 top-12 z-50 w-[230px] rounded-2xl border border-slate-200 bg-white p-2 shadow-xl" data-testid="menu-profile">
                  <div className="border-b border-slate-100 px-3 pb-2.5 pt-1.5"><p className="truncate text-[12px] font-bold text-slate-800">{user?.fullName}</p><p className="truncate text-[10px] text-slate-400">{user?.email} · {user?.roleLabel}</p></div>
                  <button role="menuitem" type="button" onClick={() => { setProfileOpen(false); setPasswordOpen(true); }} className="mt-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-[11.5px] font-semibold text-slate-600 hover:bg-slate-50"><KeyRound size={14} /> Change password</button>
                  <button role="menuitem" type="button" onClick={() => { setProfileOpen(false); void signOut(); }} data-testid="button-sign-out" className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-[11.5px] font-semibold text-rose-600 hover:bg-rose-50"><LogOut size={14} /> Sign out</button>
                </div>
              )}
            </div>
          </div>
        </header>

        {activeNav === 'Dashboard' && (
          <HomeDashboard
            search={search}
            announce={announce}
            onNavigate={setActiveNav}
            onNewAppointment={() => { setActiveNav('Appointments'); setCreateAppointmentRequest((request) => request + 1); }}
            onAddPatient={() => { setActiveNav('Patients'); setCreatePatientRequest((request) => request + 1); }}
          />
        )}
        {activeNav === 'Patients' && can('PATIENTS_READ') && <PatientsScreen search={search} onSearchChange={setSearch} announce={announce} createRequest={createPatientRequest} onCreateRequestHandled={clearPatientCreateRequest} />}
        {activeNav === 'Subscription' && can('SYNC_MANAGE') && <SubscriptionScreen />}
        {activeNav === 'Users' && can('USERS_MANAGE') && <UsersScreen />}
        {can('APPOINTMENTS_READ') && (
          <div hidden={activeNav !== 'Appointments'}>
            <AppointmentsScreen search={search} onSearchChange={setSearch} announce={announce} createRequest={createAppointmentRequest} />
          </div>
        )}
        {activeNav === 'API Catalog' && (
          selectedApiResource === 'Allergies'
            ? <AllergiesScreen />
            : selectedApiResource === 'AllergyDefs'
              ? <AllergyDefinitionsScreen />
            : selectedApiResource === 'DiseaseDefs'
              ? <DiseaseDefinitionsScreen />
            : databaseResource
              ? <DatabaseResourceScreen key={databaseResource} name={databaseResource} />
              : <ApiCatalogScreen selectedResource={selectedApiResource} />
        )}
      </main>
      {passwordOpen && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center bg-slate-950/35 p-4 backdrop-blur-[2px]" onMouseDown={(e) => { if (e.target === e.currentTarget) setPasswordOpen(false); }}>
          <section role="dialog" aria-modal="true" aria-labelledby="change-password-title" className="w-full max-w-[400px] rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl">
            <h2 id="change-password-title" className="font-[Manrope] text-[18px] font-extrabold text-slate-900">Change password</h2>
            <ChangePasswordForm onDone={() => { setPasswordOpen(false); announce('Password changed.'); }} onCancel={() => setPasswordOpen(false)} />
          </section>
        </div>
      )}
      <AssistantPanel
        open={aiOpen}
        onClose={() => setAiOpen(false)}
        onOpenPatient={(patient) => { setAiOpen(false); setSearch(patient.name); setActiveNav('Patients'); }}
      />
      {toast && <div role="status" data-testid="status-feedback" className="fixed bottom-5 left-1/2 z-[80] flex -translate-x-1/2 items-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-[11px] font-medium text-white shadow-xl"><CheckCircle2 size={15} className="text-emerald-300" />{toast}</div>}
    </div>
  );
}

export default App;