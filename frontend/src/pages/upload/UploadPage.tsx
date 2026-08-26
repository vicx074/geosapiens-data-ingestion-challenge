import { UploadPanel } from '../../features/imports/components/UploadPanel'

const flowSteps = [
  {
    number: '01',
    title: 'Envio eficiente',
    description: 'O navegador entrega o arquivo; o processamento pesado permanece no backend.',
  },
  {
    number: '02',
    title: 'Processamento assíncrono',
    description: 'O job segue em segundo plano, com estado durável e acompanhamento pela interface.',
  },
  {
    number: '03',
    title: 'Consulta em escala',
    description: 'Analytics e listagens usam respostas limitadas, sem transportar milhões de linhas.',
  },
]

export function UploadPage() {
  return (
    <div className="page page--upload">
      <div className="upload-layout">
        <section className="intro" aria-labelledby="upload-page-title">
          <p className="eyebrow">Ingestão em larga escala</p>
          <h1 id="upload-page-title">Dados grandes, fluxo simples.</h1>
          <p className="intro__description">
            Envie um CSV grande sem carregar suas linhas no React. Depois do aceite, acompanhe apenas o
            estado durável que o backend confirma.
          </p>
          <div className="intro__note">
            <span aria-hidden="true">↳</span>
            <p>Upload, fila e processamento têm responsabilidades separadas para manter a interface responsiva.</p>
          </div>
        </section>

        <UploadPanel />
      </div>

      <section className="flow" aria-labelledby="flow-title">
        <div className="section-heading">
          <p className="section-heading__label">Como o fluxo trabalha</p>
          <h2 id="flow-title">Do arquivo à análise, sem mover o processamento pesado para o browser.</h2>
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
