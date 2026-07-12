import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect } from 'vitest'
import { LoginPage } from '../LoginPage'

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('renders without crashing', () => {
    renderLoginPage()
    expect(screen.getByText('ShopMate')).toBeInTheDocument()
    expect(screen.getByText('Share shopping lists with anyone')).toBeInTheDocument()
  })

  it('Google sign-in button has correct href', () => {
    renderLoginPage()
    const link = screen.getByRole('link', { name: /sign in with google/i })
    expect(link).toHaveAttribute('href', '/oauth2/authorization/google')
  })
})
