import type { CSSProperties } from 'react'

import type { CategoryAggregateResponse } from '../../model/imports'
import { formatCurrency, formatInteger } from '../../utils/formatters'

type CategoryDistributionProps = {
  data: CategoryAggregateResponse[]
}

function widthStyle(value: number, maximum: number) {
  const percentage = maximum === 0 ? 0 : (value / maximum) * 100
  return { '--bar-width': `${percentage}%` } as CSSProperties
}

export function CategoryDistribution({ data }: CategoryDistributionProps) {
  const maximumCount = Math.max(0, ...data.map((item) => item.transactionCount))

  return (
    <section className="analytics-panel" aria-labelledby="category-distribution-title">
      <header className="analytics-panel__heading">
        <div>
          <p className="section-heading__label">Distribuição</p>
          <h3 id="category-distribution-title">Por categoria</h3>
        </div>
        <span>{formatInteger(data.length)} categorias</span>
      </header>

      <ul className="category-chart">
        {data.map((item) => (
          <li key={item.category} className="category-chart__item">
            <div className="category-chart__topline">
              <strong>{item.category}</strong>
              <span>{formatCurrency(item.totalAmount)}</span>
            </div>
            <div className="category-chart__track" aria-hidden="true">
              <span style={widthStyle(item.transactionCount, maximumCount)} />
            </div>
            <small>{formatInteger(item.transactionCount)} transações</small>
          </li>
        ))}
      </ul>
    </section>
  )
}
