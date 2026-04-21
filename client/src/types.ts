export type InterviewType = 'DSA' | 'HLD' | 'LLD' | 'RESUME_GRILLING' | 'CULTURE_FIT'
export type SdeRole = 'SDE1' | 'SDE2' | 'SDE3'

export interface Session {
  id: string
  type: InterviewType
  role: SdeRole
  status: string
}

export interface Message {
  id: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  createdAt: string
}

export interface SessionWithMessages extends Session {
  messages: Message[]
}

export interface FeedbackReport {
  summary: string
  strengths: string[]
  weaknesses: string[]
  verdict: 'HIRE' | 'NO_HIRE' | 'BORDERLINE'
  nextSteps: string[]
  scores: {
    problemUnderstanding: number
    approach: number
    correctness: number
    communication: number
    optimization: number
    total: number
  }
}
