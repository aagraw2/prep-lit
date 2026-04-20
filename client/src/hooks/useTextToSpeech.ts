import { useState, useCallback } from 'react'

interface UseTextToSpeechResult {
  speak: (text: string) => void
  cancel: () => void
  isMuted: boolean
  isSpeaking: boolean
  toggleMute: () => void
}

export function useTextToSpeech(): UseTextToSpeechResult {
  const [isMuted, setIsMuted] = useState(false)
  const [isSpeaking, setIsSpeaking] = useState(false)

  const speak = useCallback((text: string) => {
    if (isMuted) return
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.onstart = () => setIsSpeaking(true)
    utterance.onend = () => setIsSpeaking(false)
    utterance.onerror = () => setIsSpeaking(false)
    window.speechSynthesis.speak(utterance)
  }, [isMuted])

  const cancel = useCallback(() => {
    window.speechSynthesis.cancel()
    setIsSpeaking(false)
  }, [])

  const toggleMute = useCallback(() => {
    setIsMuted(prev => {
      if (!prev) {
        window.speechSynthesis.cancel()
        setIsSpeaking(false)
      }
      return !prev
    })
  }, [])

  return { speak, cancel, isMuted, isSpeaking, toggleMute }
}
