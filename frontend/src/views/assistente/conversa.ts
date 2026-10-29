import MarkdownIt from 'markdown-it'
import * as Api from '@/api/assistente'

/** Conversa com o assistente: usada pela página "Perguntar" e pela janela flutuante da barra superior. */

export const EXEMPLOS = [
  'Quantos computadores ainda usam Windows 10?',
  'Quais máquinas estão com o antivírus desatualizado?',
  'Resuma a fila do Service Desk',
  'Quais licenças estão com mais instalações que o comprado?',
  'Algum link fora do ar agora?'
]

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
export const markdown = (t: string) => md.render(t ?? '')

/** Texto puro para ler em voz alta (sem Markdown, tabelas viram frases separadas) */
export const textoFalado = (t: string) => {
  const div = document.createElement('div')
  div.innerHTML = md.render(t ?? '')
  div.querySelectorAll('li, p, tr, h1, h2, h3, h4').forEach((e) => e.append('. '))
  div.querySelectorAll('td, th').forEach((e) => e.append(', '))
  return (div.textContent || '')
    .replace(/#(\d+)/g, 'número $1')
    .replace(/\s*([.,])(\s*[.,])+/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()
}

const FERRAMENTAS_LOGS = ['ranking_logs', 'contar_logs', 'buscar_logs']

/**
 * Link para a tela "Detalhes da pesquisa" a partir das consultas de logs que o assistente fez: vale a última de
 * ranking (é a que responde "quem mais..."), senão a última contagem ou busca. undefined = a resposta não usou logs.
 */
export const detalheLogs = (consultas?: Api.Consulta[], pergunta?: string) => {
  const logs = (consultas ?? []).filter((c) => FERRAMENTAS_LOGS.includes(c.ferramenta))
  if (!logs.length) return undefined
  const principal = [...logs].reverse().find((c) => c.ferramenta === 'ranking_logs') ?? logs[logs.length - 1]
  const a = principal.argumentos ?? {}
  const distinto = [...logs].reverse().find((c) => c.argumentos?.distinto)?.argumentos.distinto
  const texto = (v: any) => (v === undefined || v === null || v === '' ? undefined : String(v))
  // medir: quantidade (contar) | bytes ou banda | sentbyte | rcvdbyte
  const medir = String(a.medir ?? '').toLowerCase()
  const soma = !medir || medir === 'quantidade' ? undefined : medir === 'banda' ? 'bytes' : medir
  // como na ferramenta: banda soma só os logs de sessão (o webfilter repetiria o mesmo acesso)
  let consulta = texto(a.consulta)
  if (soma && !/\b(sub)?type:/.test(consulta ?? '')) consulta = consulta ? `type:traffic AND (${consulta})` : 'type:traffic'
  return {
    name: 'LogsAnalise',
    query: {
      q: consulta,
      fluxo: texto(a.fluxo) ?? (soma ? 'firewall' : undefined),
      periodo: texto(a.periodo) ?? (a.de ? undefined : '1d'),
      de: texto(a.de),
      ate: texto(a.ate),
      campo: texto(principal.ferramenta === 'ranking_logs' ? a.campo : undefined),
      distinto: texto(distinto),
      soma,
      pergunta: pergunta ? pergunta.slice(0, 300) : undefined
    }
  }
}

export const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : ''

export function useConversa(aoMudar?: () => void) {
  const sessaoId = ref<number>()
  const mensagens = ref<Api.Mensagem[]>([])
  const enviando = ref(false)

  const abrir = async (id: number) => {
    sessaoId.value = id
    mensagens.value = await Api.getMensagens(id)
    aoMudar?.()
  }

  const nova = () => {
    sessaoId.value = undefined
    mensagens.value = []
  }

  /**
   * Envia a pergunta e acrescenta a resposta.
   * @return a resposta, ou undefined se falhou (o erro já aparece pelo interceptador)
   */
  const enviar = async (texto: string, falada = false): Promise<Api.Resposta | undefined> => {
    const t = texto.trim()
    if (!t || enviando.value) return
    mensagens.value.push({ id: -Date.now(), papel: 'user', conteudo: t, criadaEm: new Date().toISOString() })
    enviando.value = true
    aoMudar?.()
    try {
      const r = await Api.perguntar(t, sessaoId.value, falada)
      sessaoId.value = r.sessaoId
      mensagens.value.push({
        id: -Date.now() - 1,
        papel: 'assistant',
        conteudo: r.resposta,
        erro: r.erro,
        consultas: r.consultas,
        criadaEm: new Date().toISOString()
      })
      return r
    } catch {
      mensagens.value.pop()
      return undefined
    } finally {
      enviando.value = false
      aoMudar?.()
    }
  }

  return { sessaoId, mensagens, enviando, abrir, nova, enviar }
}
