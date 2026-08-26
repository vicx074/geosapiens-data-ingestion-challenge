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
      <section className="intro" aria-labelledby="upload-page-title">
        <p className="eyebrow">Ingestão em larga escala</p>
        <h1 id="upload-page-title">Dados grandes, fluxo simples.</h1>
        <p className="intro__description">
          Uma interface operacional para enviar datasets CSV, acompanhar o processamento e analisar o
          resultado sem transformar volume em ruído para o usuário.
        </p>
      </section>

      <section className="flow" aria-labelledby="flow-title">
        <div className="section-heading">
          <p className="section-heading__label">Fluxo planejado</p>
          <h2 id="flow-title">Do arquivo à análise, com responsabilidades claras.</h2>
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
