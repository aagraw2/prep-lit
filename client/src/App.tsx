import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { InterviewSession } from './components/InterviewSession'
import { PastInterviews } from './components/PastInterviews'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<InterviewSession />} />
        <Route path="/history" element={<PastInterviews />} />
      </Routes>
    </BrowserRouter>
  )
}
