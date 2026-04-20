import type { Message } from '../types'

interface MessageListProps {
  messages: Pick<Message, 'role' | 'content'>[]
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
      background: '#f3f4f6',
    }}>
      {messages.length === 0 && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100%',
          color: '#9ca3af',
          fontSize: '15px',
        }}>
          Starting your interview session...
        </div>
      )}
      {messages.map((msg, i) => (
        <div
          key={i}
          style={{
            display: 'flex',
            justifyContent: msg.role === 'USER' ? 'flex-end' : 'flex-start',
            alignItems: 'flex-end',
            gap: '8px',
          }}
        >
          {msg.role === 'ASSISTANT' && (
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '14px',
              flexShrink: 0,
            }}>
              🤖
            </div>
          )}
          <div
            style={{
              maxWidth: '70%',
              padding: '14px 18px',
              borderRadius: msg.role === 'USER'
                ? '20px 20px 4px 20px'
                : '20px 20px 20px 4px',
              background: msg.role === 'USER'
                ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
                : 'white',
              color: msg.role === 'USER' ? 'white' : '#1a1a2e',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              fontSize: '15px',
              lineHeight: '1.5',
              boxShadow: msg.role === 'USER'
                ? '0 4px 14px 0 rgba(102, 126, 234, 0.3)'
                : '0 2px 8px rgba(0, 0, 0, 0.08)',
            }}
          >
            {msg.content}
          </div>
          {msg.role === 'USER' && (
            <div style={{
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              background: '#e5e7eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '14px',
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
