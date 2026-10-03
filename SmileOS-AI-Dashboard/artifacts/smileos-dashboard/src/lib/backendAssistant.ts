// AI Assistant: POST /api/assistant answers questions from our database (read-only tools).
// The conversation lives only in the open panel; it is never stored in the browser.
import { pick, pickNum, request, type Raw } from './backend';

export interface AssistantTurn { role: 'user' | 'assistant'; text: string }
export interface AssistantPatient { patNum: number; name: string }
export interface AssistantAnswer { answer: string; toolsUsed: string[]; patients: AssistantPatient[] }

export async function getAssistantStatus(): Promise<{ configured: boolean }> {
  const raw = await request<Raw>('/api/assistant/status');
  return { configured: raw.configured === true };
}

export async function askAssistant(messages: AssistantTurn[]): Promise<AssistantAnswer> {
  // The backend serialises with snake_case; accept either spelling.
  const raw = await request<Raw>('/api/assistant', { method: 'POST', body: JSON.stringify({ messages }) });
  const tools = (raw.tools_used ?? raw.toolsUsed ?? []) as string[];
  const patients = ((raw.patients ?? []) as Raw[]).map((p) => ({
    patNum: pickNum(p, 'pat_num', 'patNum') ?? 0,
    name: pick(p, 'name'),
  })).filter((p) => p.patNum !== 0);
  return { answer: pick(raw, 'answer'), toolsUsed: tools, patients };
}
