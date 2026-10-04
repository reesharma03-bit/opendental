import { Fragment, useEffect, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react';
import { ArrowUp, CircleAlert, LoaderCircle, RotateCcw, ShieldCheck, Sparkles, UserRound, X } from 'lucide-react';
import { askAssistant, getAssistantStatus, type AssistantPatient, type AssistantTurn } from './lib/backendAssistant';

type ChatTurn = AssistantTurn & { toolsUsed?: string[]; patients?: AssistantPatient[]; error?: boolean };

const SUGGESTIONS = [
  'Brief me on today',
  'Which of today’s patients have allergies?',
  'Show open insurance claims, oldest first',
  'How much did we collect this week vs last week?',
  'Is the sync with Open Dental healthy?',
];

const TOOL_LABELS: Record<string, string> = {
  get_practice_overview: 'practice overview',
  get_schedule: 'schedule',
  find_patients: 'patient search',
  get_patient_summary: 'patient record',
  find_patients_with_allergy: 'allergies',
  get_open_claims: 'insurance claims',
  get_collections: 'payments',
  get_sync_status: 'sync status',
  search_records: 'synced records',
};

/** **bold** inside a line. */
function inline(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) =>
    part.startsWith('**') && part.endsWith('**') && part.length > 4
      ? <strong key={i} className="font-bold text-slate-900">{part.slice(2, -2)}</strong>
      : <Fragment key={i}>{part.replace(/`([^`]+)`/g, '$1')}</Fragment>);
}

/** Small Markdown subset (paragraphs, headings, lists, tables) rendered as elements — never as raw HTML. */
function RichText({ text }: { text: string }) {
  const lines = text.split('\n');
  const blocks: ReactNode[] = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!line.trim()) continue;
    if (/^\s*\|.*\|\s*$/.test(line)) {
      const rows: string[][] = [];
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
        const cells = lines[i].trim().slice(1, -1).split('|').map((c) => c.trim());
        if (!cells.every((c) => /^:?-{2,}:?$/.test(c))) rows.push(cells);
        i++;
      }
      i--;
      const [head, ...body] = rows;
      blocks.push(
        <div key={`t${i}`} className="my-2 overflow-x-auto rounded-lg border border-slate-200">
          <table className="w-full border-collapse text-left text-[10.5px]">
            <thead><tr className="bg-slate-50">{head?.map((c, j) => <th key={j} className="px-2 py-1.5 font-bold text-slate-600">{inline(c)}</th>)}</tr></thead>
            <tbody>{body.map((r, k) => <tr key={k} className="border-t border-slate-100">{r.map((c, j) => <td key={j} className="px-2 py-1.5 text-slate-700">{inline(c)}</td>)}</tr>)}</tbody>
          </table>
        </div>,
      );
      continue;
    }
    if (/^\s*([-*•]|\d+[.)])\s+/.test(line)) {
      const ordered = /^\s*\d+[.)]/.test(line);
      const items: string[] = [];
      while (i < lines.length && /^\s*([-*•]|\d+[.)])\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*([-*•]|\d+[.)])\s+/, ''));
        i++;
      }
      i--;
      const List = ordered ? 'ol' : 'ul';
      blocks.push(<List key={`l${i}`} className={`my-1.5 space-y-1 pl-4 ${ordered ? 'list-decimal' : 'list-disc'}`}>{items.map((item, j) => <li key={j}>{inline(item)}</li>)}</List>);
      continue;
    }
    const heading = line.match(/^#{1,4}\s+(.*)$/);
    if (heading) {
      blocks.push(<p key={`h${i}`} className="mb-1 mt-2.5 text-[12px] font-extrabold text-slate-900">{inline(heading[1])}</p>);
      continue;
    }
    blocks.push(<p key={`p${i}`} className="my-1.5">{inline(line)}</p>);
  }
  return <div className="text-[11.5px] leading-[1.6] text-slate-700">{blocks}</div>;
}

export default function AssistantPanel({ open, onClose, onOpenPatient }: {
  open: boolean;
  onClose: () => void;
  onOpenPatient: (patient: AssistantPatient) => void;
}) {
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [configured, setConfigured] = useState<boolean | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!open) return;
    getAssistantStatus().then((s) => setConfigured(s.configured)).catch(() => setConfigured(null));
    window.setTimeout(() => inputRef.current?.focus(), 50);
  }, [open]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  }, [turns, busy]);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const send = async (question: string) => {
    const text = question.trim();
    if (!text || busy) return;
    const history: ChatTurn[] = [...turns, { role: 'user', text }];
    setTurns(history);
    setDraft('');
    setBusy(true);
    try {
      // Only answered turns go back as context; failed attempts are left out.
      const context = history.filter((t) => !t.error).map(({ role, text: t }) => ({ role, text: t }));
      const answer = await askAssistant(context);
      setTurns((current) => [...current, { role: 'assistant', text: answer.answer, toolsUsed: answer.toolsUsed, patients: answer.patients }]);
    } catch (e) {
      setTurns((current) => [...current, { role: 'assistant', text: (e as Error).message, error: true }]);
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  };

  const submit = (event: FormEvent) => { event.preventDefault(); void send(draft); };
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send(draft); }
  };

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[85] flex justify-end bg-slate-950/20 backdrop-blur-[1px]" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }} data-testid="panel-ai-assistant-overlay">
      <aside role="dialog" aria-modal="true" aria-labelledby="ai-assistant-title" className="flex h-full w-full max-w-[460px] flex-col border-l border-slate-200 bg-[#fbfcfe] shadow-2xl" data-testid="panel-ai-assistant">
        <header className="flex items-center gap-3 border-b border-slate-200/80 bg-white px-5 py-4">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#315fe7] to-[#4867d5] text-white"><Sparkles size={17} /></span>
          <div className="min-w-0 flex-1">
            <h2 id="ai-assistant-title" className="font-[Manrope] text-[15px] font-extrabold text-slate-900">AI Assistant</h2>
            <p className="text-[10px] text-slate-400">Answers from your practice database · read only</p>
          </div>
          {turns.length > 0 && <button type="button" onClick={() => setTurns([])} disabled={busy} aria-label="Start a new conversation" title="New conversation" data-testid="button-ai-reset" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 disabled:opacity-40"><RotateCcw size={15} /></button>}
          <button type="button" onClick={onClose} aria-label="Close AI Assistant" data-testid="button-ai-close" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={17} /></button>
        </header>

        <div ref={listRef} className="flex-1 space-y-4 overflow-y-auto px-5 py-5" aria-live="polite">
          {configured === false && (
            <div role="note" className="flex gap-2.5 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-3 text-[11px] leading-5 text-amber-900">
              <CircleAlert size={15} className="mt-0.5 shrink-0" />
              <p><b>Not set up yet.</b> Set <code className="rounded bg-amber-100 px-1">GROQ_API_KEY</code> on the backend and restart it. Only send patient data to the AI service under a signed BAA.</p>
            </div>
          )}
          {turns.length === 0 && (
            <div>
              <p className="text-[12px] font-semibold text-slate-700">Ask about your practice in plain English.</p>
              <p className="mt-1 text-[10.5px] leading-5 text-slate-500">Schedules, patients and allergies, insurance claims, payments, or why something hasn’t reached Open Dental.</p>
              <div className="mt-4 flex flex-col gap-2">
                {SUGGESTIONS.map((s) => (
                  <button key={s} type="button" onClick={() => void send(s)} disabled={busy || configured === false} data-testid={`button-ai-suggestion-${SUGGESTIONS.indexOf(s)}`} className="rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-left text-[11px] font-semibold text-slate-600 transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 disabled:opacity-50">{s}</button>
                ))}
              </div>
            </div>
          )}
          {turns.map((turn, index) => turn.role === 'user' ? (
            <div key={index} className="flex justify-end"><p className="max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-[#315fe7] px-3.5 py-2.5 text-[11.5px] leading-[1.55] text-white">{turn.text}</p></div>
          ) : (
            <div key={index} className={`rounded-2xl rounded-bl-md border px-3.5 py-3 ${turn.error ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-white'}`} data-testid={`ai-answer-${index}`}>
              {turn.error
                ? <p className="flex gap-2 text-[11px] text-rose-700"><CircleAlert size={14} className="mt-0.5 shrink-0" />{turn.text}</p>
                : <RichText text={turn.text} />}
              {!!turn.patients?.length && (
                <div className="mt-2.5 flex flex-wrap gap-1.5 border-t border-slate-100 pt-2.5">
                  {turn.patients.map((p) => (
                    <button key={p.patNum} type="button" onClick={() => onOpenPatient(p)} className="inline-flex items-center gap-1 rounded-lg bg-slate-50 px-2 py-1 text-[10px] font-semibold text-slate-600 hover:bg-blue-50 hover:text-blue-700"><UserRound size={11} />{p.name} #{p.patNum}</button>
                  ))}
                </div>
              )}
              {!!turn.toolsUsed?.length && <p className="mt-2 text-[9px] text-slate-400">Looked at: {turn.toolsUsed.map((t) => TOOL_LABELS[t] ?? t).join(', ')}</p>}
            </div>
          ))}
          {busy && <p className="flex items-center gap-2 text-[11px] text-slate-500"><LoaderCircle size={14} className="animate-spin text-blue-600" /> Looking it up…</p>}
        </div>

        <form onSubmit={submit} className="border-t border-slate-200/80 bg-white px-4 pb-4 pt-3">
          <div className="flex items-end gap-2 rounded-2xl border border-slate-200 bg-white px-3 py-2 focus-within:border-blue-300 focus-within:ring-4 focus-within:ring-blue-100/70">
            <label htmlFor="ai-question" className="sr-only">Ask the AI Assistant</label>
            <textarea id="ai-question" ref={inputRef} rows={1} value={draft} onChange={(e) => setDraft(e.target.value)} onKeyDown={onKeyDown} maxLength={4000} placeholder={configured === false ? 'Set up the assistant to ask questions' : 'Ask about today, a patient, claims…'} disabled={busy || configured === false} data-testid="input-ai-question" className="max-h-32 min-h-[22px] flex-1 resize-none bg-transparent text-[12px] text-slate-700 outline-none placeholder:text-slate-400 disabled:opacity-60" />
            <button type="submit" disabled={busy || !draft.trim() || configured === false} aria-label="Send question" data-testid="button-ai-send" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-[#315fe7] text-white transition hover:bg-[#244fcf] disabled:opacity-40"><ArrowUp size={15} /></button>
          </div>
          <p className="mt-2 flex items-center gap-1.5 text-[9px] leading-4 text-slate-400"><ShieldCheck size={11} className="shrink-0 text-emerald-600" />Read only — it can’t change records. Check answers before acting on them.</p>
        </form>
      </aside>
    </div>
  );
}
