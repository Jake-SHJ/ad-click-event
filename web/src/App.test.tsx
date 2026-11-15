import { render, screen } from '@testing-library/react'
import App from './App'

describe('App', () => {
  it('renders the page title', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: '광고 클릭 파이프라인' })).toBeInTheDocument()
  })
})
