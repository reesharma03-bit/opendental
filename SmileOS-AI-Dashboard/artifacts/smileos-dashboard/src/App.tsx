import { useMemo, useState } from 'react';
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, BarChart3,
  Bell, BookOpen, CalendarDays, Check, CheckCircle2, ChevronDown, ChevronLeft, ChevronRight,
  CircleDollarSign, Clock3, CreditCard, LayoutDashboard,
  Lightbulb, Menu, MessageSquareText, MoreHorizontal, Search, Settings, ShieldCheck,
  Sparkles, Stethoscope, UserRoundPlus, Users, Wallet, X, Zap,
} from 'lucide-react';
import {
  Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import AppointmentsScreen from './AppointmentsScreen';
import ApiCatalogScreen from './ApiCatalogScreen';
import PatientsScreen from './PatientsScreen';
import { apiNavigationGroups, formatApiResourceName } from './apiNavigation';

type Patient = { name: string; initials: string; detail: string; color: string; id: string };

const navItems = [
  { label: 'Dashboard', icon: LayoutDashboard },
  { label: 'Patients', icon: Users, badge: '2.4k' },
  { label: 'Appointments', icon: CalendarDays },
  { label: 'API Catalog', icon: BookOpen },
];

const patients: Patient[] = [
  { name: 'Aarav Mehta', initials: 'AM', detail: 'Cleaning · 9:00 AM', color: 'bg-sky-100 text-sky-700', id: 'P-1048' },
  { name: 'Ananya Kapoor', initials: 'AK', detail: 'Root canal · 10:30 AM', color: 'bg-violet-100 text-violet-700', id: 'P-1032' },
  { name: 'Rohan Desai', initials: 'RD', detail: 'Consultation · 11:15 AM', color: 'bg-amber-100 text-amber-700', id: 'P-1009' },
  { name: 'Mira Iyer', initials: 'MI', detail: 'Whitening · 1:00 PM', color: 'bg-rose-100 text-rose-700', id: 'P-0988' },
  { name: 'Kabir Singh', initials: 'KS', detail: 'Crown fitting · 2:30 PM', color: 'bg-emerald-100 text-emerald-700', id: 'P-0974' },
];

const appointments = [
  { time: '09:00 AM', name: 'Aarav Mehta', initials: 'AM', treatment: 'Routine cleaning', dentist: 'Dr. Sharma', duration: '45 min', status: 'Checked in', tone: 'green' },
  { time: '09:45 AM', name: 'Nisha Patel', initials: 'NP', treatment: 'Dental consultation', dentist: 'Dr. Sharma', duration: '30 min', status: 'Confirmed', tone: 'blue' },
  { time: '10:30 AM', name: 'Ananya Kapoor', initials: 'AK', treatment: 'Root canal therapy', dentist: 'Dr. Mehta', duration: '90 min', status: 'In progress', tone: 'violet' },
  { time: '11:15 AM', name: 'Rohan Desai', initials: 'RD', treatment: 'New patient exam', dentist: 'Dr. Sharma', duration: '45 min', status: 'Confirmed', tone: 'blue' },
  { time: '12:00 PM', name: 'Priya Nair', initials: 'PN', treatment: 'Follow-up visit', dentist: 'Dr. Mehta', duration: '30 min', status: 'Pending', tone: 'amber' },
];

const revenueData = [
  { day: 'Mon', value: 3850 }, { day: 'Tue', value: 4920 }, { day: 'Wed', value: 4260 },
  { day: 'Thu', value: 5630 }, { day: 'Fri', value: 4810 }, { day: 'Sat', value: 6240 }, { day: 'Sun', value: 5380 },
];

const statusClasses: Record<string, string> = {
  green: 'bg-emerald-50 text-emerald-700',
  blue: 'bg-blue-50 text-blue-700',
  violet: 'bg-violet-50 text-violet-700',
  amber: 'bg-amber-50 text-amber-700',
};

function Sidebar({
  collapsed, onToggle, activeNav, setActiveNav, mobileOpen, onClose,
  selectedResource, onSelectResource,
}: {
  collapsed: boolean; onToggle: () => void; activeNav: string; setActiveNav: (name: string) => void;
  mobileOpen: boolean; onClose: () => void;
  selectedResource: string | null; onSelectResource: (name: string) => void;
}) {
  const [expandedGroups, setExpandedGroups] = useState<string[]>([]);

  const toggleGroup = (label: string) => {
    setExpandedGroups((groups) => groups.includes(label)
      ? groups.filter((group) => group !== label)
      : [...groups, label]);
  };

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
            {navItems.map(({ label, icon: Icon, badge }) => (
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
            <nav aria-label="Open Dental API resources" className="space-y-1">
              {apiNavigationGroups.map(({ label, resources }) => {
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
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#e4edf9] text-xs font-bold text-[#395781]">DS</div>
            <div className={`min-w-0 ${collapsed ? 'md:hidden' : ''}`}><p className="truncate text-[12px] font-bold text-slate-800">Dr. Sharma</p><p className="truncate text-[10px] text-slate-400">Practice owner</p></div>
            <MoreHorizontal size={17} className={`ml-auto text-slate-400 ${collapsed ? 'md:hidden' : ''}`} />
          </div>
        </div>
      </aside>
    </>
  );
}

function MetricCard({ label, value, change, icon: Icon, iconTone, trend, note, testId }: {
  label: string; value: string; change: string; icon: typeof Users; iconTone: string; trend: 'up' | 'down'; note: string; testId: string;
}) {
  return (
    <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] transition hover:-translate-y-0.5 hover:shadow-[0_8px_24px_rgba(26,49,91,0.07)]" data-testid={testId}>
      <div className="flex items-start justify-between"><p className="text-[12px] font-semibold text-slate-500">{label}</p><span className={`flex h-9 w-9 items-center justify-center rounded-xl ${iconTone}`}><Icon size={18} strokeWidth={1.9} /></span></div>
      <div className="mt-3 flex items-end justify-between gap-2"><p className="font-[Manrope] text-[27px] font-extrabold leading-none tracking-[-1.2px] text-slate-900">{value}</p><span className={`mb-0.5 inline-flex items-center gap-0.5 rounded-md px-1.5 py-1 text-[10px] font-bold ${trend === 'up' ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'}`}>{trend === 'up' ? <ArrowUpRight size={12} /> : <ArrowDownRight size={12} />}{change}</span></div>
      <p className="mt-2.5 text-[10px] text-slate-400">{note}</p>
    </section>
  );
}

function Avatar({ initials, tone }: { initials: string; tone?: string }) {
  return <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${tone || 'bg-slate-100 text-slate-600'}`}>{initials}</span>;
}

function App() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [activeNav, setActiveNav] = useState('Dashboard');
  const [selectedApiResource, setSelectedApiResource] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [toast, setToast] = useState('');
  const [createPatientRequest, setCreatePatientRequest] = useState(0);
  const [createAppointmentRequest, setCreateAppointmentRequest] = useState(0);
  const [aiOpen, setAiOpen] = useState(false);
  const [period, setPeriod] = useState('This week');
  const [checkedIn, setCheckedIn] = useState<string[]>(['Aarav Mehta']);

  const visibleAppointments = useMemo(() => appointments.filter((item) =>
    `${item.name} ${item.treatment} ${item.dentist} ${item.status}`.toLowerCase().includes(search.toLowerCase())), [search]);
  const visiblePatients = useMemo(() => patients.filter((patient) =>
    `${patient.name} ${patient.detail} ${patient.id}`.toLowerCase().includes(search.toLowerCase())), [search]);
  const dateLabel = new Intl.DateTimeFormat('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
  const weekdayLabel = new Intl.DateTimeFormat('en-IN', { weekday: 'long' }).format(new Date()).toUpperCase();

  const announce = (message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 3200);
  };
  const runQuickAction = (name: string) => {
    setActiveNav(name);
    if (name === 'API Catalog') setSelectedApiResource(null);
  };
  const openApiResource = (name: string) => {
    setSelectedApiResource(name);
    setActiveNav('API Catalog');
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
            <button onClick={() => { setAiOpen((value) => !value); announce('AI Assistant is a display-only sample feature.'); }} data-testid="button-ai-assistant" className="hidden h-[37px] items-center gap-2 rounded-xl bg-[#315fe7] px-3.5 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.2)] transition hover:bg-[#244fcf] sm:flex"><Sparkles size={15} /> AI Assistant</button>
            <button onClick={() => announce('Profile menu opened.')} data-testid="button-user-profile" aria-label="Open profile" className="flex items-center gap-2 rounded-xl py-1 pl-1 pr-1.5 transition hover:bg-white">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#e4edf9] text-[10px] font-bold text-[#395781]">DS</span><span className="hidden text-left sm:block"><span className="block text-[11px] font-bold leading-4 text-slate-700">Dr. Sharma</span><span className="block text-[9px] text-slate-400">Owner</span></span><ChevronDown size={13} className="hidden text-slate-400 sm:block" />
            </button>
          </div>
        </header>

        <div hidden={activeNav !== 'Dashboard'} className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9">
          <section className="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
            <div><p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400"><span className="h-1.5 w-1.5 rounded-full bg-emerald-500" /> {weekdayLabel}, YOUR PRACTICE IS LOOKING GOOD</p><h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-greeting">Good morning, Dr. Sharma<span className="text-blue-600">.</span></h1><p className="mt-1.5 text-[12px] text-slate-500" data-testid="text-today-date">{dateLabel} <span className="mx-1.5 text-slate-300">·</span> Here’s your practice at a glance.</p></div>
            <div className="flex items-center gap-2">
              <button onClick={() => announce('Calendar view is part of the display-only dashboard.')} data-testid="button-calendar-date" className="flex h-9 items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 shadow-sm hover:border-slate-300"><CalendarDays size={14} className="text-slate-400" /> Today <ChevronDown size={13} className="text-slate-400" /></button>
               <button onClick={() => { setActiveNav('Appointments'); setCreateAppointmentRequest((request) => request + 1); }} data-testid="button-new-appointment" className="flex h-9 items-center gap-2 rounded-xl bg-[#315fe7] px-3.5 text-[11px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf]"><span className="text-[16px] font-normal leading-none">+</span> New appointment</button>
            </div>
          </section>

          <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Practice key metrics">
            <MetricCard label="Today's Appointments" value="18" change="+12.5%" icon={CalendarDays} iconTone="bg-blue-50 text-blue-600" trend="up" note="Compared to last week" testId="card-kpi-appointments" />
            <MetricCard label="Active Patients" value="2,486" change="+4.8%" icon={Users} iconTone="bg-cyan-50 text-cyan-700" trend="up" note="Across your practice" testId="card-kpi-patients" />
            <MetricCard label="Revenue this month" value="₹4,82,650" change="+8.2%" icon={CircleDollarSign} iconTone="bg-emerald-50 text-emerald-700" trend="up" note="₹42,300 above last month" testId="card-kpi-revenue" />
            <MetricCard label="Outstanding payments" value="₹68,420" change="6.3%" icon={Wallet} iconTone="bg-amber-50 text-amber-700" trend="down" note="Across 14 pending invoices" testId="card-kpi-outstanding" />
          </section>

          <section className="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.72fr)_minmax(320px,.88fr)]">
            <div className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-todays-appointments">
              <div className="flex flex-wrap items-center justify-between gap-3 px-5 pb-4 pt-5 sm:px-6">
                <div><div className="flex items-center gap-2"><h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Today's appointments</h2><span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700">18</span></div><p className="mt-1 text-[10px] text-slate-400">Your schedule for today</p></div>
                 <button onClick={() => setActiveNav('Appointments')} data-testid="button-view-all-appointments" className="flex items-center gap-1 text-[10px] font-bold text-blue-600 hover:text-blue-800">View schedule <ArrowRight size={13} /></button>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-left">
                  <thead><tr className="border-y border-slate-100 bg-slate-50/65 text-[9px] font-bold uppercase tracking-[.8px] text-slate-400"><th className="px-5 py-2.5 sm:px-6">Time</th><th className="px-3 py-2.5">Patient</th><th className="px-3 py-2.5">Treatment</th><th className="px-3 py-2.5">Dentist</th><th className="px-3 py-2.5">Status</th><th className="px-5 py-2.5 sm:px-6">Action</th></tr></thead>
                  <tbody>{visibleAppointments.map((row, index) => <tr key={row.name} className="border-b border-slate-100/80 last:border-0 hover:bg-slate-50/50" data-testid={`row-appointment-${index}`}>
                    <td className="whitespace-nowrap px-5 py-3.5 text-[10px] font-semibold text-slate-500 sm:px-6"><span className="flex items-center gap-1.5"><Clock3 size={12} className="text-slate-400" />{row.time}</span></td>
                    <td className="whitespace-nowrap px-3 py-3"><div className="flex items-center gap-2.5"><Avatar initials={row.initials} tone={patients.find((p) => p.initials === row.initials)?.color} /><div><p className="text-[11px] font-bold text-slate-700">{row.name}</p><p className="mt-0.5 text-[9px] text-slate-400">{row.duration}</p></div></div></td>
                    <td className="whitespace-nowrap px-3 py-3 text-[10px] text-slate-500">{row.treatment}</td><td className="whitespace-nowrap px-3 py-3 text-[10px] text-slate-500">{row.dentist}</td>
                    <td className="whitespace-nowrap px-3 py-3"><span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${statusClasses[row.tone]}`}>{row.status}</span></td>
                    <td className="whitespace-nowrap px-5 py-3 sm:px-6">{checkedIn.includes(row.name) ? <span className="inline-flex items-center gap-1 text-[9px] font-semibold text-emerald-600"><Check size={12} /> Checked in</span> : <button onClick={() => { setCheckedIn((items) => [...items, row.name]); announce(`${row.name} checked in.`); }} data-testid={`button-check-in-${index}`} className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-[9px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700">Check in</button>}</td>
                  </tr>)}
                  {visibleAppointments.length === 0 && <tr><td colSpan={6} className="px-6 py-10 text-center text-xs text-slate-400" data-testid="empty-appointments">No appointments match “{search}”.</td></tr>}</tbody>
                </table>
              </div>
              <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 sm:px-6"><span className="text-[10px] text-slate-400">Showing {visibleAppointments.length} of 18 appointments</span><div className="flex items-center gap-1"><button aria-label="Previous appointments" data-testid="button-previous-appointments" onClick={() => announce('You are viewing today’s appointment list.')} className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-slate-50"><ChevronLeft size={14} /></button><span className="px-2 text-[10px] font-semibold text-slate-500">1 / 4</span><button aria-label="Next appointments" data-testid="button-next-appointments" onClick={() => announce('More appointments are available in the complete schedule.')} className="flex h-7 w-7 items-center justify-center rounded-lg border border-slate-200 text-slate-400 hover:bg-slate-50"><ChevronRight size={14} /></button></div></div>
            </div>

            <div className="flex flex-col gap-5">
              <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-appointment-status">
                <div className="flex items-start justify-between"><div><h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Appointment status</h2><p className="mt-1 text-[10px] text-slate-400">Today's schedule at a glance</p></div><button onClick={() => announce('Status breakdown for today.')} aria-label="Appointment status options" data-testid="button-status-options" className="flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100"><MoreHorizontal size={17} /></button></div>
                <div className="mt-5 flex items-center gap-5">
                  <div className="relative flex h-[112px] w-[112px] shrink-0 items-center justify-center rounded-full" style={{ background: 'conic-gradient(#315fe7 0deg 178deg, #56b6c1 178deg 258deg, #a99bec 258deg 318deg, #f3c36c 318deg 360deg)' }}>
                    <div className="flex h-[82px] w-[82px] flex-col items-center justify-center rounded-full bg-white"><span className="font-[Manrope] text-[22px] font-extrabold text-slate-800">18</span><span className="text-[9px] text-slate-400">total today</span></div>
                  </div>
                  <div className="min-w-0 flex-1 space-y-2.5">
                    {[['Confirmed', 8, '#315fe7'], ['Completed', 5, '#56b6c1'], ['In progress', 3, '#a99bec'], ['Pending', 2, '#f3c36c']].map(([label, amount, color]) => <div key={label as string} className="flex items-center gap-2"><span className="h-2 w-2 rounded-full" style={{ background: color as string }} /><span className="flex-1 text-[10px] text-slate-500">{label}</span><span className="text-[10px] font-bold text-slate-700">{amount}</span></div>)}
                  </div>
                </div>
                <div className="mt-4 flex items-center gap-2 rounded-xl bg-emerald-50/75 px-3 py-2.5 text-[10px] text-emerald-800"><CheckCircle2 size={14} className="shrink-0 text-emerald-600" /><span><b>On schedule</b> — next appointment in 12 min</span></div>
              </section>
              <section className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-[#253f9e] via-[#3154c5] to-[#4867d5] p-5 text-white shadow-[0_8px_24px_rgba(42,74,173,.16)]" data-testid="card-ai-insights">
                <div className="absolute -right-9 -top-11 h-36 w-36 rounded-full border border-white/10" /><div className="absolute -right-1 -top-4 h-24 w-24 rounded-full border border-white/10" />
                <div className="relative flex items-start justify-between"><div><div className="flex items-center gap-2"><span className="flex h-7 w-7 items-center justify-center rounded-lg bg-white/15"><Sparkles size={15} /></span><span className="text-[12px] font-bold">AI practice insights</span></div><p className="mt-3 max-w-[250px] text-[13px] font-semibold leading-[1.45]">A sharper view of what needs your attention.</p></div><span className="rounded-md border border-white/15 bg-white/10 px-2 py-1 text-[8px] font-bold uppercase tracking-[1px] text-blue-100">Today</span></div>
                <div className="relative mt-4 space-y-3 border-t border-white/15 pt-3">
                  <p className="flex gap-2.5 text-[10px] leading-[1.55] text-blue-50"><span className="mt-0.5 text-cyan-200"><Zap size={13} /></span><span><b className="text-white">Opportunity:</b> 4 patients are due for a hygiene recall this week.</span></p>
                  <p className="flex gap-2.5 text-[10px] leading-[1.55] text-blue-50"><span className="mt-0.5 text-cyan-200"><Lightbulb size={13} /></span><span><b className="text-white">Heads up:</b> Afternoon chair utilization is 18% below your weekly average.</span></p>
                </div>
                {aiOpen && <div className="relative mt-3 rounded-xl border border-white/15 bg-white/10 p-3 text-[10px] leading-5 text-blue-50" data-testid="panel-ai-assistant"><b className="text-white">Assistant ready.</b> Review recall opportunities or ask about today's schedule. This sample uses display-only practice data.</div>}
                <button onClick={() => setAiOpen((value) => !value)} data-testid="button-review-ai-insights" className="relative mt-4 flex h-8 w-full items-center justify-center gap-2 rounded-lg bg-white text-[10px] font-bold text-[#294ab0] transition hover:bg-blue-50">{aiOpen ? 'Close AI assistant' : 'Review insights'} <ArrowRight size={13} /></button>
              </section>
            </div>
          </section>

          <section className="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1.22fr)_minmax(330px,.78fr)]">
            <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-6" data-testid="section-revenue-chart">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div><div className="flex items-center gap-2"><h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Revenue overview</h2><span className="flex items-center gap-1 rounded-md bg-emerald-50 px-1.5 py-1 text-[9px] font-bold text-emerald-700"><ArrowUpRight size={11} /> 8.2%</span></div><p className="mt-1 text-[10px] text-slate-400">Revenue collected over the last 7 days</p></div>
                <button onClick={() => setPeriod((value) => value === 'This week' ? 'Last week' : 'This week')} data-testid="button-revenue-period" className="flex h-8 items-center gap-2 rounded-lg border border-slate-200 px-2.5 text-[10px] font-semibold text-slate-600 hover:bg-slate-50">{period}<ChevronDown size={13} /></button>
              </div>
              <div className="mt-3 flex items-end gap-2"><span className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900" data-testid="text-revenue-total">₹35,090</span><span className="mb-1 text-[9px] text-slate-400">this week</span></div>
              <div className="mt-3 h-[186px] w-full" data-testid="chart-weekly-revenue">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={revenueData} margin={{ top: 12, right: 4, left: -20, bottom: 0 }}>
                    <defs><linearGradient id="revenueFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#4d78ed" stopOpacity={0.19} /><stop offset="95%" stopColor="#4d78ed" stopOpacity={0.005} /></linearGradient></defs>
                    <CartesianGrid vertical={false} stroke="#eef1f6" strokeDasharray="3 4" />
                    <XAxis dataKey="day" axisLine={false} tickLine={false} tick={{ fill: '#9aa6b7', fontSize: 10 }} dy={8} />
                    <YAxis axisLine={false} tickLine={false} tick={{ fill: '#9aa6b7', fontSize: 9 }} tickFormatter={(value: number) => `₹${value / 1000}k`} />
                    <Tooltip cursor={{ stroke: '#bac9f7', strokeDasharray: '4 4' }} contentStyle={{ border: '1px solid #e7edf6', borderRadius: 10, fontSize: 11, boxShadow: '0 5px 16px rgba(31,53,88,.08)' }} formatter={(value: number) => [`₹${value.toLocaleString('en-IN')}`, 'Revenue']} />
                    <Area type="monotone" dataKey="value" stroke="#426be4" strokeWidth={2.5} fill="url(#revenueFill)" activeDot={{ r: 4, strokeWidth: 3, stroke: '#fff', fill: '#426be4' }} dot={{ r: 3, fill: '#fff', stroke: '#426be4', strokeWidth: 2 }} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </section>
            <section className="rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-6" data-testid="section-recent-patients">
              <div className="flex items-start justify-between"><div><h2 className="font-[Manrope] text-[14px] font-extrabold text-slate-800">Recent patients</h2><p className="mt-1 text-[10px] text-slate-400">Latest arrivals at your practice</p></div><button onClick={() => setActiveNav('Patients')} data-testid="button-view-patients" className="mt-0.5 flex items-center gap-1 text-[10px] font-bold text-blue-600 hover:text-blue-800">View all <ArrowRight size={12} /></button></div>
              <div className="mt-4 divide-y divide-slate-100">
                {visiblePatients.map((patient) => <div key={patient.id} className="flex items-center gap-2.5 py-2.5 first:pt-0 last:pb-0" data-testid={`row-patient-${patient.id}`}><Avatar initials={patient.initials} tone={patient.color} /><div className="min-w-0 flex-1"><p className="truncate text-[11px] font-bold text-slate-700">{patient.name}</p><p className="mt-0.5 truncate text-[9px] text-slate-400">{patient.detail}</p></div><span className="rounded-md bg-slate-50 px-1.5 py-1 text-[8px] font-semibold text-slate-400">{patient.id}</span><button onClick={() => announce(`Patient details for ${patient.name} are display-only.`)} aria-label={`Open ${patient.name} profile`} data-testid={`button-patient-${patient.id}`} className="ml-0.5 flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 hover:bg-blue-50 hover:text-blue-600"><ArrowRight size={13} /></button></div>)}
                {visiblePatients.length === 0 && <p className="py-9 text-center text-xs text-slate-400" data-testid="empty-patients">No patients match “{search}”.</p>}
              </div>
              <button onClick={() => { setActiveNav('Patients'); setCreatePatientRequest((request) => request + 1); }} data-testid="button-add-patient" className="mt-4 flex h-9 w-full items-center justify-center gap-2 rounded-xl border border-dashed border-slate-200 text-[10px] font-semibold text-slate-500 transition hover:border-blue-200 hover:bg-blue-50/50 hover:text-blue-700"><UserRoundPlus size={14} /> Add a patient</button>
            </section>
          </section>

          <footer className="mt-6 flex flex-col items-center justify-between gap-2 border-t border-slate-200/75 pt-4 text-[9px] text-slate-400 sm:flex-row"><span className="flex items-center gap-1.5"><ShieldCheck size={12} className="text-emerald-600" /> SmileOS keeps your practice in sync.</span><span>Sample dashboard data · For display purposes only</span></footer>
        </div>
        <div hidden={activeNav !== 'Patients'}>
          <PatientsScreen search={search} onSearchChange={setSearch} announce={announce} createRequest={createPatientRequest} />
        </div>
        <div hidden={activeNav !== 'Appointments'}>
          <AppointmentsScreen search={search} onSearchChange={setSearch} announce={announce} createRequest={createAppointmentRequest} />
        </div>
        <div hidden={activeNav !== 'API Catalog'}>
          <ApiCatalogScreen selectedResource={selectedApiResource} />
        </div>
      </main>
      {toast && <div role="status" data-testid="status-feedback" className="fixed bottom-5 left-1/2 z-[80] flex -translate-x-1/2 items-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-[11px] font-medium text-white shadow-xl"><CheckCircle2 size={15} className="text-emerald-300" />{toast}</div>}
    </div>
  );
}

export default App;