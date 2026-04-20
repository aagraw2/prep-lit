export type InterviewType = 'DSA' | 'HLD' | 'LLD'
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
