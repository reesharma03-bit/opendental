import { useEffect, useMemo, useState } from 'react';
import { BookOpen, ExternalLink, Search, ShieldAlert, X } from 'lucide-react';
import { API_SPEC_URL, apiResources } from './apiResources';

export default function ApiCatalogScreen({ selectedResource = null }: { selectedResource?: string | null }) {
  const [query, setQuery] = useState(selectedResource ?? '');
  useEffect(() => setQuery(selectedResource ?? ''), [selectedResource]);
  const filteredResources = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return apiResources.filter((resource) => resource.name.toLowerCase().includes(normalized));
  }, [query]);
  const groupedResources = useMemo(() => filteredResources.reduce<Record<string, typeof filteredResources>>(
    (groups, resource) => {
      const initial = resource.name[0].toUpperCase();
      (groups[initial] ??= []).push(resource);
      return groups;
    },
    {},
  ), [filteredResources]);

  return (
    <div className="mx-auto max-w-[1500px] px-4 pb-10 pt-7 sm:px-6 lg:px-9" data-testid="screen-api-catalog">
      <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p className="mb-1.5 flex items-center gap-2 text-[11px] font-semibold text-slate-400">
            <BookOpen size={13} className="text-blue-500" /> DEVELOPER REFERENCE
          </p>
          <h1 className="font-[Manrope] text-[25px] font-extrabold tracking-[-1px] text-slate-900 sm:text-[29px]" data-testid="text-api-catalog-title">
            API Catalog<span className="text-blue-600">.</span>
          </h1>
          <p className="mt-1.5 text-[12px] text-slate-500">Browse official OpenDental API resource documentation.</p>
        </div>
        <a
          href={API_SPEC_URL}
          target="_blank"
          rel="noreferrer"
          data-testid="link-full-api-specification"
          className="inline-flex h-10 items-center justify-center gap-2 self-start rounded-xl border border-slate-200 bg-white px-4 text-[11px] font-bold text-slate-700 shadow-sm transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-700 sm:self-auto"
        >
          Full API specification <ExternalLink size={14} />
        </a>
      </div>

      <div role="note" data-testid="notice-api-reference-only" className="mb-5 flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-[#fff8e9] px-4 py-3.5 text-amber-950 sm:items-center">
        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-700 sm:mt-0">
          <ShieldAlert size={17} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-[12px] font-bold">Reference only · Not connected to OpenDental</p>
          <p className="mt-0.5 text-[11px] leading-5 text-amber-900/75">These links open official documentation in a new tab. This catalog makes no API requests and does not connect to a practice.</p>
        </div>
        <span className="hidden rounded-full border border-amber-300/80 px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.8px] text-amber-800 sm:inline-flex">Docs only</span>
      </div>

      <section className="overflow-hidden rounded-2xl border border-slate-200/75 bg-white shadow-[0_2px_10px_rgba(26,49,91,0.025)]" data-testid="section-api-resource-directory">
        <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <div className="flex items-center gap-2">
              <h2 className="font-[Manrope] text-[15px] font-extrabold tracking-[-.3px] text-slate-800">Resources</h2>
              <span className="rounded-md bg-blue-50 px-1.5 py-0.5 text-[9px] font-bold text-blue-700" data-testid="text-api-resource-count">{filteredResources.length} / {apiResources.length}</span>
            </div>
            <p className="mt-1 text-[10px] text-slate-400">Alphabetical directory · official resource references</p>
          </div>
          <label className="relative block w-full sm:max-w-[340px]">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search resource names"
              aria-label="Search API resources"
              aria-controls="api-resource-results"
              data-testid="input-api-resource-search"
              className="h-10 w-full rounded-xl border border-slate-200 bg-[#fbfcfe] pl-9 pr-9 text-[11px] text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-300 focus:ring-4 focus:ring-blue-100/70"
            />
            {query && (
              <button type="button" onClick={() => setQuery('')} aria-label="Clear API resource search" data-testid="button-clear-api-search" className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-md text-slate-400 hover:bg-slate-100">
                <X size={13} />
              </button>
            )}
          </label>
        </div>
        <div id="api-resource-results" aria-live="polite" className="px-5 py-5 sm:px-6">
          {filteredResources.length > 0 ? (
            <div className="grid grid-cols-1 gap-x-10 gap-y-7 md:grid-cols-2 xl:grid-cols-3">
              {Object.entries(groupedResources).map(([letter, resources]) => (
                <section key={letter} aria-labelledby={`api-group-${letter}`} data-testid={`group-api-resources-${letter}`}>
                  <h3 id={`api-group-${letter}`} className="mb-2 border-b border-slate-100 pb-2 font-[Manrope] text-[12px] font-extrabold text-blue-700">{letter}</h3>
                  <ul className="space-y-1">
                    {resources.map((resource) => (
                      <li key={resource.name}>
                        <a
                          href={resource.url}
                          target="_blank"
                          rel="noreferrer"
                          data-testid={`link-api-resource-${resource.name}`}
                          className="group flex min-h-10 items-center justify-between gap-3 rounded-lg px-2.5 text-[11px] font-semibold text-slate-600 transition hover:bg-blue-50 hover:text-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
                        >
                          <span>{resource.name}</span>
                          <ExternalLink size={13} aria-hidden="true" className="shrink-0 text-slate-300 transition group-hover:text-blue-500" />
                        </a>
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          ) : (
            <div className="py-12 text-center" data-testid="empty-api-resource-results">
              <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-xl bg-slate-100 text-slate-400"><Search size={17} /></span>
              <p className="mt-3 text-[12px] font-semibold text-slate-600">No matching resources</p>
              <p className="mt-1 text-[10px] text-slate-400">Try a different resource name.</p>
              <button type="button" onClick={() => setQuery('')} data-testid="button-reset-api-search" className="mt-3 rounded-lg px-3 py-2 text-[10px] font-bold text-blue-700 hover:bg-blue-50">Show all resources</button>
            </div>
          )}
        </div>
        <div className="flex flex-col gap-2 border-t border-slate-100 px-5 py-3 text-[9px] text-slate-400 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span data-testid="text-api-catalog-summary">Showing {filteredResources.length} official resource {filteredResources.length === 1 ? 'link' : 'links'}</span>
          <a href={API_SPEC_URL} target="_blank" rel="noreferrer" data-testid="link-api-specification-footer" className="inline-flex items-center gap-1 font-semibold text-blue-600 hover:text-blue-800">View the complete specification <ExternalLink size={11} /></a>
        </div>
      </section>
    </div>
  );
}