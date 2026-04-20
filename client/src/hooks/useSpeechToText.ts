import { useState, useRef, useCallback } from 'react'

const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition

interface UseSpeechToTextResult {
  transcript: string
  isListening: boolean
  isAvailable: boolean
  start: () => void
  stop: () => void
  clearTranscript: () => void
}

// Single global instance
let globalRec: any = null

export function useSpeechToText(): UseSpeechToTextResult {
  const [transcript, setTranscript] = useState('')
  const [isListening, setIsListening] = useState(false)
  const fullTranscriptRef = useRef('')

  const isAvailable = !!SpeechRecognition

  const start = useCallback(() => {
    if (!SpeechRecognition) {
      alert('Use Chrome')
      return
    }

    // Stop any existing
    if (globalRec) {
      try { globalRec.stop() } catch(e) {}
    }

    fullTranscriptRef.current = ''
    setTranscript('')

    const rec = new SpeechRecognition()
    rec.continuous = true
    rec.interimResults = true
    rec.lang = 'en-US'

    rec.onresult = (e: any) => {
      console.log('RESULT:', e.results.length, 'items')
      let final = ''
      let interim = ''

      for (let i = 0; i < e.results.length; i++) {
        const t = e.results[i][0].transcript
        if (e.results[i].isFinal) {
          final += t + ' '
        } else {
          interim += t
        }
      }

      fullTranscriptRef.current = final
      setTranscript((final + interim).trim())
    }

    rec.onerror = (e: any) => {
      console.log('ERROR:', e.error)
      if (e.error === 'not-allowed') {
        alert('Mic blocked')
        setIsListening(false)
      }
      // Ignore no-speech, aborted etc
    }

    rec.onend = () => {
      console.log('ENDED')
      setIsListening(false)
      if (fullTranscriptRef.current.trim()) {
        setTranscript(fullTranscriptRef.current.trim())
      }
    }

    globalRec = rec
    rec.start()
    setIsListening(true)
    console.log('STARTED')
  }, [])

  const stop = useCallback(() => {
    if (globalRec) {
      try { globalRec.stop() } catch(e) {}
    }
    setIsListening(false)
  }, [])

  const clearTranscript = useCallback(() => {
    fullTranscriptRef.current = ''
    setTranscript('')
  }, [])

  return { transcript, isListening, isAvailable, start, stop, clearTranscript }
}
