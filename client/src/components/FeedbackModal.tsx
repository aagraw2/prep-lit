import { FeedbackReport } from '../types'

interface FeedbackModalProps {
  feedback: FeedbackReport
  onClose: () => void
}

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

export function FeedbackModal({ feedback, onClose }: FeedbackModalProps) {
  const verdictConfig = {
    HIRE:       { bg: 'rgba(74, 222, 128, 0.12)', text: colors.success, border: 'rgba(74, 222, 128, 0.3)',  label: '✓  HIRE' },
    BORDERLINE: { bg: 'rgba(250, 204, 21, 0.12)',  text: colors.warning, border: 'rgba(250, 204, 21, 0.3)',  label: '~  BORDERLINE' },
    NO_HIRE:    { bg: 'rgba(248, 113, 113, 0.12)', text: colors.error,   border: 'rgba(248, 113, 113, 0.3)', label: '✕  NO HIRE' },
  }

  const vc = verdictConfig[feedback.verdict]

  const scoreColor = (v: number) =>
    v >= 70 ? colors.success : v >= 50 ? colors.accent : colors.error

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0, 0, 0, 0.85)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px',
      zIndex: 1000,
      backdropFilter: 'blur(4px)',
    }}>
      <div style={{
        background: colors.secondary,
        borderRadius: '20px',
        maxWidth: '680px',
        width: '100%',
        maxHeight: '90vh',
        overflow: 'auto',
        boxShadow: '0 25px 50px rgba(0, 0, 0, 0.6)',
        border: `1px solid ${colors.border}`,
      }}>
        {/* Header */}
        <div style={{
          padding: '28px 32px 24px',
          borderBottom: `1px solid ${colors.border}`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <h2 style={{
            margin: 0,
            fontSize: '24px',
            fontWeight: '700',
            color: colors.text,
          }}>Interview Feedback</h2>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '20px',
              cursor: 'pointer',
              color: colors.textMuted,
              lineHeight: 1,
              padding: '4px 8px',
              borderRadius: '6px',
              transition: 'color 0.2s',
            }}
            onMouseEnter={(e) => e.currentTarget.style.color = colors.text}
            onMouseLeave={(e) => e.currentTarget.style.color = colors.textMuted}
          >✕</button>
        </div>

        <div style={{ padding: '28px 32px', display: 'flex', flexDirection: 'column', gap: '28px' }}>

          {/* Verdict */}
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            padding: '10px 20px',
            background: vc.bg,
            color: vc.text,
            borderRadius: '10px',
            fontSize: '15px',
            fontWeight: '700',
            letterSpacing: '0.5px',
            border: `1px solid ${vc.border}`,
            alignSelf: 'flex-start',
          }}>
            {vc.label}
          </div>

          {/* Summary */}
          <div>
            <h3 style={{ margin: '0 0 10px', fontSize: '16px', fontWeight: '600', color: colors.text }}>Summary</h3>
            <p style={{ margin: 0, fontSize: '14px', lineHeight: '1.7', color: colors.textMuted }}>{feedback.summary}</p>
          </div>

          {/* Scores */}
          <div>
            <h3 style={{ margin: '0 0 16px', fontSize: '16px', fontWeight: '600', color: colors.text }}>Scores</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {[
                { label: 'Problem Understanding', value: feedback.scores.problemUnderstanding },
                { label: 'Approach',               value: feedback.scores.approach },
                { label: 'Correctness',            value: feedback.scores.correctness },
                { label: 'Communication',          value: feedback.scores.communication },
                { label: 'Optimization',           value: feedback.scores.optimization },
              ].map(s => (
                <div key={s.label}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px', fontSize: '13px' }}>
                    <span style={{ color: colors.textMuted }}>{s.label}</span>
                    <span style={{ fontWeight: '600', color: scoreColor(s.value) }}>{s.value}/100</span>
                  </div>
                  <div style={{ height: '6px', background: colors.border, borderRadius: '3px', overflow: 'hidden' }}>
                    <div style={{
                      width: `${s.value}%`,
                      height: '100%',
                      background: scoreColor(s.value),
                      borderRadius: '3px',
                      transition: 'width 0.4s ease',
                    }} />
                  </div>
                </div>
              ))}

              {/* Total */}
              <div style={{
                marginTop: '8px',
                padding: '14px 18px',
                background: colors.primary,
                borderRadius: '10px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                border: `1px solid ${colors.border}`,
              }}>
                <span style={{ fontSize: '15px', fontWeight: '600', color: colors.text }}>Total Score</span>
                <span style={{
                  fontSize: '22px',
                  fontWeight: '700',
                  color: scoreColor(feedback.scores.total),
                }}>{feedback.scores.total}/100</span>
              </div>
            </div>
          </div>

          {/* Strengths */}
          <div>
            <h3 style={{ margin: '0 0 10px', fontSize: '16px', fontWeight: '600', color: colors.text }}>Strengths</h3>
            <ul style={{ margin: 0, padding: '0 0 0 18px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {feedback.strengths.map((s, i) => (
                <li key={i} style={{ fontSize: '14px', lineHeight: '1.6', color: colors.textMuted }}>{s}</li>
              ))}
            </ul>
          </div>

          {/* Weaknesses */}
          <div>
            <h3 style={{ margin: '0 0 10px', fontSize: '16px', fontWeight: '600', color: colors.text }}>Areas for Improvement</h3>
            <ul style={{ margin: 0, padding: '0 0 0 18px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {feedback.weaknesses.map((w, i) => (
                <li key={i} style={{ fontSize: '14px', lineHeight: '1.6', color: colors.textMuted }}>{w}</li>
              ))}
            </ul>
          </div>

          {/* Next Steps */}
          <div>
            <h3 style={{ margin: '0 0 10px', fontSize: '16px', fontWeight: '600', color: colors.text }}>Next Steps</h3>
            <ul style={{ margin: 0, padding: '0 0 0 18px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {feedback.nextSteps.map((s, i) => (
                <li key={i} style={{ fontSize: '14px', lineHeight: '1.6', color: colors.textMuted }}>{s}</li>
              ))}
            </ul>
          </div>

        </div>
      </div>
    </div>
  )
}
