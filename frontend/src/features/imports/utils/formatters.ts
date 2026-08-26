const integerFormatter = new Intl.NumberFormat('pt-BR')
const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})
const dateTimeFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'medium',
  timeStyle: 'short',
})
const monthFormatter = new Intl.DateTimeFormat('pt-BR', {
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

export function formatInteger(value: number) {
  return integerFormatter.format(value)
}

export function formatCurrency(value: number) {
  return currencyFormatter.format(value)
}

export function formatDateTime(value: string | null) {
  if (!value) {
    return '—'
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date)
}

export function formatMonth(value: string) {
  const match = /^(\d{4})-(\d{2})$/.exec(value)
  if (!match) {
    return value
  }

  const year = Number(match[1])
  const month = Number(match[2])
  if (month < 1 || month > 12) {
    return value
  }

  // O contrato do backend agrega meses em UTC; criar a data também em UTC evita deslocamento de mês
  // quando o navegador estiver em um fuso horário negativo.
  return monthFormatter.format(new Date(Date.UTC(year, month - 1, 1)))
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }

  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let index = 0

  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }

  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(value)} ${units[index]}`
}

export function validateCsvFile(file: File) {
  if (file.size === 0) {
    return 'O arquivo selecionado está vazio.'
  }

  if (!file.name.toLocaleLowerCase('pt-BR').endsWith('.csv')) {
    return 'Selecione um arquivo com extensão .csv.'
  }

  return null
}
