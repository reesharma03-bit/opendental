import './_group.css';

import { useState } from 'react';
import {
  Activity, CalendarDays, Check, FileText, HeartPulse, Image, LockKeyhole,
  ShieldAlert, Stethoscope, Wallet,
} from 'lucide-react';

const tabs = [
  { id: 'profile', label: 'Profile', icon: Stethoscope },
  { id: 'timeline', label: 'Timeline', icon: CalendarDays },
  { id: 'history', label: 'Medical history', icon: HeartPulse },
  { id: 'plans', label: 'Treatment plans', icon: FileText },
  { id: 'notes', label: 'Clinical notes', icon: FileText },
  { id: 'images', label: 'Images', icon: Image },
  { id: 'invoices', label: 'Invoices', icon: Wallet },
  { id: 'insurance', label: 'Insurance', icon: ShieldAlert },
  { id: 'consents', label: 'Consent forms', icon: Check },
  { id: 'chart', label: 'Dental chart', icon: Activity },
] as const;

const quadrants = [
  { name: 'Upper right', teeth: ['18', '17', '16', '15', '14', '13', '12', '11'] },
  { name: 'Upper left', teeth: ['21', '22', '23', '24', '25', '26', '27', '28'] },
  { name: 'Lower right', teeth: ['48', '47', '46', '45', '44', '43', '42', '41'] },
  { name: 'Lower left', teeth: ['31', '32', '33', '34', '35', '36', '37', '38'] },
];

const sampleItems: Record<string, { title: string; note: string }[]> = {
  timeline: [
    { title: 'Example visit entry', note: 'Timeline layout preview · no visit data' },
    { title: 'Example record event', note: 'Display-only placeholder' },
  ],
  history: [
    { title: 'Medical history', note: 'No clinical information is displayed in this preview.' },
    { title: 'Alerts and sensitivities', note: 'Example interface section · no patient data' },
  ],
  plans: [
    { title: 'Treatment plan layout', note: 'Procedure and status fields shown as a UI example only.' },
  ],
  notes: [
    { title: 'Clinical note layout', note: 'No clinical note content is stored or displayed.' },
  ],
  images: [
    { title: 'Image library', note: 'Example image slots · no patient images are present.' },
  ],
  invoices: [
    { title: 'Invoice overview', note: 'Example billing interface · no balance or transaction data.' },
  ],
  insurance: [
    { title: 'Coverage details', note: 'Example insurance interface · no member information.' },
  ],
  consents: [
    { title: 'Consent documents', note: 'Example document list · no signed forms or records.' },
  ],
};

export default function PatientExperiencePreview() {
  const [activeTab, setActiveTab] = useState<(typeof tabs)[number]['id']>('profile');
  const [selectedTeeth, setSelectedTeeth] = useState<string[]>([]);

  const toggleTooth = (tooth: string) => {
    setSelectedTeeth((current) => current.includes(tooth)
      ? current.filter((item) => item !== tooth)
      : [...current, tooth]);
  };

  return (
    <section id="patient-experience-demo" className="mb-6 overflow-hidden rounded-xl border border-blue-200 bg-white shadow-[0_8px_24px_rgba(37,99,235,.08)]" data-testid="patient-experience-demo">
      <header className="flex flex-col gap-4 border-b border-blue-100 bg-blue-50/55 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <div>
          <div className="mb-1.5 flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-white px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-blue-800"><LockKeyhole size={12} /> Fictional patient · UI preview only</span>
            <span className="text-[11px] font-semibold text-slate-500">DEMO-ONLY</span>
          </div>
          <h2 className="text-xl font-bold tracking-tight text-slate-900">Patient experience preview</h2>
          <p className="mt-1 text-sm text-slate-600">An isolated example workspace. It is not linked to any patient record.</p>
        </div>
        <p className="max-w-sm rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-medium leading-5 text-amber-950">
          Fictional interface content only—not clinical data. Nothing here is saved or transmitted.
        </p>
      </header>

      <div className="grid min-w-0 lg:grid-cols-[224px_minmax(0,1fr)]">
        <aside className="border-b border-slate-200 bg-slate-50/70 p-4 lg:border-b-0 lg:border-r">
          <div className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-3">
            <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-800">TP</span>
            <div className="min-w-0">
              <p className="truncate text-sm font-bold text-slate-900">Taylor Preview</p>
              <p className="mt-0.5 text-xs text-slate-500">Fictional sample profile</p>
            </div>
          </div>
          <nav className="mt-4 grid grid-cols-2 gap-1.5 sm:grid-cols-3 lg:grid-cols-1" aria-label="Fictional patient preview sections">
            {tabs.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                type="button"
                onClick={() => setActiveTab(id)}
                aria-current={activeTab === id ? 'page' : undefined}
                data-testid={`button-demo-patient-tab-${id}`}
                className={`flex min-h-10 items-center gap-2 rounded-lg px-3 py-2 text-left text-xs font-semibold transition ${activeTab === id ? 'bg-blue-100 text-blue-900' : 'text-slate-600 hover:bg-white hover:text-slate-900'}`}
              >
                <Icon size={15} aria-hidden="true" className="shrink-0" />
                <span>{label}</span>
              </button>
            ))}
          </nav>
          <div className="mt-4 flex items-start gap-2 rounded-lg border border-slate-200 bg-white p-3 text-xs leading-5 text-slate-600">
            <ShieldAlert size={15} className="mt-0.5 shrink-0 text-blue-700" />
            No connection to directory records, Open Dental, or patient identifiers.
          </div>
        </aside>

        <div className="min-w-0 p-4 sm:p-6">
          <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-[11px] font-bold uppercase tracking-[.1em] text-blue-700">Fictional preview record</p>
              <h3 className="mt-1 text-lg font-bold text-slate-900">{tabs.find((tab) => tab.id === activeTab)?.label}</h3>
            </div>
            <span className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-medium text-slate-600">Unsaved · local preview</span>
          </div>

          {activeTab === 'profile' && (
            <div className="grid gap-4 xl:grid-cols-[minmax(0,1.25fr)_minmax(220px,.75fr)]">
              <section className="rounded-xl border border-slate-200 p-4 sm:p-5">
                <div className="flex items-center gap-3 border-b border-slate-100 pb-4">
                  <span className="flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 text-sm font-bold text-blue-800">TP</span>
                  <div><h4 className="font-semibold text-slate-900">Taylor Preview</h4><p className="text-xs text-slate-500">Fictional profile · no real patient ID</p></div>
                </div>
                <dl className="grid gap-x-6 gap-y-4 pt-4 sm:grid-cols-2">
                  <ProfileField label="Preferred name" value="Example only" />
                  <ProfileField label="Contact details" value="Not included in preview" />
                  <ProfileField label="Patient number" value="Not assigned" />
                  <ProfileField label="Record status" value="Unsaved demo" />
                </dl>
              </section>
              <section className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                <h4 className="text-sm font-semibold text-slate-900">Workspace sections</h4>
                <p className="mt-1 text-xs leading-5 text-slate-600">Use the preview navigation to explore the patient workspace. All sections are illustrative interface examples.</p>
                <div className="mt-4 flex items-center gap-2 rounded-lg border border-blue-100 bg-blue-50 px-3 py-2.5 text-xs font-medium text-blue-900"><LockKeyhole size={14} /> No clinical content or identifiers</div>
              </section>
            </div>
          )}

          {activeTab === 'chart' && (
            <section className="rounded-xl border border-slate-200 p-4 sm:p-5" aria-label="Interactive fictional dental chart">
              <div className="flex flex-col justify-between gap-2 sm:flex-row sm:items-center">
                <div><h4 className="font-semibold text-slate-900">Interactive chart preview</h4><p className="mt-1 text-xs text-slate-600">Select tooth markers to preview the chart interaction.</p></div>
                <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600"><span className="h-2.5 w-2.5 rounded-sm bg-blue-600" /> Local selection</span>
              </div>
              <div className="mt-5 grid gap-4 sm:grid-cols-2">
                {quadrants.map((quadrant) => (
                  <section key={quadrant.name} className="rounded-lg border border-slate-200 bg-slate-50/70 p-3">
                    <h5 className="mb-3 text-[11px] font-semibold uppercase tracking-wide text-slate-500">{quadrant.name}</h5>
                    <div className="grid grid-cols-4 gap-2">
                      {quadrant.teeth.map((tooth) => {
                        const selected = selectedTeeth.includes(tooth);
                        return (
                          <button
                            key={tooth}
                            type="button"
                            onClick={() => toggleTooth(tooth)}
                            aria-pressed={selected}
                            aria-label={`${selected ? 'Deselect' : 'Select'} tooth ${tooth} in local demo chart`}
                            data-testid={`button-demo-tooth-${tooth}`}
                            className={`flex min-h-12 flex-col items-center justify-center rounded-lg border text-xs font-semibold transition ${selected ? 'border-blue-700 bg-blue-600 text-white shadow-sm' : 'border-slate-200 bg-white text-slate-700 hover:border-blue-300 hover:bg-blue-50'}`}
                          >
                            <span className="mb-0.5 text-[9px] font-medium opacity-70">TOOTH</span>{tooth}
                          </button>
                        );
                      })}
                    </div>
                  </section>
                ))}
              </div>
              <div role="status" aria-live="polite" className="mt-4 rounded-lg border border-blue-100 bg-blue-50/70 px-3 py-2.5 text-xs leading-5 text-blue-950">
                {selectedTeeth.length ? `Selected locally: ${selectedTeeth.join(', ')}.` : 'No teeth selected.'} This selection stays in this preview and is never saved or transmitted.
              </div>
            </section>
          )}

          {activeTab !== 'profile' && activeTab !== 'chart' && (
            <section className="rounded-xl border border-slate-200" aria-live="polite">
              {(sampleItems[activeTab] ?? []).map((item) => (
                <div key={item.title} className="flex items-start gap-3 border-b border-slate-100 p-4 last:border-0">
                  <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-600"><FileText size={15} /></span>
                  <div><h4 className="text-sm font-semibold text-slate-900">{item.title}</h4><p className="mt-1 text-xs leading-5 text-slate-600">{item.note}</p></div>
                </div>
              ))}
              <p className="border-t border-slate-100 bg-slate-50/70 px-4 py-3 text-xs text-slate-600">Interface preview only · no patient data, clinical content, or saved records.</p>
            </section>
          )}
        </div>
      </div>
    </section>
  );
}

function ProfileField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className="mt-1 text-sm font-semibold text-slate-800">{value}</dd>
    </div>
  );
}