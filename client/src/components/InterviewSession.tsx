import { useState, useRef, useEffect } from 'react'
import { InterviewType, SdeRole } from '../types'
import { createSession, sendMessage, getSession } from '../api/client'
import { useTextToSpeech } from '../hooks/useTextToSpeech'
import { MessageList } from './MessageList'
import { VoiceControls } from './VoiceControls'

type LocalMessage = { role: 'USER' | 'ASSISTANT'; content: string }

const interviewTypeLabels: Record<InterviewType, string> = {
  DSA: 'Data Structures & Algorithms',
  HLD: 'High Level Design',
  LLD: 'Low Level Design',
}

const roleLabels: Record<SdeRole, string> = {
  SDE1: 'Junior Engineer (SDE1)',
  SDE2: 'Mid-Level Engineer (SDE2)',
  SDE3: 'Senior Engineer (SDE3)',
}

export function InterviewSession() {
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [interviewType, setInterviewType] = useState<InterviewType>('DSA')
  const [sdeRole, setSdeRole] = useState<SdeRole>('SDE1')
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [isStreaming, setIsStreaming] = useState(false)
  const [isStarting, setIsStarting] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const tts = useTextToSpeech()

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleStart = async () => {
    setIsStarting(true)
    try {
      const session = await createSession(interviewType, sdeRole)
      const sessionWithMessages = await getSession(session.id)
      const existingMessages: LocalMessage[] = sessionWithMessages.messages
        .filter(m => m.role === 'USER' || m.role === 'ASSISTANT')
        .map(m => ({ role: m.role as 'USER' | 'ASSISTANT', content: m.content }))
      setMessages(existingMessages)
      setSessionId(session.id)
      if (existingMessages.length > 0 && existingMessages[0].role === 'ASSISTANT') {
        tts.speak(existingMessages[0].content)
      }
    } finally {
      setIsStarting(false)
    }
  }

  const handleSubmit = async (text: string) => {
    if (!sessionId) return

    setMessages(prev => [...prev, { role: 'USER', content: text }])
    setIsStreaming(true)

    let accumulated = ''

    try {
      await sendMessage(
        sessionId,
        text,
        (token) => {
          accumulated += token
          setMessages(prev => {
            const last = prev[prev.length - 1]
            if (last?.role === 'ASSISTANT') {
              return [...prev.slice(0, -1), { role: 'ASSISTANT', content: last.content + token }]
            }
            return [...prev, { role: 'ASSISTANT', content: token }]
          })
        },
        () => {
          tts.speak(accumulated)
        },
      )
    } catch (error) {
      console.error('Failed to send message:', error)
      const errorMessage = "I'm sorry, I didn't catch that. Could you please repeat?"
      setMessages(prev => [
        ...prev,
        { role: 'ASSISTANT', content: errorMessage }
      ])
      tts.speak(errorMessage)
    } finally {
      setIsStreaming(false)
    }
  }

  const handleHint = () => {
    handleSubmit("Can you give me a hint?")
  }

  if (!sessionId) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        padding: '20px',
      }}>
        <div style={{
          background: 'white',
          borderRadius: '24px',
          padding: '48px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          maxWidth: '420px',
          width: '100%',
        }}>
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <div style={{
              width: '72px',
              height: '72px',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              borderRadius: '16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
              fontSize: '32px',
            }}>
              💼
            </div>
            <h1 style={{
              fontSize: '28px',
              fontWeight: '700',
              color: '#1a1a2e',
              margin: '0 0 8px',
            }}>PrepLit</h1>
            <p style={{
              color: '#6b7280',
              fontSize: '15px',
              margin: 0,
            }}>Your AI-powered interview coach</p>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            <div>
              <label style={{
                display: 'block',
                fontSize: '13px',
                fontWeight: '600',
                color: '#374151',
                marginBottom: '8px',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
              }}>
                Interview Type
              </label>
              <select
                value={interviewType}
                onChange={e => setInterviewType(e.target.value as InterviewType)}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '14px 16px',
                  fontSize: '15px',
                  border: '2px solid #e5e7eb',
                  borderRadius: '12px',
                  background: '#f9fafb',
                  color: '#1a1a2e',
                  cursor: 'pointer',
                  outline: 'none',
                  transition: 'border-color 0.2s, box-shadow 0.2s',
                }}
              >
                <option value="DSA">{interviewTypeLabels.DSA}</option>
                <option value="HLD">{interviewTypeLabels.HLD}</option>
                <option value="LLD">{interviewTypeLabels.LLD}</option>
              </select>
            </div>

            <div>
              <label style={{
                display: 'block',
                fontSize: '13px',
                fontWeight: '600',
                color: '#374151',
                marginBottom: '8px',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
              }}>
                Experience Level
              </label>
              <select
                value={sdeRole}
                onChange={e => setSdeRole(e.target.value as SdeRole)}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '14px 16px',
                  fontSize: '15px',
                  border: '2px solid #e5e7eb',
                  borderRadius: '12px',
                  background: '#f9fafb',
                  color: '#1a1a2e',
                  cursor: 'pointer',
                  outline: 'none',
                  transition: 'border-color 0.2s, box-shadow 0.2s',
                }}
              >
                <option value="SDE1">{roleLabels.SDE1}</option>
                <option value="SDE2">{roleLabels.SDE2}</option>
                <option value="SDE3">{roleLabels.SDE3}</option>
              </select>
            </div>

            <button
              onClick={handleStart}
              disabled={isStarting}
              style={{
                marginTop: '8px',
                padding: '16px 24px',
                fontSize: '16px',
                fontWeight: '600',
                color: 'white',
                background: isStarting
                  ? '#9ca3af'
                  : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                border: 'none',
                borderRadius: '12px',
                cursor: isStarting ? 'not-allowed' : 'pointer',
                transition: 'transform 0.2s, box-shadow 0.2s',
                boxShadow: '0 4px 14px 0 rgba(102, 126, 234, 0.4)',
              }}
            >
              {isStarting ? 'Starting...' : 'Start Interview'}
            </button>
          </div>

          <p style={{
            textAlign: 'center',
            fontSize: '13px',
            color: '#9ca3af',
            marginTop: '24px',
            marginBottom: 0,
          }}>
            Practice makes perfect. Good luck!
          </p>
        </div>
      </div>
    )
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      background: '#f3f4f6',
    }}>
      {/* Header */}
      <div style={{
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        padding: '16px 24px',
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
      }}>
        <div style={{
          width: '40px',
          height: '40px',
          background: 'rgba(255,255,255,0.2)',
          borderRadius: '10px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '20px',
        }}>
          💼
        </div>
        <div>
          <h1 style={{
            margin: 0,
            fontSize: '18px',
            fontWeight: '600',
            color: 'white',
          }}>PrepLit Interview</h1>
          <p style={{
            margin: 0,
            fontSize: '13px',
            color: 'rgba(255,255,255,0.8)',
          }}>
            {interviewTypeLabels[interviewType]} • {roleLabels[sdeRole]}
          </p>
        </div>
      </div>

      {/* Messages */}
      <MessageList messages={messages} />
      <div ref={bottomRef} />

      {/* Input */}
      <div style={{
        borderTop: '1px solid #e5e7eb',
        padding: '16px 24px',
        background: 'white',
        boxShadow: '0 -4px 6px -1px rgba(0, 0, 0, 0.05)',
      }}>
        <VoiceControls onSubmit={handleSubmit} onHint={handleHint} disabled={isStreaming || tts.isSpeaking} isSpeaking={tts.isSpeaking} />
      </div>
    </div>
  )
}
