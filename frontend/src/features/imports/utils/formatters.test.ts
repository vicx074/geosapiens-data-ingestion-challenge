import { describe, expect, it } from 'vitest'

import { formatCurrency, formatFileSize, formatMonth, validateCsvFile } from './formatters'

describe('formatters de importação', () => {
  it('aceita CSV não vazio sem depender do MIME informado pelo navegador', () => {
    const file = new File(['a,b\n1,2'], 'dataset.CSV', { type: '' })

    expect(validateCsvFile(file)).toBeNull()
  })

  it('rejeita extensão diferente de CSV e arquivo vazio', () => {
    expect(validateCsvFile(new File(['conteúdo'], 'dataset.txt'))).toMatch(/extensão \.csv/i)
    expect(validateCsvFile(new File([], 'dataset.csv'))).toMatch(/vazio/i)
  })

  it('formata tamanho sem precisão excessiva', () => {
    expect(formatFileSize(1536)).toBe('1,5 KB')
  })

  it('formata moeda e mês mantendo o contrato mensal em UTC', () => {
    expect(formatCurrency(1250.5)).toMatch(/1\.250,50/)
    expect(formatMonth('2026-07')).toMatch(/jul\. de 2026/i)
    expect(formatMonth('valor-inválido')).toBe('valor-inválido')
  })
})
