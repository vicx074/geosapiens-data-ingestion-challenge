const integerFormatter = new Intl.NumberFormat('pt-BR')
const dateTimeFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export function formatInteger(value: number) {
  return integerFormatter.format(value)
}

export function formatDateTime(value: string | null) {
  if (!value) {
    return '—'
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date)
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
