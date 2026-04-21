import { FeedbackReport } from '../types'

interface FeedbackModalProps {
  feedback: FeedbackReport
  onClose: () => void
}

const colors = {
  primary: '#1a1d29',
  secondary: '#252936',
  accent: '#D4A574',
  accentDark: '#C89850',
  text: '#e8eaed',
  textMuted: '#9aa0a6',
  border: '#3c4043',
  success: '#81c995',
  error: '#f28b82',
  warning: '#fdd663',
}

export function FeedbackModal({ feedback, onClose }: FeedbackModalProps) {
  const verdictColors = {
    HIRE: { bg: 'rgba(129, 201, 149, 0.15)', text: colors.success, emoji: '✅' },
    BORDERLINE: { bg: 'rgba(253, 214, 99, 0.15)', text: colors.warning, emoji: '⚠️' },
    NO_HIRE: { bg: 'rgba(242, 139, 130, 0.15)', text: colors.error, emoji: '❌' },
  }

  const verdictColor = verdictColors[feedback.verdict]

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
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
        maxWidth: '700px',
        width: '100%',
        maxHeight: '90vh',
        overflow: 'auto',
        boxShadow: '0 25px 50px rgba(0, 0, 0, 0.5)',
        border: `1px solid ${colors.border}`,
      }}>
        {/* Header */}
        <div style={{
          padding: '32px',
          borderBottom: `1px solid ${colors.border}`,
        }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '20px',
          }}>
            <h2 style={{
              margin: 0,
              fontSize: '28px',
              fontWeight: '700',
              background: `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentDark} 100%)`,
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              letterSpacing: '-0.5px',
            }}>Interview Feedback</h2>
            <button
              onClick={onClose}
              style={{
                background: 'none',
                border: 'none',
                fontSize: '24px',
                cursor: 'pointer',
                padding: '4px',
                color: colors.textMuted,
                transition: 'color 0.2s',
              }}
              onMouseEnter={(e) => e.currentTarget.style.color = colors.text}
              onMouseLeave={(e) => e.currentTarget.style.color = colors.textMuted}
            >
              ✕
            </button>
          </div>

          {/* Verdict */}
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '10px',
            padding: '12px 24px',
            background: verdictColor.bg,
            color: verdictColor.text,
            borderRadius: '12px',
            fontSize: '18px',
            fontWeight: '600',
            border: `1px solid ${verdictColor.text}40`,
          }}>
            <span>{verdictColor.emoji}</span>
            <span>{feedback.verdict.replace('_', ' ')}</span>
          </div>
        </div>

        {/* Content */}
        <div style={{ padding: '32px' }}>
          {/* Summary */}
          <div style={{ marginBottom: '32px' }}>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: '18px',
              fontWeight: '600',
              color: colors.text,
            }}>Summary</h3>
            <p style={{
              margin: 0,
              fontSize: '15px',
              lineHeight: '1.6',
              color: colors.textMuted,
            }}>{feedback.summary}</p>
          </div>

          {/* Scores */}
          <div style={{ marginBottom: '32px' }}>
            <h3 style={{
              margin: '0 0 16px',
              fontSize: '18px',
              fontWeight: '600',
              color: colors.text,
            }}>Scores</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {[
                { label: 'Problem Understanding', value: feedback.scores.problemUnderstanding },
                { label: 'Approach', value: feedback.scores.approach },
                { label: 'Correctness', value: feedback.scores.correctness },
                { label: 'Communication', value: feedback.scores.communication },
                { label: 'Optimization', value: feedback.scores.optimization },
              ].map(score => (
                <div key={score.label}>
                  <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    marginBottom: '8px',
                    fontSize: '14px',
                  }}>
                    <span style={{ color: colors.textMuted }}>{score.label}</span>
                    <span style={{ fontWeight: '600', color: colors.text }}>{score.value}/100</span>
                  </div>
                  <div style={{
                    height: '8px',
                    background: colors.border,
                    borderRadius: '4px',
                    overflow: 'hidden',
                  }}>
                    <div style={{
                      width: `${score.value}%`,
                      height: '100%',
                      background: score.value >= 70 
                        ? `linear-gradient(90deg, ${colors.success}, ${colors.accent})` 
                        : score.value >= 50 
                          ? `linear-gradient(90deg, ${colors.warning}, ${colors.accent})` 
                          : `linear-gradient(90deg, ${colors.error}, ${colors.warning})`,
                      transition: 'width 0.3s ease',
                    }} />
                  </div>
                </div>
              ))}
              <div style={{
                marginTop: '8px',
                padding: '16px',
                background: colors.primary,
                borderRadius: '12px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                border: `1px solid ${colors.border}`,
              }}>
                <span style={{ fontSize: '16px', fontWeight: '600', color: colors.text }}>Total Score</span>
                <span style={{ 
                  fontSize: '24px', 
                  fontWeight: '700', 
                  background: `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentDark} 100%)`,
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                }}>{feedback.scores.total}/100</span>
              </div>
            </div>
          </div>

          {/* Strengths */}
          <div style={{ marginBottom: '32px' }}>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: '18px',
              fontWeight: '600',
              color: colors.text,
            }}>Strengths</h3>
            <ul style={{
              margin: 0,
              padding: '0 0 0 20px',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
            }}>
              {feedback.strengths.map((strength, i) => (
                <li key={i} style={{
                  fontSize: '15px',
                  lineHeight: '1.6',
                  color: colors.textMuted,
                }}>{strength}</li>
              ))}
            </ul>
          </div>

          {/* Weaknesses */}
          <div style={{ marginBottom: '32px' }}>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: '18px',
              fontWeight: '600',
              color: colors.text,
            }}>Areas for Improvement</h3>
            <ul style={{
              margin: 0,
              padding: '0 0 0 20px',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
            }}>
              {feedback.weaknesses.map((weakness, i) => (
                <li key={i} style={{
                  fontSize: '15px',
                  lineHeight: '1.6',
                  color: colors.textMuted,
                }}>{weakness}</li>
              ))}
            </ul>
          </div>

          {/* Next Steps */}
          <div>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: '18px',
              fontWeight: '600',
              color: colors.text,
            }}>Next Steps</h3>
            <ul style={{
              margin: 0,
              padding: '0 0 0 20px',
              display: 'flex',
              flexDirection: 'column',
              gap: '8px',
            }}>
              {feedback.nextSteps.map((step, i) => (
                <li key={i} style={{
                  fontSize: '15px',
                  lineHeight: '1.6',
                  color: colors.textMuted,
                }}>{step}</li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </div>
  )
}
