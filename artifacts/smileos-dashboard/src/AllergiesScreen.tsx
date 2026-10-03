import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  AlertTriangle, Check, ChevronDown, CircleAlert, Clock3, Edit2,
  LoaderCircle, Plus, Search, ShieldCheck, Trash2, X,
} from 'lucide-react';
import {
  createBackendAllergy, deleteBackendAllergy, listBackendAllergies,
  updateBackendAllergy, type AllergyDraft, type BackendAllergy,
} from './lib/backendAllergies';
import { backendAvailable } from './lib/backend';
import { listBackendPatients, type BackendPatient } from './lib/backendPatients';

const emptyDraft = (): AllergyDraft => ({
  description: '',
  reaction: '',
  dateAdverseReaction: '',
  isActive: true,
});

const fieldClass = 'mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70';

function patientName(patient: BackendPatient) {
  return patient.preferred || `${patient.firstName} ${patient.lastName}`.trim() || `Patient #${patient.patNum}`;
}

export default function AllergiesScreen() {
  const [patients, setPatients] = useState<BackendPatient[]>([]);
  const [selectedPatNum, setSelectedPatNum] = useState<number | null>(null);
  const [patientSearch, setPatientSearch] = useState('');
  const [allergies, setAllergies] = useState<BackendAllergy[]>([]);
  const [loadingPatients, setLoadingPatients] = useState(true);
  const [loadingAllergies, setLoadingAllergies] = useState(false);
  const [saving, setSaving] = useState(false);
  const [screenError, setScreenError] = useState('');
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [filter, setFilter] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<BackendAllergy | null>(null);
  const [draft, setDraft] = useState<AllergyDraft>(emptyDraft());

  useEffect(() => {
    let live = true;
    setLoadingPatients(true);
    listBackendPatients('')
      .then((rows) => {
        if (!live) return;
        setPatients(rows.filter((patient) => patient.patNum > 0));
        setScreenError('');
      })
      .catch((error: Error) => {
        if (live) setScreenError(`Unable to load patients: ${error.message}`);
      })
      .finally(() => { if (live) setLoadingPatients(false); });
    return () => { live = false; };
  }, []);

  useEffect(() => {
    if (!selectedPatNum || !backendAvailable()) {
      setAllergies([]);
      setLoadingAllergies(false);
      return;
    }
    let live = true;
    setLoadingAllergies(true);
    setActionError('');
    listBackendAllergies(selectedPatNum)
      .then((rows) => {
        if (!live) return;
        setAllergies(rows);
        setScreenError('');
      })
      .catch((error: Error) => {
        if (live) {
          setAllergies([]);
          setScreenError(`Unable to load allergies: ${error.message}`);
        }
      })
      .finally(() => { if (live) setLoadingAllergies(false); });
    return () => { live = false; };
  }, [selectedPatNum]);

  const filteredPatients = useMemo(() => {
    const term = patientSearch.trim().toLowerCase();
    if (!term) return patients;
    return patients.filter((patient) =>
      `${patientName(patient)} ${patient.patNum}`.toLowerCase().includes(term));
  }, [patients, patientSearch]);

  const filteredAllergies = useMemo(() => {
    const term = filter.trim().toLowerCase();
    if (!term) return allergies;
    return allergies.filter((allergy) =>
      `${allergy.description} ${allergy.reaction} ${allergy.snomedType}`.toLowerCase().includes(term));
  }, [allergies, filter]);

  const activeCount = allergies.filter((allergy) => allergy.isActive).length;
  const selectedPatient = patients.find((patient) => patient.patNum === selectedPatNum);

  const openCreate = () => {
    setEditing(null);
    setDraft(emptyDraft());
    setActionError('');
    setFormOpen(true);
  };

  const openEdit = (allergy: BackendAllergy) => {
    setEditing(allergy);
    setDraft({
      description: allergy.description,
      reaction: allergy.reaction,
      dateAdverseReaction: allergy.dateAdverseReaction,
      isActive: allergy.isActive,
    });
    setActionError('');
    setFormOpen(true);
  };

  const saveAllergy = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedPatNum) return;
    if (!draft.description.trim()) {
      setActionError('Enter an allergy or substance name.');
      return;
    }
    setSaving(true);
    setActionError('');
    try {
      if (editing) {
        await updateBackendAllergy(editing.allergyNum, draft);
        setNotice('Allergy record updated in Open Dental.');
      } else {
        await createBackendAllergy(selectedPatNum, draft);
        setNotice('Allergy added to Open Dental.');
      }
      setFormOpen(false);
      setAllergies(await listBackendAllergies(selectedPatNum));
      setScreenError('');
    } catch (error) {
      setActionError((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const removeAllergy = async (allergy: BackendAllergy) => {
    const confirmed = window.confirm(
      `Permanently delete the ${allergy.description || 'allergy'} record from Open Dental?`,
    );
    if (!confirmed) return;
    setSaving(true);
    setActionError('');
    try {
      await deleteBackendAllergy(allergy.allergyNum);
      setAllergies((current) => current.filter((item) => item.allergyNum !== allergy.allergyNum));
      setNotice('Allergy record deleted from Open Dental.');
      setScreenError('');
    } catch (error) {
      setActionError((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-allergies">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400">
            <AlertTriangle size={13} className="text-amber-500" /> PATIENT SAFETY
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-allergies-title">
            Allergies<span className="text-blue-600">.</span>
          </h1>
          <p className="mt-1.5 text-[12px] text-slate-500">Review and maintain patient allergy records in Open Dental.</p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          disabled={!selectedPatNum || loadingAllergies || saving}
          data-testid="button-create-allergy"
          className="flex h-10 items-center justify-center gap-2 self-start rounded-xl bg-[#315fe7] px-4 text-[12px] font-bold text-white shadow-[0_4px_12px_rgba(49,95,231,.18)] transition hover:bg-[#244fcf] disabled:cursor-not-allowed disabled:opacity-50 sm:self-auto"
        >
          <Plus size={16} /> Add allergy
        </button>
      </div>

      <div role="note" data-testid="notice-allergies-live-data" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950 sm:items-center">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700 sm:mt-0"><ShieldCheck size={17} /></span>
        <div className="min-w-0 flex-1">
          <p className="text-[12px] font-bold">Live patient data · Changes write to Open Dental</p>
          <p className="mt-0.5 text-[11px] leading-5 text-amber-900/75">Confirm the patient before adding, editing, or deleting an allergy. Deletion is permanent.</p>
        </div>
        <span className="hidden rounded-full border border-amber-300/80 px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.8px] text-amber-800 sm:inline-flex">Open Dental</span>
      </div>

      {screenError && (
        <div role="alert" data-testid="allergies-error" className="mb-4 flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700">
          <CircleAlert size={15} className="mt-0.5 shrink-0" /><span>{screenError}</span>
        </div>
      )}
      {notice && !screenError && (
        <div role="status" data-testid="allergies-success" className="mb-4 flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-[11px] font-semibold text-emerald-700">
          <Check size={15} />{notice}
        </div>
      )}

      <section className="mb-5 rounded-2xl border border-slate-200/75 bg-white p-5 shadow-[0_2px_10px_rgba(26,49,91,0.025)] sm:p-6" aria-labelledby="allergies-patient-label">
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(260px,1fr)] md:items-end">
          <div>
            <label id="allergies-patient-label" htmlFor="allergies-patient-search" className="text-[12px] font-bold text-slate-700">Find a patient</label>
            <div className="relative mt-1.5">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                id="allergies-patient-search"
                value={patientSearch}
                onChange={(event) => setPatientSearch(event.target.value)}
                placeholder="Search patient name or number"
                aria-label="Search patients for allergy records"
                data-testid="input-allergy-patient-search"
                className={`${fieldClass} mt-0 pl-9`}
              />
            </div>
          </div>
          <label className="text-[12px] font-bold text-slate-700" htmlFor="allergies-patient-select">
            Patient record
            <span className="relative mt-1.5 block">
              <select
                id="allergies-patient-select"
                value={selectedPatNum ?? ''}
                onChange={(event) => {
                  setSelectedPatNum(event.target.value ? Number(event.target.value) : null);
                  setNotice('');
                  setScreenError('');
                }}
                disabled={loadingPatients || patients.length === 0}
                data-testid="select-allergy-patient"
                className={`${fieldClass} appearance-none pr-9`}
              >
                <option value="">{loadingPatients ? 'Loading patients…' : patients.length ? 'Choose a patient' : 'No patients available'}</option>
                {filteredPatients.map((patient) => (
                  <option key={patient.patNum} value={patient.patNum}>
                    {patientName(patient)} · #{patient.patNum}
                  </option>
                ))}
              </select>
              <ChevronDown size={15} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" />
            </span>
          </label>
        </div>
        {selectedPatient && (
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-slate-100 pt-4 text-[11px]">
            <span className="font-bold text-slate-800">{patientName(selectedPatient)}</span>
            <span className="text-slate-400">Patient #{selectedPatient.patNum}</span>
            {selectedPatient.chartNumber && <span className="text-slate-400">Chart {selectedPatient.chartNumber}</span>}
          </div>
        )}
      </section>

      {selectedPatNum && (
        <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-allergy-records">
          <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Allergy records</h2>
                <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-allergy-count">{allergies.length}</span>
              </div>
              <p className="mt-1 text-[10px] text-slate-400">{activeCount} active · {allergies.length - activeCount} inactive</p>
            </div>
            <label className="relative block w-full sm:max-w-[310px]">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="Filter this patient's allergies" aria-label="Filter allergy records" data-testid="input-allergy-filter" className={`${fieldClass} mt-0 pl-9`} />
            </label>
          </div>

          {loadingAllergies ? (
            <div className="flex items-center justify-center gap-2 px-5 py-14 text-[12px] font-medium text-slate-500" data-testid="allergies-loading">
              <LoaderCircle size={17} className="animate-spin text-blue-600" /> Loading allergy records…
            </div>
          ) : allergies.length === 0 ? (
            <div className="px-5 py-14 text-center" data-testid="empty-allergies">
              <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><ShieldCheck size={19} /></span>
              <p className="mt-3 text-[12px] font-semibold text-slate-700">No allergy records for this patient</p>
              <p className="mt-1 text-[10px] text-slate-400">Add a record when the patient has a known allergy or adverse reaction.</p>
            </div>
          ) : filteredAllergies.length === 0 ? (
            <div className="px-5 py-12 text-center text-[11px] text-slate-500" data-testid="empty-allergy-filter">No allergies match “{filter}”.</div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] border-collapse text-left">
                  <thead className="bg-[#fbfcfe] text-[9px] font-bold uppercase tracking-[.8px] text-slate-400">
                    <tr>
                      <th className="px-5 py-3 sm:px-6">Allergy / substance</th>
                      <th className="px-4 py-3">Reaction</th>
                      <th className="px-4 py-3">Adverse reaction date</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-5 py-3 text-right sm:px-6">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredAllergies.map((allergy) => (
                      <tr key={allergy.allergyNum} data-testid={`row-allergy-${allergy.allergyNum}`} className="border-t border-slate-100 transition hover:bg-slate-50/60">
                        <td className="px-5 py-4 sm:px-6">
                          <p className="text-[12px] font-bold text-slate-800">{allergy.description || 'Unnamed allergy'}</p>
                          <p className="mt-1 text-[9px] text-slate-400">Allergy #{allergy.allergyNum}{allergy.allergyDefNum ? ` · Definition #${allergy.allergyDefNum}` : ''}</p>
                        </td>
                        <td className="px-4 py-4 text-[11px] text-slate-600">{allergy.reaction || 'Not recorded'}</td>
                        <td className="px-4 py-4 text-[10px] text-slate-500">{allergy.dateAdverseReaction || 'Not recorded'}</td>
                        <td className="px-4 py-4">
                          <span className={`inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[9px] font-bold ${allergy.isActive ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                            <span className={`h-1.5 w-1.5 rounded-full ${allergy.isActive ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                            {allergy.isActive ? 'Active' : 'Inactive'}
                          </span>
                        </td>
                        <td className="px-5 py-4 sm:px-6">
                          <div className="flex justify-end gap-1">
                            <button type="button" onClick={() => openEdit(allergy)} disabled={saving} aria-label={`Edit ${allergy.description} allergy`} data-testid={`button-edit-allergy-${allergy.allergyNum}`} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-blue-50 hover:text-blue-700 disabled:opacity-50"><Edit2 size={14} /></button>
                            <button type="button" onClick={() => void removeAllergy(allergy)} disabled={saving} aria-label={`Delete ${allergy.description} allergy`} data-testid={`button-delete-allergy-${allergy.allergyNum}`} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"><Trash2 size={14} /></button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex items-center justify-between border-t border-slate-100 px-5 py-3 text-[10px] text-slate-400 sm:px-6">
                <span>Showing {filteredAllergies.length} of {allergies.length} allergy records</span>
                {allergies.some((allergy) => allergy.dateTStamp) && <span className="inline-flex items-center gap-1"><Clock3 size={11} /> Synced from Open Dental</span>}
              </div>
            </>
          )}
        </section>
      )}

      {actionError && <div role="alert" data-testid="allergy-action-error" className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-[11px] font-medium text-rose-700">{actionError}</div>}

      {formOpen && (
        <div className="fixed inset-0 z-[90] flex items-center justify-center overflow-y-auto bg-slate-950/35 p-4 backdrop-blur-[2px]" data-testid="dialog-allergy-backdrop">
          <section role="dialog" aria-modal="true" aria-labelledby="allergy-form-title" data-testid="dialog-allergy-form" className="my-auto w-full max-w-[520px] rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl sm:p-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="mb-1 text-[10px] font-bold uppercase tracking-[1px] text-amber-600">Patient #{selectedPatNum}</p>
                <h2 id="allergy-form-title" className="font-[Manrope] text-[18px] font-extrabold text-slate-900">{editing ? 'Edit allergy' : 'Add allergy'}</h2>
                <p className="mt-1 text-[11px] text-slate-500">{editing ? 'Update the recorded reaction, date, or active status.' : 'Record an allergy or substance and any known reaction.'}</p>
              </div>
              <button type="button" onClick={() => setFormOpen(false)} aria-label="Close allergy form" data-testid="button-close-allergy-form" className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={17} /></button>
            </div>
            <form onSubmit={saveAllergy} className="mt-5 space-y-4">
              <label className="block text-[11px] font-bold text-slate-700" htmlFor="allergy-description">
                Allergy or substance <span className="text-rose-500">*</span>
                <input id="allergy-description" autoFocus={!editing} required maxLength={255} value={draft.description} onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))} disabled={Boolean(editing)} placeholder="e.g. Penicillin, Latex" data-testid="input-allergy-description" className={`${fieldClass} disabled:bg-slate-50 disabled:text-slate-500`} />
                {editing && <span className="mt-1 block text-[10px] font-normal text-slate-400">The Open Dental API does not allow changing the allergy definition on an existing record.</span>}
              </label>
              <label className="block text-[11px] font-bold text-slate-700" htmlFor="allergy-reaction">
                Reaction
                <textarea id="allergy-reaction" rows={3} maxLength={2000} value={draft.reaction} onChange={(event) => setDraft((current) => ({ ...current, reaction: event.target.value }))} placeholder="Describe the adverse reaction, if known" data-testid="input-allergy-reaction" className="mt-1.5 w-full resize-y rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-[12px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70" />
              </label>
              <label className="block text-[11px] font-bold text-slate-700" htmlFor="allergy-date">
                Date of adverse reaction
                <input id="allergy-date" type="date" value={draft.dateAdverseReaction} onChange={(event) => setDraft((current) => ({ ...current, dateAdverseReaction: event.target.value }))} data-testid="input-allergy-date" className={fieldClass} />
              </label>
              {editing && (
                <label className="flex cursor-pointer items-center gap-2.5 rounded-xl border border-slate-200 px-3 py-3 text-[11px] font-semibold text-slate-700">
                  <input type="checkbox" checked={draft.isActive} onChange={(event) => setDraft((current) => ({ ...current, isActive: event.target.checked }))} data-testid="checkbox-allergy-active" className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500" />
                  Active allergy
                </label>
              )}
              {actionError && <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-[10px] font-medium text-rose-700">{actionError}</p>}
              <div className="flex flex-col-reverse gap-2 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
                <button type="button" onClick={() => setFormOpen(false)} disabled={saving} className="h-10 rounded-xl border border-slate-200 px-4 text-[11px] font-semibold text-slate-600 transition hover:bg-slate-50 disabled:opacity-50">Cancel</button>
                <button type="submit" disabled={saving} data-testid="button-save-allergy" className="flex h-10 items-center justify-center gap-2 rounded-xl bg-[#315fe7] px-4 text-[11px] font-bold text-white transition hover:bg-[#244fcf] disabled:cursor-wait disabled:opacity-60">
                  {saving && <LoaderCircle size={14} className="animate-spin" />}
                  {saving ? 'Saving…' : editing ? 'Save changes' : 'Add allergy'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}