import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { InterviewSession } from './components/InterviewSession'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<InterviewSession />} />
      </Routes>
    </BrowserRouter>
  )
}
