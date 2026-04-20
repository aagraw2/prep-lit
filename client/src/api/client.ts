import type { InterviewType, Session, SessionWithMessages, SdeRole } from '../types'

const BASE = ''

export async function createSession(type: InterviewType, role: SdeRole): Promise<Session> {
  const res = await fetch(`${BASE}/api/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ type, role }),
  })
  if (!res.ok) throw new Error(`createSession failed: ${res.status}`)
  return res.json()
}

export async function sendMessage(
  sessionId: string,
  content: string,
  onToken: (token: string) => void,
  onDone: () => void,
): Promise<void> {
  const res = await fetch(`${BASE}/api/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  })
  if (!res.ok) throw new Error(`sendMessage failed: ${res.status}`)
  if (!res.body) {
    onDone()
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5)
        if (data && data.trim() !== '[DONE]') {
          onToken(data)
        }
      }
    }
  }

  onDone()
}

export async function getSession(sessionId: string): Promise<SessionWithMessages> {
  const res = await fetch(`${BASE}/api/sessions/${sessionId}`)
  if (!res.ok) throw new Error(`getSession failed: ${res.status}`)
  return res.json()
}
