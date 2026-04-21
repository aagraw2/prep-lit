import type { Message } from '../types'

interface MessageListProps {
  messages: Pick<Message, 'role' | 'content'>[]
}

const colors = {
  primary: '#0f1117',
  secondary: '#1a1f2e',
  accent: '#4f8ef7',
  accentDark: '#3a6fd8',
  text: '#e8eaed',
  textMuted: '#8b95a8',
  border: '#2a3147',
  userBubble: '#1e2640',
}

// Lightbulb SVG icon for AI
const BulbIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path d="M9 21h6M12 3a6 6 0 0 1 6 6c0 2.22-1.21 4.16-3 5.2V17a1 1 0 0 1-1 1H10a1 1 0 0 1-1-1v-2.8C7.21 13.16 6 11.22 6 9a6 6 0 0 1 6-6z"
      stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
  </svg>
)

// Human outline SVG icon for user
const UserIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="12" cy="8" r="4" stroke="#8b95a8" strokeWidth="1.8"/>
    <path d="M4 20c0-4 3.58-7 8-7s8 3 8 7" stroke="#8b95a8" strokeWidth="1.8" strokeLinecap="round"/>
  </svg>
)

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
              flexShrink: 0,
              boxShadow: `0 2px 8px rgba(79, 142, 247, 0.3)`,
            }}>
              <BulbIcon />
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
              flexShrink: 0,
            }}>
              <UserIcon />
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
