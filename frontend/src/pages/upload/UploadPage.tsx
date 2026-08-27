import { UploadPanel } from '../../features/imports/components/UploadPanel'

const flowSteps = [
  {
    number: '01',
    title: 'Recebido',
    description: 'O arquivo é armazenado sem ser carregado por inteiro na aplicação.',
  },
  {
    number: '02',
    title: 'Processado',
    description: 'O Worker lê o CSV em stream e confirma os registros em batches.',
  },
  {
    number: '03',
    title: 'Disponível',
    description: 'Status, registros e agregações ficam acessíveis pela mesma interface.',
  },
]

export function UploadPage() {
  return (
    <div className="page page--upload">
      <div className="upload-layout">
        <section className="intro" aria-labelledby="upload-page-title">
          <p className="eyebrow">Processamento assíncrono</p>
          <h1 id="upload-page-title">Um arquivo. Milhões de registros.</h1>
          <p className="intro__description">
            Envie o CSV e acompanhe o processamento sem manter uma requisição aberta.
          </p>
          <div className="intro__note">
            <span aria-hidden="true">→</span>
            <p>Leitura em stream · persistência em batches · consultas paginadas</p>
          </div>
        </section>

        <UploadPanel />
      </div>

      <section className="flow" aria-labelledby="flow-title">
        <div className="section-heading">
          <p className="section-heading__label">Fluxo</p>
          <h2 id="flow-title">Do upload ao resultado.</h2>
        </div>

        <div className="flow-grid">
          {flowSteps.map((step) => (
            <article className="flow-card" key={step.number}>
              <span className="flow-card__number">{step.number}</span>
              <h3>{step.title}</h3>
              <p>{step.description}</p>
            </article>
          ))}
        </div>
      </section>
    </div>
  )
}
