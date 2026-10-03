import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Bell,
  BellOff,
  Flame,
  Sparkles,
  Users,
  Wind,
  Moon,
  Heart,
  CalendarCheck,
  ChevronRight,
  Check,
  X,
  Settings2,
  Leaf,
} from 'lucide-react';

const NOTIFICATIONS = [
  {
    id: 1,
    group: 'today',
    type: 'streak',
    icon: Flame,
    tone: 'amber',
    title: '21-day streak',
    body: 'Three weeks of showing up for yourself. Your longest streak yet.',
    time: '7:02 AM',
    unread: true,
    category: 'practice',
    action: 'View milestone',
  },
  {
    id: 2,
    group: 'today',
    type: 'live',
    icon: Wind,
    tone: 'sage',
    title: 'Tara Brach is live in 40 minutes',
    body: '"Letting Go of Control" — a 30-minute guided sit with live Q&A after.',
    time: '8:20 AM',
    unread: true,
    category: 'practice',
    action: 'Reserve a cushion',
  },
  {
    id: 3,
    group: 'today',
    type: 'community',
    icon: Users,
    tone: 'clay',
    title: 'Maya finished "Grief & Gratitude"',
    body: 'Your circle member completed the 7-day course you recommended.',
    time: '11:45 AM',
    unread: true,
    category: 'community',
    action: null,
  },
  {
    id: 4,
    group: 'yesterday',
    type: 'insight',
    icon: Sparkles,
    tone: 'sage',
    title: 'Your weekly reflection is ready',
    body: '94 mindful minutes, mostly in the evening. Your calmest day was Thursday.',
    time: 'Yesterday, 9:00 PM',
    unread: false,
    category: 'insights',
    action: 'Open reflection',
  },
  {
    id: 5,
    group: 'yesterday',
    type: 'sleep',
    icon: Moon,
    tone: 'ink',
    title: 'New sleep story: "The Lighthouse Keeper"',
    body: 'Narrated by Idris Wakefield. 42 minutes of slow Atlantic fog.',
    time: 'Yesterday, 6:30 PM',
    unread: false,
    category: 'practice',
    action: null,
  },
  {
    id: 6,
    group: 'earlier',
    type: 'community',
    icon: Heart,
    tone: 'clay',
    title: '3 people sent you metta',
    body: 'Jonas, Priya, and one anonymous practitioner wished you well after your shared sit.',
    time: 'Tue, 8:14 PM',
    unread: false,
    category: 'community',
    action: null,
  },
  {
    id: 7,
    group: 'earlier',
    type: 'reminder',
    icon: CalendarCheck,
    tone: 'sage',
    title: 'Retreat registration closes Friday',
    body: 'Half-day silent retreat · Saturday, March 22 · 9 AM with Joseph Goldstein.',
    time: 'Mon, 10:00 AM',
    unread: false,
    category: 'practice',
    action: 'Save my place',
  },
];

const TONES = {
  amber: { bg: '#F4E3C8', fg: '#9A6B2F' },
  sage: { bg: '#DCE5D6', fg: '#5A7252' },
  clay: { bg: '#EBD9D1', fg: '#A06B58' },
  ink: { bg: '#DDDDE6', fg: '#4A4A66' },
};

const FILTERS = [
  { key: 'all', label: 'All' },
  { key: 'practice', label: 'Practice' },
  { key: 'community', label: 'Circle' },
  { key: 'insights', label: 'Insights' },
];

const GROUP_LABELS = { today: 'Today', yesterday: 'Yesterday', earlier: 'Earlier this week' };

export default function App() {
  const [filter, setFilter] = useState('all');
  const [items, setItems] = useState(NOTIFICATIONS);
  const [quietHours, setQuietHours] = useState(true);

  const visible = items.filter((n) => filter === 'all' || n.category === filter);
  const unreadCount = items.filter((n) => n.unread).length;

  const dismiss = (id) => setItems((prev) => prev.filter((n) => n.id !== id));
  const markAllRead = () => setItems((prev) => prev.map((n) => ({ ...n, unread: false })));
  const markRead = (id) =>
    setItems((prev) => prev.map((n) => (n.id === id ? { ...n, unread: false } : n)));

  const groups = ['today', 'yesterday', 'earlier'].filter((g) =>
    visible.some((n) => n.group === g)
  );

  return (
    <div className="min-h-screen w-full flex items-center justify-center px-4 py-10 bg-[#EFEAE0] relative overflow-hidden">
      <link
        href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600&family=Inter:wght@400;450;500;600&display=swap"
        rel="stylesheet"
      />
      <style
        dangerouslySetInnerHTML={{
          __html: `
        body { margin: 0; }
        .serif { font-family: 'Fraunces', serif; }
        .sans { font-family: 'Inter', sans-serif; }
        .grain::before {
          content: '';
          position: absolute;
          inset: 0;
          background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.035'/%3E%3C/svg%3E");
          pointer-events: none;
        }
        .panel-scroll::-webkit-scrollbar { width: 5px; }
        .panel-scroll::-webkit-scrollbar-track { background: transparent; }
        .panel-scroll::-webkit-scrollbar-thumb { background: #D5CDBE; border-radius: 99px; }
        @keyframes breathe {
          0%, 100% { transform: scale(1); opacity: 0.5; }
          50% { transform: scale(1.6); opacity: 0.15; }
        }
        .breathe { animation: breathe 5s ease-in-out infinite; }
      `,
        }}
      />

      {/* ambient backdrop */}
      <div className="absolute inset-0 grain" />
      <div
        className="absolute -top-40 -left-32 w-[34rem] h-[34rem] rounded-full opacity-50"
        style={{ background: 'radial-gradient(circle, #DCE5D6 0%, transparent 65%)' }}
      />
      <div
        className="absolute -bottom-48 -right-24 w-[38rem] h-[38rem] rounded-full opacity-60"
        style={{ background: 'radial-gradient(circle, #EBD9C4 0%, transparent 65%)' }}
      />

      {/* notification panel */}
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7, ease: [0.22, 1, 0.36, 1] }}
        className="relative w-full max-w-[460px] bg-[#FBF8F1] rounded-[28px] shadow-[0_24px_80px_-24px_rgba(72,62,44,0.35),0_2px_8px_rgba(72,62,44,0.06)] border border-[#E6DECF] overflow-hidden"
      >
        {/* header */}
        <div className="px-7 pt-7 pb-5">
          <div className="flex items-start justify-between">
            <div>
              <div className="flex items-center gap-2.5">
                <div className="relative flex items-center justify-center">
                  <span className="absolute w-8 h-8 rounded-full bg-[#5A7252] breathe" />
                  <div className="relative w-9 h-9 rounded-full bg-[#5A7252] flex items-center justify-center">
                    <Bell size={16} className="text-[#FBF8F1]" strokeWidth={2} />
                  </div>
                </div>
                <h1 className="serif text-[26px] font-medium text-[#33312A] tracking-[-0.01em]">
                  Stillness
                </h1>
              </div>
              <p className="sans text-[13px] text-[#8A8473] mt-2.5 leading-relaxed">
                {unreadCount > 0 ? (
                  <>
                    <span className="text-[#5A7252] font-medium">{unreadCount} gentle nudges</span>{' '}
                    waiting for you
                  </>
                ) : (
                  'Nothing needs your attention right now'
                )}
              </p>
            </div>
            <button className="mt-1 w-9 h-9 rounded-full flex items-center justify-center text-[#9C9583] hover:bg-[#F0EBDF] hover:text-[#5A5446] transition-colors">
              <Settings2 size={17} strokeWidth={1.75} />
            </button>
          </div>

          {/* filters */}
          <div className="flex items-center gap-1.5 mt-6">
            {FILTERS.map((f) => (
              <button
                key={f.key}
                onClick={() => setFilter(f.key)}
                className={`sans text-[12.5px] px-3.5 py-1.5 rounded-full transition-all duration-300 ${
                  filter === f.key
                    ? 'bg-[#33312A] text-[#FBF8F1] font-medium'
                    : 'text-[#7C7565] hover:bg-[#F0EBDF]'
                }`}
              >
                {f.label}
              </button>
            ))}
            <button
              onClick={markAllRead}
              className="sans ml-auto text-[12px] text-[#5A7252] hover:text-[#3F543A] font-medium flex items-center gap-1 transition-colors"
            >
              <Check size={13} strokeWidth={2.5} />
              Mark all read
            </button>
          </div>
        </div>

        <div className="h-px bg-gradient-to-r from-transparent via-[#E6DECF] to-transparent" />

        {/* notifications */}
        <div className="panel-scroll max-h-[480px] overflow-y-auto px-4 pt-3 pb-4">
          {groups.length === 0 && (
            <div className="py-16 text-center">
              <Leaf size={28} className="mx-auto text-[#C9C2B0]" strokeWidth={1.5} />
              <p className="serif text-[18px] text-[#7C7565] mt-4">All quiet here</p>
              <p className="sans text-[12.5px] text-[#A39C89] mt-1">A clear mind, a clear inbox.</p>
            </div>
          )}

          {groups.map((group) => (
            <div key={group} className="mb-2">
              <p className="sans text-[10.5px] font-semibold uppercase tracking-[0.14em] text-[#A39C89] px-3 pt-3 pb-2">
                {GROUP_LABELS[group]}
              </p>
              <AnimatePresence mode="popLayout">
                {visible
                  .filter((n) => n.group === group)
                  .map((n) => {
                    const Icon = n.icon;
                    const tone = TONES[n.tone];
                    return (
                      <motion.div
                        key={n.id}
                        layout
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, x: 60, transition: { duration: 0.25 } }}
                        transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
                        onClick={() => markRead(n.id)}
                        className={`group relative flex gap-3.5 px-3 py-3.5 rounded-2xl cursor-pointer transition-colors duration-300 ${
                          n.unread ? 'bg-[#F3EEE2]' : 'hover:bg-[#F5F1E7]'
                        }`}
                      >
                        <div
                          className="shrink-0 w-10 h-10 rounded-full flex items-center justify-center mt-0.5"
                          style={{ backgroundColor: tone.bg }}
                        >
                          <Icon size={17} strokeWidth={1.75} style={{ color: tone.fg }} />
                        </div>

                        <div className="flex-1 min-w-0 pr-5">
                          <div className="flex items-baseline justify-between gap-3">
                            <h3 className="serif text-[15.5px] font-medium text-[#33312A] leading-snug">
                              {n.title}
                            </h3>
                            <span className="sans shrink-0 text-[10.5px] text-[#A39C89] tracking-wide">
                              {n.time}
                            </span>
                          </div>
                          <p className="sans text-[12.5px] text-[#7C7565] leading-[1.55] mt-1">
                            {n.body}
                          </p>
                          {n.action && (
                            <button
                              className="sans mt-2.5 inline-flex items-center gap-1 text-[12px] font-medium transition-colors"
                              style={{ color: tone.fg }}
                            >
                              {n.action}
                              <ChevronRight size={13} strokeWidth={2.25} />
                            </button>
                          )}
                        </div>

                        {/* unread dot */}
                        {n.unread && (
                          <span className="absolute right-3.5 top-1/2 -translate-y-1/2 w-[7px] h-[7px] rounded-full bg-[#5A7252]" />
                        )}

                        {/* dismiss */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            dismiss(n.id);
                          }}
                          className="absolute -right-1 -top-1 w-6 h-6 rounded-full bg-[#FBF8F1] border border-[#E6DECF] text-[#A39C89] hover:text-[#33312A] hover:border-[#C9C2B0] items-center justify-center hidden group-hover:flex transition-colors shadow-sm"
                        >
                          <X size={11} strokeWidth={2.25} />
                        </button>
                      </motion.div>
                    );
                  })}
              </AnimatePresence>
            </div>
          ))}
        </div>

        {/* footer — quiet hours */}
        <div className="px-7 py-4 bg-[#F3EEE2] border-t border-[#E6DECF] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-[#E4DCCB] flex items-center justify-center">
              {quietHours ? (
                <BellOff size={14} className="text-[#7C7565]" strokeWidth={1.75} />
              ) : (
                <Bell size={14} className="text-[#7C7565]" strokeWidth={1.75} />
              )}
            </div>
            <div>
              <p className="sans text-[12.5px] font-medium text-[#4A463B]">Quiet hours</p>
              <p className="sans text-[11px] text-[#A39C89] mt-px">
                {quietHours ? 'Silenced from 9 PM to 7 AM' : 'Notifications arrive anytime'}
              </p>
            </div>
          </div>
          <button
            onClick={() => setQuietHours(!quietHours)}
            className={`relative w-11 h-6 rounded-full transition-colors duration-300 ${
              quietHours ? 'bg-[#5A7252]' : 'bg-[#D5CDBE]'
            }`}
          >
            <motion.span
              animate={{ x: quietHours ? 22 : 3 }}
              transition={{ type: 'spring', stiffness: 500, damping: 32 }}
              className="absolute top-[3px] left-0 w-[18px] h-[18px] rounded-full bg-[#FBF8F1] shadow-sm"
            />
          </button>
        </div>
      </motion.div>

      {/* small caption beneath */}
      <p className="sans absolute bottom-6 left-1/2 -translate-x-1/2 text-[11px] tracking-[0.18em] uppercase text-[#B3AC99]">
        Stillness · Notification Center
      </p>
    </div>
  );
}