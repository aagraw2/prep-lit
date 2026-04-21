import type { Message } from '../types'

interface MessageListProps {
  messages: Pick<Message, 'role' | 'content'>[]
}

const colors = {
  primary: '#1a1d29',
  secondary: '#252936',
  accent: '#D4A574',
  accentDark: '#C89850',
  text: '#e8eaed',
  textMuted: '#9aa0a6',
  border: '#3c4043',
  userBubble: '#2d3142',
}

export function MessageList({ messages }: MessageListProps) {
  return (
    <div style={{
      flex: 1,
      overflowY: 'auto',
      padding: '24px',
      display: 'flex',
      flexDirection: 'column',
      gap: '16px',
      background: colors.primary,
    }}>
      {messages.length === 0 && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          color: colors.textMuted,
          fontSize: '15px',
          fontWeight: '500',
        }}>
          Initializing interview session...
        </div>
      )}
      {messages.map((msg, i) => (
        <div
          key={i}
          style={{
            display: 'flex',
            justifyContent: msg.role === 'USER' ? 'flex-end' : 'flex-start',
            alignItems: 'flex-end',
            gap: '10px',
          }}
        >
          {msg.role === 'ASSISTANT' && (
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              background: `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentDark} 100%)`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '16px',
              flexShrink: 0,
              boxShadow: `0 2px 8px rgba(212, 165, 116, 0.3)`,
            }}>
              🔥
            </div>
          )}
          <div
            style={{
              maxWidth: '70%',
              padding: '14px 18px',
              borderRadius: msg.role === 'USER'
                ? '16px 16px 4px 16px'
                : '16px 16px 16px 4px',
              background: msg.role === 'USER'
                ? colors.userBubble
                : colors.secondary,
              color: colors.text,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              fontSize: '15px',
              lineHeight: '1.6',
              boxShadow: '0 2px 8px rgba(0, 0, 0, 0.3)',
              border: `1px solid ${colors.border}`,
            }}
          >
            {msg.content}
          </div>
          {msg.role === 'USER' && (
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '50%',
              background: colors.border,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '16px',
              flexShrink: 0,
            }}>
              👤
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
