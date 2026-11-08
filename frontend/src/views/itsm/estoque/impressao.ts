import type { Item, Movimento } from '@/api/itsm/estoque'

/** Impressão pelo navegador, que também oferece "Salvar como PDF". Todo dado passa por escapar. */
const escapar = (s: unknown) =>
  String(s ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!
  )

const data = (d?: string) => (d ? d.split('-').reverse().join('/') : '')

const ESTILO = `
  body { font: 13px/1.5 Arial, sans-serif; color: #000; margin: 24px; }
  h1 { font-size: 18px; text-align: center; margin: 0 0 20px; }
  table { width: 100%; border-collapse: collapse; margin: 12px 0; }
  th, td { border: 1px solid #888; padding: 5px 7px; text-align: left; vertical-align: top; }
  th { background: #eee; }
  .assinaturas { display: flex; gap: 40px; margin-top: 70px; }
  .assinaturas div { flex: 1; border-top: 1px solid #000; padding-top: 4px; text-align: center; }
  .linha { border-bottom: 1px solid #000; display: inline-block; min-width: 260px; }
`

export const imprimirHtml = (corpo: string) => {
  const quadro = document.createElement('iframe')
  // fora da tela, e não display:none: alguns navegadores imprimem em branco um quadro oculto
  quadro.style.cssText = 'position:fixed;left:-10000px;top:0;width:1000px;height:800px;border:0'
  // sandbox sem scripts: o conteúdo vem do banco; a impressão é chamada daqui
  quadro.setAttribute('sandbox', 'allow-same-origin allow-modals')
  document.body.appendChild(quadro)
  quadro.srcdoc = `<!doctype html><html><head><meta charset="utf-8"><title>Estoque GTI</title><style>${ESTILO}</style></head><body>${corpo}</body></html>`
  quadro.onload = () => {
    quadro.contentWindow?.focus()
    quadro.contentWindow?.print()
    setTimeout(() => quadro.remove(), 60000)
  }
}

export const termoHtml = (m: Movimento) => {
  const retirada = m.tipo === 'retirada'
  const quando = new Date(m.quando).toLocaleDateString('pt-BR')
  const linhas: [string, unknown][] = [
    ['Tipo', m.tipoItem],
    ['Descrição', m.item],
    ['IMEI, ICCID ou número de série', m.identificador],
    ['Linha', m.linha],
    ['Patrimônio', m.patrimonio],
    ['Quantidade', m.quantidade],
    ['Chamado', m.chamadoId ? `#${m.chamadoId}` : '']
  ]
  const texto = retirada
    ? `Declaro ter recebido o bem abaixo, de propriedade do órgão, para uso exclusivo em serviço. Comprometo-me a
       zelar por sua guarda e conservação, a comunicar de imediato à área de TI qualquer defeito, perda, furto ou roubo e a
       devolvê-lo quando solicitado ou ao deixar a função.`
    : `Declaro ter devolvido à área de TI o bem abaixo, na data indicada.`
  return `
    <h1>Termo de ${retirada ? 'responsabilidade' : 'devolução'} de equipamento</h1>
    <p>Servidor(a): <b>${escapar(m.pessoa)}</b>${m.destino ? ` &nbsp; Setor/Unidade: <b>${escapar(m.destino)}</b>` : ''}</p>
    <p>Matrícula: <span class="linha">&nbsp;</span></p>
    <p>${texto}</p>
    <table>${linhas
      .filter(([, v]) => v)
      .map(([r, v]) => `<tr><th>${escapar(r)}</th><td>${escapar(v)}</td></tr>`)
      .join('')}</table>
    ${m.motivo ? `<p>Observação: ${escapar(m.motivo)}</p>` : ''}
    <p>Data: ${escapar(quando)}</p>
    <div class="assinaturas">
      <div>${escapar(m.pessoa)}</div>
      <div>Responsável pela entrega${m.registradoPor ? `: ${escapar(m.registradoPor)}` : ''}</div>
    </div>`
}

const SITUACAO: Record<string, string> = {
  disponivel: 'Disponível',
  em_uso: 'Em uso',
  manutencao: 'Em manutenção',
  baixado: 'Baixado'
}

export const relatorioHtml = (itens: Item[]) => `
  <h1>Estoque GTI</h1>
  <p>${escapar(new Date().toLocaleString('pt-BR'))} · ${itens.length} itens</p>
  <table>
    <tr><th>Tipo</th><th>Descrição</th><th>Identificação</th><th>Condição</th><th>Situação</th><th>Saldo</th><th>Com quem está</th><th>Garantia</th></tr>
    ${itens
      .map(
        (i) => `<tr><td>${escapar(i.tipo)}</td><td>${escapar(i.descricao)}</td>
          <td>${escapar([i.identificador, i.linha, i.patrimonio].filter(Boolean).join(' · '))}</td>
          <td>${i.condicao === 'novo' ? 'Novo' : 'Usado'}</td><td>${escapar(SITUACAO[i.situacao || ''])}</td>
          <td>${i.controle === 'quantidade' ? escapar(i.quantidade) : ''}</td>
          <td>${escapar([i.responsavel, i.destino].filter(Boolean).join(' · '))}</td><td>${escapar(data(i.garantiaAte))}</td></tr>`
      )
      .join('')}
  </table>`
