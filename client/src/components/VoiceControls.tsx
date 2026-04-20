import { useEffect, useRef, useState } from 'react'
import { useSpeechToText } from '../hooks/useSpeechToText'
import { useTextToSpeech } from '../hooks/useTextToSpeech'

interface VoiceControlsProps {
  onSubmit: (text: string) => void
  onHint: () => void
  disabled?: boolean
  isSpeaking?: boolean
}

interface AudioDevice {
  deviceId: string
  label: string
}

export function VoiceControls({ onSubmit, onHint, disabled, isSpeaking }: VoiceControlsProps) {
  const { transcript, isListening, start, stop, clearTranscript } = useSpeechToText()
  const { isMuted, toggleMute } = useTextToSpeech()
  const wasListeningRef = useRef(false)
  const [micLevel, setMicLevel] = useState(0)
  const [micError, setMicError] = useState<string | null>(null)
  const [audioDevices, setAudioDevices] = useState<AudioDevice[]>([])
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('')
  const streamRef = useRef<MediaStream | null>(null)
  const audioContextRef = useRef<AudioContext | null>(null)

  // Get list of audio devices
  useEffect(() => {
    const getDevices = async () => {
      try {
        // Request permission first
        await navigator.mediaDevices.getUserMedia({ audio: true })
        const devices = await navigator.mediaDevices.enumerateDevices()
        const audioInputs = devices
          .filter(d => d.kind === 'audioinput')
          .map(d => ({ deviceId: d.deviceId, label: d.label || `Microphone ${d.deviceId.slice(0, 5)}` }))
        setAudioDevices(audioInputs)
        if (audioInputs.length > 0 && !selectedDeviceId) {
          setSelectedDeviceId(audioInputs[0].deviceId)
        }
      } catch (err) {
        console.error('Failed to enumerate devices:', err)
      }
    }
    getDevices()
  }, [])

  // Setup mic level monitoring when device changes
  useEffect(() => {
    if (!selectedDeviceId) return

    // Cleanup previous stream
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(t => t.stop())
    }
    if (audioContextRef.current) {
      audioContextRef.current.close()
    }

    const setupMic = async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { deviceId: { exact: selectedDeviceId } }
        })
        streamRef.current = stream

        const audioContext = new AudioContext()
        audioContextRef.current = audioContext
        const analyser = audioContext.createAnalyser()
        const microphone = audioContext.createMediaStreamSource(stream)
        microphone.connect(analyser)
        analyser.fftSize = 256
        const dataArray = new Uint8Array(analyser.frequencyBinCount)

        const checkLevel = () => {
          if (audioContextRef.current?.state === 'closed') return
          analyser.getByteFrequencyData(dataArray)
          const avg = dataArray.reduce((a, b) => a + b) / dataArray.length
          setMicLevel(avg)
          requestAnimationFrame(checkLevel)
        }
        checkLevel()
        setMicError(null)
      } catch (err: any) {
        console.error('Mic error:', err)
        setMicError(err.message)
      }
    }

    setupMic()

    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(t => t.stop())
      }
      if (audioContextRef.current) {
        audioContextRef.current.close()
      }
    }
  }, [selectedDeviceId])

  // Auto-submit when recording stops and there's a transcript
  useEffect(() => {
    if (wasListeningRef.current && !isListening && transcript.trim()) {
      onSubmit(transcript.trim())
      clearTranscript()
    }
    wasListeningRef.current = isListening
  }, [isListening, transcript, onSubmit, clearTranscript])

  const handleMicClick = () => {
    if (disabled) return
    if (isListening) {
      stop()
    } else {
      clearTranscript()
      start()
    }
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: '16px',
    }}>
      {/* Transcript display */}
      <div style={{
        padding: '16px 20px',
        background: isListening ? '#fef3c7' : '#f9fafb',
        borderRadius: '16px',
        border: isListening ? '2px solid #f59e0b' : '2px solid #e5e7eb',
        minHeight: '60px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: transcript ? '#1a1a2e' : '#9ca3af',
        fontSize: '16px',
        textAlign: 'center',
        transition: 'all 0.2s ease',
      }}>
        {transcript || (isListening ? '🎙️ Listening... Speak now!' : 'Click the microphone to start speaking')}
      </div>

      {/* Controls */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '16px',
      }}>
        {/* Mute TTS button */}
        <button
          onClick={toggleMute}
          style={{
            width: '50px',
            height: '50px',
            borderRadius: '50%',
            border: 'none',
            background: isMuted ? '#fef2f2' : '#f0fdf4',
            color: isMuted ? '#dc2626' : '#16a34a',
            fontSize: '22px',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s ease',
          }}
          title={isMuted ? 'Unmute AI voice' : 'Mute AI voice'}
        >
          {isMuted ? '🔇' : '🔊'}
        </button>

        {/* Main mic button */}
        <button
          onClick={handleMicClick}
          disabled={disabled}
          style={{
            width: '80px',
            height: '80px',
            borderRadius: '50%',
            border: 'none',
            background: disabled
              ? '#e5e7eb'
              : isListening
                ? 'linear-gradient(135deg, #ef4444 0%, #dc2626 100%)'
                : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            fontSize: '32px',
            cursor: disabled ? 'not-allowed' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: disabled
              ? 'none'
              : isListening
                ? '0 8px 25px rgba(239, 68, 68, 0.5)'
                : '0 8px 25px rgba(102, 126, 234, 0.5)',
            transition: 'all 0.2s ease',
            animation: isListening ? 'pulse 1.5s infinite' : 'none',
          }}
          title={isListening ? 'Stop recording & send' : 'Start recording'}
        >
          {isListening ? '⏹️' : '🎤'}
        </button>

        {/* Hint button */}
        <button
          onClick={onHint}
          disabled={disabled}
          style={{
            width: '50px',
            height: '50px',
            borderRadius: '50%',
            border: 'none',
            background: disabled ? '#e5e7eb' : '#fef3c7',
            color: disabled ? '#9ca3af' : '#d97706',
            fontSize: '22px',
            cursor: disabled ? 'not-allowed' : 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            transition: 'all 0.2s ease',
          }}
          title="Get a hint"
        >
          💡
        </button>
      </div>

      {/* Mic level indicator */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '8px',
        fontSize: '13px',
        color: micError ? '#dc2626' : '#6b7280',
      }}>
        {micError ? (
          <span>Mic error: {micError}</span>
        ) : (
          <>
            <span>Mic level:</span>
            <div style={{
              width: '100px',
              height: '8px',
              background: '#e5e7eb',
              borderRadius: '4px',
              overflow: 'hidden',
            }}>
              <div style={{
                width: `${Math.min(micLevel * 2, 100)}%`,
                height: '100%',
                background: micLevel > 10 ? '#22c55e' : '#9ca3af',
                transition: 'width 0.1s',
              }} />
            </div>
            <span>{micLevel > 10 ? '✓ Working' : 'Speak to test'}</span>
          </>
        )}
      </div>

      {/* Microphone selector */}
      {audioDevices.length > 1 && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '4px',
          fontSize: '13px',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ color: '#6b7280' }}>Microphone:</span>
            <select
              value={selectedDeviceId}
              onChange={(e) => setSelectedDeviceId(e.target.value)}
              style={{
                padding: '4px 8px',
                fontSize: '13px',
                border: '1px solid #e5e7eb',
                borderRadius: '6px',
                background: '#f9fafb',
                color: '#374151',
                maxWidth: '200px',
              }}
            >
              {audioDevices.map(device => (
                <option key={device.deviceId} value={device.deviceId}>
                  {device.label}
                </option>
              ))}
            </select>
          </div>
          <span style={{ color: '#9ca3af', fontSize: '11px' }}>
            Speech uses system default. Change in System Settings → Sound if needed.
          </span>
        </div>
      )}

      {/* Status text */}
      <div style={{
        textAlign: 'center',
        fontSize: '13px',
        color: '#6b7280',
      }}>
        {isSpeaking
          ? 'AI is speaking...'
          : disabled
            ? 'Waiting for response...'
            : isListening
              ? 'Click the button again to stop and send'
              : 'Tap microphone to speak'}
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% { transform: scale(1); }
          50% { transform: scale(1.05); }
        }
      `}</style>
    </div>
  )
}
