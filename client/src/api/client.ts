import type { InterviewType, Session, SessionWithMessages, SdeRole, FeedbackReport } from '../types'

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

export async function createSessionWithResume(
  type: InterviewType,
  role: SdeRole,
  resumeFile: File
): Promise<Session> {
  const formData = new FormData()
  formData.append('type', type)
  formData.append('role', role)
  formData.append('resume', resumeFile)

  const res = await fetch(`${BASE}/api/sessions/with-resume`, {
    method: 'POST',
    body: formData,
  })
  if (!res.ok) {
    const error = await res.text()
    throw new Error(`createSessionWithResume failed: ${res.status} - ${error}`)
  }
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

export async function listSessions(): Promise<Session[]> {
  const res = await fetch(`${BASE}/api/sessions`)
  if (!res.ok) throw new Error(`listSessions failed: ${res.status}`)
  return res.json()
}

export async function getSession(sessionId: string): Promise<SessionWithMessages> {
  const res = await fetch(`${BASE}/api/sessions/${sessionId}`)
  if (!res.ok) throw new Error(`getSession failed: ${res.status}`)
  return res.json()
}

export async function endInterview(sessionId: string): Promise<FeedbackReport> {
  const res = await fetch(`${BASE}/api/sessions/${sessionId}/end`, {
    method: 'POST',
  })
  if (!res.ok) throw new Error(`endInterview failed: ${res.status}`)
  return res.json()
}
