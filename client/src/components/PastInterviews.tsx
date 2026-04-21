import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { listSessions, getSession } from '../api/client'
import { Session, SessionWithMessages, FeedbackReport } from '../types'
import { MessageList } from './MessageList'

const colors = {
  primary: '#0f1117',
  secondary: '#1a1f2e',
  accent: '#4f8ef7',
  accentDark: '#3a6fd8',
  text: '#e8eaed',
  textMuted: '#8b95a8',
  border: '#2a3147',
  success: '#4ade80',
  error: '#f87171',
  warning: '#facc15',
}

const typeLabels: Record<string, string> = {
  DSA: 'DSA',
  HLD: 'High Level Design',
  LLD: 'Low Level Design',
  RESUME_GRILLING: 'Resume Deep Dive',
  CULTURE_FIT: 'Culture Fit',
}

const roleLabels: Record<string, string> = {
  SDE1: 'Junior',
  SDE2: 'Mid-Level',
  SDE3: 'Senior',
}

const verdictConfig = {
  HIRE:       { color: '#4ade80', label: 'HIRE' },
  BORDERLINE: { color: '#facc15', label: 'BORDERLINE' },
  NO_HIRE:    { color: '#f87171', label: 'NO HIRE' },
}

function formatDate(iso: string) {
  const d = new Date(iso)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) +
    ' · ' + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
}

function scoreColor(v: number) {
  return v >= 70 ? colors.success : v >= 50 ? colors.accent : colors.error
}

function ScoreBar({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px', fontSize: '12px' }}>
        <span style={{ color: colors.textMuted }}>{label}</span>
        <span style={{ fontWeight: '600', color: scoreColor(value) }}>{value}/100</span>
      </div>
      <div style={{ height: '5px', background: colors.border, borderRadius: '3px', overflow: 'hidden' }}>
        <div style={{ width: `${value}%`, height: '100%', background: scoreColor(value), borderRadius: '3px' }} />
      </div>
    </div>
  )
}

function FeedbackPanel({ feedback }: { feedback: FeedbackReport }) {
  const vc = verdictConfig[feedback.verdict]
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Verdict + total */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <span style={{
          padding: '4px 14px', borderRadius: '20px', fontSize: '12px', fontWeight: '700',
          background: `${vc.color}18`, color: vc.color, border: `1px solid ${vc.color}40`,
        }}>{vc.label}</span>
        <span style={{ fontSize: '22px', fontWeight: '700', color: scoreColor(feedback.scores.total) }}>
          {feedback.scores.total}/100
        </span>
      </div>

      {/* Score bars */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <ScoreBar label="Problem Understanding" value={feedback.scores.problemUnderstanding} />
        <ScoreBar label="Approach"              value={feedback.scores.approach} />
        <ScoreBar label="Correctness"           value={feedback.scores.correctness} />
        <ScoreBar label="Communication"         value={feedback.scores.communication} />
        <ScoreBar label="Optimization"          value={feedback.scores.optimization} />
      </div>

      {/* Summary */}
      <div>
        <div style={{ fontSize: '13px', fontWeight: '600', color: colors.text, marginBottom: '6px' }}>Summary</div>
        <p style={{ margin: 0, fontSize: '13px', lineHeight: '1.6', color: colors.textMuted }}>{feedback.summary}</p>
      </div>

      {/* Strengths */}
      <div>
        <div style={{ fontSize: '13px', fontWeight: '600', color: colors.text, marginBottom: '6px' }}>Strengths</div>
        <ul style={{ margin: 0, padding: '0 0 0 16px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {feedback.strengths.map((s, i) => (
            <li key={i} style={{ fontSize: '13px', color: colors.textMuted }}>{s}</li>
          ))}
        </ul>
      </div>

      {/* Areas for improvement */}
      <div>
        <div style={{ fontSize: '13px', fontWeight: '600', color: colors.text, marginBottom: '6px' }}>Areas for Improvement</div>
        <ul style={{ margin: 0, padding: '0 0 0 16px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {feedback.weaknesses.map((w, i) => (
            <li key={i} style={{ fontSize: '13px', color: colors.textMuted }}>{w}</li>
          ))}
        </ul>
      </div>

      {/* Next steps */}
      <div>
        <div style={{ fontSize: '13px', fontWeight: '600', color: colors.text, marginBottom: '6px' }}>Next Steps</div>
        <ul style={{ margin: 0, padding: '0 0 0 16px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {feedback.nextSteps.map((s, i) => (
            <li key={i} style={{ fontSize: '13px', color: colors.textMuted }}>{s}</li>
          ))}
        </ul>
      </div>
    </div>
  )
}

export function PastInterviews() {
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<Session[]>([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<SessionWithMessages | null>(null)
  const [loadingSession, setLoadingSession] = useState(false)
  const [activeTab, setActiveTab] = useState<'conversation' | 'feedback'>('conversation')

  useEffect(() => {
    listSessions().then(setSessions).finally(() => setLoading(false))
  }, [])

  const handleSelect = async (id: string) => {
    setLoadingSession(true)
    setActiveTab('conversation')
    try {
      const s = await getSession(id)
      setSelected(s)
    } finally {
      setLoadingSession(false)
    }
  }

  return (
    <div style={{ display: 'flex', height: '100vh', background: colors.primary, color: colors.text, fontFamily: 'system-ui, sans-serif' }}>

      {/* Sidebar */}
      <div style={{ width: '300px', flexShrink: 0, borderRight: `1px solid ${colors.border}`, display: 'flex', flexDirection: 'column', background: colors.secondary }}>
        <div style={{ padding: '16px 20px', borderBottom: `1px solid ${colors.border}`, display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => navigate('/')}
            style={{ background: 'none', border: 'none', color: colors.textMuted, cursor: 'pointer', fontSize: '18px', padding: '2px 6px', borderRadius: '6px', transition: 'color 0.2s' }}
            onMouseEnter={e => e.currentTarget.style.color = colors.text}
            onMouseLeave={e => e.currentTarget.style.color = colors.textMuted}
          >←</button>
          <h2 style={{ margin: 0, fontSize: '15px', fontWeight: '600', color: colors.text }}>Past Interviews</h2>
        </div>

        <div style={{ flex: 1, overflowY: 'auto' }}>
          {loading && <div style={{ padding: '24px', color: colors.textMuted, fontSize: '13px', textAlign: 'center' }}>Loading...</div>}
          {!loading && sessions.length === 0 && <div style={{ padding: '24px', color: colors.textMuted, fontSize: '13px', textAlign: 'center' }}>No past interviews yet.</div>}
          {sessions.map(s => {
            const isActive = selected?.id === s.id
            const vc = s.feedback ? verdictConfig[s.feedback.verdict] : null
            return (
              <div
                key={s.id}
                onClick={() => handleSelect(s.id)}
                style={{
                  padding: '14px 20px',
                  borderBottom: `1px solid ${colors.border}`,
                  cursor: 'pointer',
                  background: isActive ? `${colors.accent}15` : 'transparent',
                  borderLeft: isActive ? `3px solid ${colors.accent}` : '3px solid transparent',
                  transition: 'background 0.15s',
                }}
                onMouseEnter={e => { if (!isActive) e.currentTarget.style.background = `${colors.accent}0a` }}
                onMouseLeave={e => { if (!isActive) e.currentTarget.style.background = 'transparent' }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                  <span style={{ fontSize: '13px', fontWeight: '600', color: colors.text }}>{typeLabels[s.type] ?? s.type}</span>
                  {vc && (
                    <span style={{ fontSize: '10px', fontWeight: '700', color: vc.color, padding: '2px 7px', borderRadius: '10px', background: `${vc.color}18`, border: `1px solid ${vc.color}30` }}>
                      {vc.label}
                    </span>
                  )}
                </div>
                <div style={{ fontSize: '11px', color: colors.textMuted }}>{roleLabels[s.role] ?? s.role}</div>
                {s.feedback && (
                  <div style={{ fontSize: '11px', color: scoreColor(s.feedback.scores.total), marginTop: '3px', fontWeight: '600' }}>
                    {s.feedback.scores.total}/100
                  </div>
                )}
                <div style={{ fontSize: '11px', color: colors.textMuted, marginTop: '3px' }}>
                  {s.createdAt ? formatDate(s.createdAt) : ''}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Main panel */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {!selected && !loadingSession && (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: colors.textMuted, fontSize: '14px' }}>
            Select an interview to view
          </div>
        )}
        {loadingSession && (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: colors.textMuted, fontSize: '14px' }}>
            Loading...
          </div>
        )}
        {selected && !loadingSession && (
          <>
            {/* Header + tabs */}
            <div style={{ padding: '14px 24px', borderBottom: `1px solid ${colors.border}`, background: colors.secondary }}>
              <div style={{ fontSize: '14px', fontWeight: '600', color: colors.text, marginBottom: '2px' }}>
                {typeLabels[selected.type] ?? selected.type}
              </div>
              <div style={{ fontSize: '11px', color: colors.textMuted, marginBottom: '12px' }}>
                {roleLabels[selected.role] ?? selected.role}
                {selected.createdAt ? ` · ${formatDate(selected.createdAt)}` : ''}
                {` · ${selected.messages.filter(m => m.role !== 'SYSTEM').length} messages`}
              </div>
              {/* Tabs */}
              <div style={{ display: 'flex', gap: '4px' }}>
                {(['conversation', 'feedback'] as const).map(tab => (
                  <button
                    key={tab}
                    onClick={() => setActiveTab(tab)}
                    disabled={tab === 'feedback' && !selected.feedback}
                    style={{
                      padding: '6px 16px',
                      fontSize: '12px',
                      fontWeight: '600',
                      border: 'none',
                      borderRadius: '6px',
                      cursor: tab === 'feedback' && !selected.feedback ? 'not-allowed' : 'pointer',
                      background: activeTab === tab ? colors.accent : 'transparent',
                      color: activeTab === tab ? colors.primary : tab === 'feedback' && !selected.feedback ? colors.border : colors.textMuted,
                      transition: 'all 0.15s',
                      textTransform: 'capitalize',
                    }}
                  >
                    {tab}{tab === 'feedback' && !selected.feedback ? ' (none)' : ''}
                  </button>
                ))}
              </div>
            </div>

            {/* Tab content */}
            {activeTab === 'conversation' && (
              <MessageList messages={selected.messages.filter(m => m.role === 'USER' || m.role === 'ASSISTANT')} />
            )}
            {activeTab === 'feedback' && selected.feedback && (
              <div style={{ flex: 1, overflowY: 'auto', padding: '24px' }}>
                <FeedbackPanel feedback={selected.feedback} />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
