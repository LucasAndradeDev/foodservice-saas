import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Badge } from './Badge'

describe('Badge', () => {
  it('renders its children', () => {
    render(<Badge tone="free">Livre</Badge>)
    expect(screen.getByText('Livre')).toBeInTheDocument()
  })

  it('does not render a dot by default', () => {
    const { container } = render(<Badge tone="occupied">Ocupada</Badge>)
    expect(container.querySelector('span > span')).not.toBeInTheDocument()
  })

  it('renders a dot when dot is true', () => {
    const { container } = render(
      <Badge tone="critical" dot>
        Crítico
      </Badge>,
    )
    expect(container.querySelector('span > span')).toBeInTheDocument()
  })
})
