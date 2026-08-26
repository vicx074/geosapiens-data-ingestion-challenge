import type { CSSProperties } from 'react'

import type { MonthAggregateResponse } from '../../model/imports'
import { formatCurrency, formatInteger, formatMonth } from '../../utils/formatters'

type MonthlySeriesProps = {
  data: MonthAggregateResponse[]
}

function widthStyle(value: number, maximumMagnitude: number) {
  const percentage = maximumMagnitude === 0 ? 0 : (Math.abs(value) / maximumMagnitude) * 100
  return { '--bar-width': `${percentage}%` } as CSSProperties
}

export function MonthlySeries({ data }: MonthlySeriesProps) {
  const maximumMagnitude = Math.max(0, ...data.map((item) => Math.abs(item.totalAmount)))

  return (
    <section className="analytics-panel" aria-labelledby="monthly-series-title">
      <header className="analytics-panel__heading">
        <div>
          <p className="section-heading__label">Série temporal</p>
          <h3 id="monthly-series-title">Valor por mês</h3>
        </div>
        <span>{formatInteger(data.length)} meses</span>
      </header>

      <ul className="month-chart">
        {data.map((item) => (
          <li key={item.month} className="month-chart__item">
            <div className="month-chart__labels">
              <div>
                <strong>{formatMonth(item.month)}</strong>
                <small>{formatInteger(item.transactionCount)} transações</small>
              </div>
              <span className={item.totalAmount < 0 ? 'month-chart__value month-chart__value--negative' : 'month-chart__value'}>
                {formatCurrency(item.totalAmount)}
              </span>
            </div>
            <div className="month-chart__track" aria-hidden="true">
              <span
                className={item.totalAmount < 0 ? 'month-chart__bar month-chart__bar--negative' : 'month-chart__bar'}
                style={widthStyle(item.totalAmount, maximumMagnitude)}
              />
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
