import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { InterviewType, SdeRole, FeedbackReport } from '../types'
import { createSession, createSessionWithResume, sendMessage, getSession, endInterview } from '../api/client'
import { useTextToSpeech } from '../hooks/useTextToSpeech'
import { MessageList } from './MessageList'
import { VoiceControls } from './VoiceControls'
import { FeedbackModal } from './FeedbackModal'

type LocalMessage = { role: 'USER' | 'ASSISTANT'; content: string }

const interviewTypeLabels: Record<InterviewType, string> = {
  DSA: 'Data Structures & Algorithms',
  HLD: 'High Level Design',
  LLD: 'Low Level Design',
  RESUME_GRILLING: 'Resume Deep Dive',
  CULTURE_FIT: 'Culture Fit & Behavioral',
  API_AND_DATABASE_DESIGN: 'API & DB Design',
}

const roleLabels: Record<SdeRole, string> = {
  SDE1: 'Junior Engineer',
  SDE2: 'Mid-Level Engineer',
  SDE3: 'Senior Engineer',
}

// Color palette — blue theme
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
}

const Logo = ({ size = 40 }: { size?: number }) => (
  <img 
    src="/logo.png" 
    alt="PrepLit Logo" 
    style={{ 
      height: size,
      width: 'auto',
      objectFit: 'contain'
    }} 
  />
)

export function InterviewSession() {
  const navigate = useNavigate()
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [interviewType, setInterviewType] = useState<InterviewType>('DSA')
  const [sdeRole, setSdeRole] = useState<SdeRole>('SDE1')
  const [messages, setMessages] = useState<LocalMessage[]>([])
  const [isStreaming, setIsStreaming] = useState(false)
  const [isStarting, setIsStarting] = useState(false)
  const [assistantHasResponded, setAssistantHasResponded] = useState(false)
  const [feedback, setFeedback] = useState<FeedbackReport | null>(null)
  const [isEndingInterview, setIsEndingInterview] = useState(false)
  const [resumeFile, setResumeFile] = useState<File | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const tts = useTextToSpeech()

  const showResumeUpload = interviewType === 'RESUME_GRILLING'

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      if (file.type !== 'application/pdf') {
        setUploadError('Please upload a PDF file')
        setResumeFile(null)
        return
      }
      if (file.size > 5 * 1024 * 1024) {
        setUploadError('File size must be less than 5MB')
        setResumeFile(null)
        return
      }
      setUploadError(null)
      setResumeFile(file)
    }
  }

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleStart = async () => {
    if (interviewType === 'RESUME_GRILLING' && !resumeFile) {
      setUploadError('Please upload your resume')
      return
    }

    setIsStarting(true)
    try {
      let session
      if (interviewType === 'RESUME_GRILLING' && resumeFile) {
        session = await createSessionWithResume(interviewType, sdeRole, resumeFile)
      } else {
        session = await createSession(interviewType, sdeRole)
      }
      const sessionWithMessages = await getSession(session.id)
      const existingMessages: LocalMessage[] = sessionWithMessages.messages
        .filter(m => m.role === 'USER' || m.role === 'ASSISTANT')
        .map(m => ({ role: m.role as 'USER' | 'ASSISTANT', content: m.content }))
      setMessages(existingMessages)
      setSessionId(session.id)
      if (existingMessages.length > 0 && existingMessages[0].role === 'ASSISTANT') {
        tts.speak(existingMessages[0].content)
        setAssistantHasResponded(true)
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
          setAssistantHasResponded(true)
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

  const handleEndInterview = async () => {
    if (!sessionId) return
    setIsEndingInterview(true)
    try {
      const feedbackReport = await endInterview(sessionId)
      setFeedback(feedbackReport)
    } catch (error) {
      console.error('Failed to end interview:', error)
      alert('Failed to get feedback. Please try again.')
    } finally {
      setIsEndingInterview(false)
    }
  }

  const handleCloseFeedback = () => {
    setFeedback(null)
    // Optionally reset the session
    setSessionId(null)
    setMessages([])
    setAssistantHasResponded(false)
  }

  if (!sessionId) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        background: `linear-gradient(135deg, ${colors.primary} 0%, ${colors.secondary} 100%)`,
        padding: '20px',
      }}>
        <div style={{
          background: colors.secondary,
          borderRadius: '20px',
          padding: '48px',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.5), 0 0 1px rgba(79, 142, 247, 0.3)',
          maxWidth: '440px',
          width: '100%',
          border: `1px solid ${colors.border}`,
        }}>
          {/* Logo */}
          <div style={{ textAlign: 'center', marginBottom: '40px' }}>
            <Logo size={200} />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            {/* Interview Type */}
            <div>
              <label style={{
                display: 'block',
                fontSize: '12px',
                fontWeight: '600',
                color: colors.textMuted,
                marginBottom: '10px',
                textTransform: 'uppercase',
                letterSpacing: '1px',
              }}>
                Interview Type
              </label>
              <select
                value={interviewType}
                onChange={e => {
                  setInterviewType(e.target.value as InterviewType)
                  setResumeFile(null)
                  setUploadError(null)
                }}
                style={{
                  display: 'block',
                  width: '100%',
                  padding: '14px 16px',
                  fontSize: '15px',
                  border: `1px solid ${colors.border}`,
                  borderRadius: '10px',
                  background: colors.primary,
                  color: colors.text,
                  cursor: 'pointer',
                  outline: 'none',
                  transition: 'all 0.2s',
                  fontWeight: '500',
                }}
                onFocus={(e) => e.target.style.borderColor = colors.accent}
                onBlur={(e) => e.target.style.borderColor = colors.border}
              >
                <option value="DSA">{interviewTypeLabels.DSA}</option>
                <option value="HLD">{interviewTypeLabels.HLD}</option>
                <option value="LLD">{interviewTypeLabels.LLD}</option>
                <option value="RESUME_GRILLING">{interviewTypeLabels.RESUME_GRILLING}</option>
                <option value="CULTURE_FIT">{interviewTypeLabels.CULTURE_FIT}</option>
                <option value="API_AND_DATABASE_DESIGN">{interviewTypeLabels.API_AND_DATABASE_DESIGN}</option>
              </select>
            </div>

            {/* Resume Upload (shown only for RESUME_GRILLING) */}
            {showResumeUpload && (
              <div>
                <label style={{
                  display: 'block',
                  fontSize: '12px',
                  fontWeight: '600',
                  color: colors.textMuted,
                  marginBottom: '10px',
                  textTransform: 'uppercase',
                  letterSpacing: '1px',
                }}>
                  Upload Resume (PDF)
                </label>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,application/pdf"
                  onChange={handleFileChange}
                  style={{ display: 'none' }}
                />
                <div
                  onClick={() => fileInputRef.current?.click()}
                  style={{
                    padding: '16px',
                    border: `2px dashed ${uploadError ? colors.error : resumeFile ? colors.success : colors.border}`,
                    borderRadius: '10px',
                    background: colors.primary,
                    cursor: 'pointer',
                    textAlign: 'center',
                    transition: 'all 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    if (!resumeFile) e.currentTarget.style.borderColor = colors.accent
                  }}
                  onMouseLeave={(e) => {
                    if (!resumeFile && !uploadError) e.currentTarget.style.borderColor = colors.border
                  }}
                >
                  {resumeFile ? (
                    <span style={{ color: colors.success, fontWeight: '500' }}>{resumeFile.name}</span>
                  ) : (
                    <span style={{ color: colors.textMuted }}>Click to upload PDF</span>
                  )}
                </div>
                {uploadError && (
                  <p style={{ color: colors.error, fontSize: '12px', marginTop: '8px', marginBottom: 0 }}>{uploadError}</p>
                )}
              </div>
            )}

            {/* Experience Level */}
            <div>
              <label style={{
                display: 'block',
                fontSize: '12px',
                fontWeight: '600',
                color: colors.textMuted,
                marginBottom: '10px',
                textTransform: 'uppercase',
                letterSpacing: '1px',
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
                  border: `1px solid ${colors.border}`,
                  borderRadius: '10px',
                  background: colors.primary,
                  color: colors.text,
                  cursor: 'pointer',
                  outline: 'none',
                  transition: 'all 0.2s',
                  fontWeight: '500',
                }}
                onFocus={(e) => e.target.style.borderColor = colors.accent}
                onBlur={(e) => e.target.style.borderColor = colors.border}
              >
                <option value="SDE1">{roleLabels.SDE1}</option>
                <option value="SDE2">{roleLabels.SDE2}</option>
                <option value="SDE3">{roleLabels.SDE3}</option>
              </select>
            </div>

            {/* Start Button */}
            <button
              onClick={handleStart}
              disabled={isStarting}
              style={{
                marginTop: '8px',
                padding: '16px 24px',
                fontSize: '16px',
                fontWeight: '600',
                color: colors.primary,
                background: isStarting
                  ? colors.textMuted
                  : `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentDark} 100%)`,
                border: 'none',
                borderRadius: '10px',
                cursor: isStarting ? 'not-allowed' : 'pointer',
                transition: 'all 0.2s',
                boxShadow: isStarting ? 'none' : `0 4px 20px rgba(79, 142, 247, 0.3)`,
                letterSpacing: '0.5px',
              }}
              onMouseEnter={(e) => {
                if (!isStarting) {
                  e.currentTarget.style.transform = 'translateY(-2px)'
                  e.currentTarget.style.boxShadow = `0 6px 25px rgba(79, 142, 247, 0.4)`
                }
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'translateY(0)'
                e.currentTarget.style.boxShadow = isStarting ? 'none' : `0 4px 20px rgba(79, 142, 247, 0.3)`
              }}
            >
              {isStarting ? 'Initializing...' : 'Start Interview'}
            </button>
          </div>

          <p style={{
            textAlign: 'center',
            fontSize: '12px',
            color: colors.textMuted,
            marginTop: '32px',
            marginBottom: '12px',
          }}>
            Practice makes perfect. Good luck!
          </p>
          <button
            onClick={() => navigate('/history')}
            style={{
              display: 'block',
              width: '100%',
              padding: '10px',
              background: 'none',
              border: `1px solid ${colors.border}`,
              borderRadius: '8px',
              color: colors.textMuted,
              fontSize: '13px',
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
            onMouseEnter={e => {
              e.currentTarget.style.borderColor = colors.accent
              e.currentTarget.style.color = colors.accent
            }}
            onMouseLeave={e => {
              e.currentTarget.style.borderColor = colors.border
              e.currentTarget.style.color = colors.textMuted
            }}
          >
            View Past Interviews →
          </button>
        </div>
      </div>
    )
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      background: colors.primary,
    }}>
      {/* Header */}
      <div style={{
        background: colors.secondary,
        padding: '16px 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        boxShadow: '0 2px 10px rgba(0, 0, 0, 0.3)',
        borderBottom: `1px solid ${colors.border}`,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <img src="/header.png" alt="PrepLit" style={{ height: 72, width: 'auto', objectFit: 'contain' }} />
          <p style={{
            margin: 0,
            fontSize: '12px',
            color: colors.textMuted,
            fontWeight: '500',
          }}>
            {interviewTypeLabels[interviewType]} • {roleLabels[sdeRole]}
          </p>
        </div>
        <button
          onClick={handleEndInterview}
          disabled={isEndingInterview || isStreaming}
          style={{
            padding: '10px 20px',
            fontSize: '14px',
            fontWeight: '600',
            color: colors.primary,
            background: isEndingInterview || isStreaming 
              ? colors.textMuted 
              : `linear-gradient(135deg, ${colors.accent} 0%, ${colors.accentDark} 100%)`,
            border: 'none',
            borderRadius: '8px',
            cursor: isEndingInterview || isStreaming ? 'not-allowed' : 'pointer',
            opacity: isEndingInterview || isStreaming ? 0.5 : 1,
            transition: 'all 0.2s',
            letterSpacing: '0.3px',
          }}
          onMouseEnter={(e) => {
            if (!isEndingInterview && !isStreaming) {
              e.currentTarget.style.transform = 'translateY(-1px)'
              e.currentTarget.style.boxShadow = `0 4px 12px rgba(79, 142, 247, 0.3)`
            }
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.transform = 'translateY(0)'
            e.currentTarget.style.boxShadow = 'none'
          }}
        >
          {isEndingInterview ? 'Ending...' : 'End Interview'}
        </button>
      </div>

      {/* Messages */}
      <MessageList messages={messages} />
      <div ref={bottomRef} />

      {/* Input */}
      <div style={{
        borderTop: `1px solid ${colors.border}`,
        padding: '20px 24px',
        background: colors.secondary,
        boxShadow: '0 -2px 10px rgba(0, 0, 0, 0.2)',
      }}>
        <VoiceControls 
          onSubmit={handleSubmit} 
          onHint={handleHint} 
          disabled={isStreaming || tts.isSpeaking} 
          hintDisabled={!assistantHasResponded || isStreaming || tts.isSpeaking}
          isSpeaking={tts.isSpeaking} 
        />
      </div>

      {/* Feedback Modal */}
      {feedback && (
        <FeedbackModal feedback={feedback} onClose={handleCloseFeedback} />
      )}
    </div>
  )
}
