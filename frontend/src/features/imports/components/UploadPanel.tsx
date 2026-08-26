import { useEffect, useId, useRef, useState, type DragEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { HttpError } from '../../../shared/api/http-client'
import { createImport } from '../api/imports.api'
import { formatFileSize, validateCsvFile } from '../utils/formatters'

function uploadErrorMessage(error: unknown) {
  if (error instanceof HttpError) {
    return error.detail ?? 'A API não conseguiu aceitar o arquivo. Revise os dados e tente novamente.'
  }

  if (error instanceof Error && error.name === 'AbortError') {
    return null
  }

  return 'Não foi possível conectar à API. O envio não será repetido automaticamente.'
}

export function UploadPanel() {
  const inputId = useId()
  const navigate = useNavigate()
  const controllerRef = useRef<AbortController | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => () => controllerRef.current?.abort(), [])

  function selectFile(nextFile: File | null) {
    setSubmitError(null)

    if (!nextFile) {
      setFile(null)
      setValidationError(null)
      return
    }

    const error = validateCsvFile(nextFile)
    setValidationError(error)
    setFile(error ? null : nextFile)
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragging(false)
    selectFile(event.dataTransfer.files.item(0))
  }

  async function handleSubmit() {
    if (!file || isSubmitting) {
      return
    }

    const controller = new AbortController()
    controllerRef.current = controller
    setIsSubmitting(true)
    setSubmitError(null)

    try {
      const accepted = await createImport(file, controller.signal)
      navigate(`/imports/${accepted.jobId}`)
    } catch (error) {
      const message = uploadErrorMessage(error)
      if (message) {
        setSubmitError(message)
      }
    } finally {
      if (controllerRef.current === controller) {
        controllerRef.current = null
        setIsSubmitting(false)
      }
    }
  }

  return (
    <section className="upload-panel" aria-labelledby="upload-panel-title">
      <div className="upload-panel__heading">
        <div>
          <p className="section-heading__label">Novo dataset</p>
          <h2 id="upload-panel-title">Importar arquivo CSV</h2>
        </div>
        <span className="upload-panel__format">.CSV</span>
      </div>

      <div
        className={`dropzone${isDragging ? ' dropzone--dragging' : ''}`}
        onDragEnter={(event) => {
          event.preventDefault()
          setIsDragging(true)
        }}
        onDragOver={(event) => event.preventDefault()}
        onDragLeave={(event) => {
          if (event.currentTarget === event.target) {
            setIsDragging(false)
          }
        }}
        onDrop={handleDrop}
      >
        <input
          className="visually-hidden"
          id={inputId}
          type="file"
          accept=".csv,text/csv"
          disabled={isSubmitting}
          onChange={(event) => selectFile(event.target.files?.item(0) ?? null)}
        />
        <div className="dropzone__icon" aria-hidden="true">
          ↑
        </div>
        <p className="dropzone__title">Arraste o CSV para cá</p>
        <p className="dropzone__hint">ou escolha o arquivo sem ler seu conteúdo no navegador</p>
        <label className="button button--secondary" htmlFor={inputId}>
          Selecionar arquivo CSV
        </label>
      </div>

      {validationError ? (
        <p className="inline-message inline-message--error" role="alert">
          {validationError}
        </p>
      ) : null}

      {file ? (
        <div className="selected-file" aria-live="polite">
          <div className="selected-file__identity">
            <span className="selected-file__icon" aria-hidden="true">
              CSV
            </span>
            <div>
              <strong>{file.name}</strong>
              <span>{formatFileSize(file.size)}</span>
            </div>
          </div>
          <button
            className="button button--ghost"
            type="button"
            disabled={isSubmitting}
            onClick={() => selectFile(null)}
          >
            Remover
          </button>
        </div>
      ) : null}

      {submitError ? (
        <div className="inline-message inline-message--error" role="alert">
          <strong>Envio não confirmado.</strong>
          <span>{submitError}</span>
          <span>Antes de reenviar, confirme se um job já foi criado caso a conexão tenha caído após o aceite.</span>
        </div>
      ) : null}

      <div className="upload-panel__footer">
        <p>
          O envio termina quando a API aceita o arquivo. O processamento das linhas continua em segundo
          plano.
        </p>
        <button
          className="button button--primary"
          type="button"
          disabled={!file || isSubmitting}
          onClick={handleSubmit}
        >
          {isSubmitting ? 'Enviando arquivo…' : 'Iniciar importação'}
        </button>
      </div>
    </section>
  )
}
