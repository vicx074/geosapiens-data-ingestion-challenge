import { describe, expect, it } from 'vitest'

import { buildImportFormData } from './imports.api'

describe('multipart da importação', () => {
  it('preserva o File e o nome original no campo esperado pelo backend', () => {
    const file = new File(['transaction_id,occurred_at,amount,category'], 'dataset.csv', {
      type: 'text/csv',
    })

    const formData = buildImportFormData(file)
    const uploaded = formData.get('file')

    expect(uploaded).toBeInstanceOf(File)
    expect((uploaded as File).name).toBe('dataset.csv')
    expect((uploaded as File).size).toBe(file.size)
  })
})
