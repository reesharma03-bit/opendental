import { useCallback, useEffect, useState } from 'react';
import { CircleAlert, LoaderCircle } from 'lucide-react';
import ResourceScreen from './ResourceScreen';
import { databaseMeta, getCapabilities, loadSample } from './lib/databaseResources';
import type { ResourceMeta } from './lib/resourceMeta';

/**
 * An API Catalog screen backed by our database: works out what the resource may change
 * and which fields it has, then hands over to the generic ResourceScreen.
 */
export default function DatabaseResourceScreen({ name }: { name: string }) {
  const [meta, setMeta] = useState<ResourceMeta | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    let live = true;
    setMeta(null);
    setError('');
    Promise.all([getCapabilities(), loadSample(name)])
      .then(([capabilities, sample]) => {
        if (!live) return;
        const capability = capabilities.get(name.toLowerCase());
        if (!capability) {
          setError(`${name} is not stored in our database.`);
          return;
        }
        setMeta(databaseMeta(name, capability, sample));
      })
      .catch((e: Error) => { if (live) setError(e.message); });
    return () => { live = false; };
  }, [name]);

  useEffect(() => load(), [load]);

  if (error) {
    return (
      <div className="mx-auto max-w-[1500px] px-4 pt-7 sm:px-6 lg:px-9" data-testid={`screen-database-${name}`}>
        <div role="alert" className="flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3.5 text-[12px] text-rose-700">
          <CircleAlert size={16} className="mt-0.5 shrink-0" />
          <div className="flex-1">
            <p className="font-bold">Could not open {name}</p>
            <p className="mt-0.5">{error}</p>
          </div>
          <button type="button" onClick={load} className="rounded-lg border border-rose-300 px-3 py-1.5 text-[11px] font-bold hover:bg-rose-100">Retry</button>
        </div>
      </div>
    );
  }
  if (!meta) {
    return (
      <div className="flex items-center gap-2 px-9 pt-10 text-[12px] text-slate-500" data-testid={`screen-database-${name}`}>
        <LoaderCircle size={16} className="animate-spin text-blue-600" /> Loading {name}…
      </div>
    );
  }
  return <ResourceScreen key={name} resource={meta} />;
}
