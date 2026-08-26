export const INGESTION_STATUSES = [
  'RECEIVED',
  'QUEUED',
  'PROCESSING',
  'COMPLETED',
  'COMPLETED_WITH_ERRORS',
  'FAILED',
] as const

export type IngestionStatus = (typeof INGESTION_STATUSES)[number]

export type AcceptedIngestionResponse = {
  jobId: string
  status: IngestionStatus
  statusUrl: string
}

export type IngestionStatusResponse = {
  jobId: string
  filename: string
  status: IngestionStatus
  processedRows: number
  acceptedRows: number
  rejectedRows: number
  terminal: boolean
  createdAt: string
  queuedAt: string | null
  startedAt: string | null
  finishedAt: string | null
  updatedAt: string
  failureReason: string | null
}

export type CategoryAggregateResponse = {
  category: string
  transactionCount: number
  totalAmount: number
}

export type MonthAggregateResponse = {
  month: string
  transactionCount: number
  totalAmount: number
}

export type IngestionAnalyticsResponse = {
  transactionCount: number
  totalAmount: number
  byCategory: CategoryAggregateResponse[]
  byMonth: MonthAggregateResponse[]
}

export type IngestionTransactionItemResponse = {
  id: number
  sourceRow: number
  transactionId: string
  occurredAt: string
  amount: number
  category: string
}

export type IngestionTransactionPageResponse = {
  items: IngestionTransactionItemResponse[]
  nextCursor: number | null
}

export type IngestionErrorItemResponse = {
  sourceRow: number
  code: string
  reason: string
}

export type IngestionErrorPageResponse = {
  items: IngestionErrorItemResponse[]
  nextCursor: number | null
}

export type StatusPresentation = {
  label: string
  description: string
  tone: 'neutral' | 'info' | 'success' | 'warning' | 'danger'
}

export const STATUS_PRESENTATION: Record<IngestionStatus, StatusPresentation> = {
  RECEIVED: {
    label: 'Recebido',
    description: 'O arquivo foi aceito e o job está sendo preparado para a fila.',
    tone: 'neutral',
  },
  QUEUED: {
    label: 'Na fila',
    description: 'O job está durável e aguarda capacidade disponível do Worker.',
    tone: 'info',
  },
  PROCESSING: {
    label: 'Processando',
    description: 'O Worker está validando e confirmando o arquivo em lotes.',
    tone: 'info',
  },
  COMPLETED: {
    label: 'Concluído',
    description: 'Todas as linhas foram processadas sem rejeições.',
    tone: 'success',
  },
  COMPLETED_WITH_ERRORS: {
    label: 'Concluído com erros',
    description: 'O processamento terminou, mas algumas linhas foram rejeitadas.',
    tone: 'warning',
  },
  FAILED: {
    label: 'Falhou',
    description: 'O processamento não pôde ser concluído.',
    tone: 'danger',
  },
}
