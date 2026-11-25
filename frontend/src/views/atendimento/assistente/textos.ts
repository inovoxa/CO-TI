// Prévia dos textos do assistente virtual: as mesmas regras de TextosAssistente.preencher (backend).

export const EXEMPLOS: Record<string, string> = {
  chamado: '1234',
  exemplo: '1234',
  titulo: 'Impressora do protocolo não imprime',
  situacao: 'Em atendimento',
  aberto_em: '20/11/2026',
  solucionado_em: '',
  ultima_autor: 'Carlos (Service Desk)',
  ultima_data: '21/11/2026 14:32',
  ultima_texto: 'Troquei o toner. Pode testar e me avisar?',
  texto: 'Troquei o toner. Pode testar e me avisar?',
  aprovador: 'Maria Souza',
  solicitante: 'João Lima',
  link: `${window.location.origin}/aprovacoes`
}

/** Troca {variavel}; some a linha cuja variável ficou vazia; desconhecida fica como está. */
export const preencher = (modelo: string | undefined, valores: Record<string, string>) => {
  const linhas: string[] = []
  for (const linha of (modelo || '').split('\n')) {
    let vazia = false
    const l = linha.replace(/\{([a-z_]+)\}/g, (m, nome: string) => {
      if (!(nome in valores)) return m
      const v = valores[nome] ?? ''
      if (!v.trim()) vazia = true
      return v
    })
    if (!vazia) linhas.push(l)
  }
  return linhas
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

const escapar = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

/** *negrito*, _itálico_ e ~riscado~ como o WhatsApp mostra (o texto é escapado antes). */
export const formatarWhats = (texto: string) =>
  escapar(texto)
    .replace(/\*([^*\n]+)\*/g, '<b>$1</b>')
    .replace(/(^|[\s(])_([^_\n]+)_/g, '$1<i>$2</i>')
    .replace(/~([^~\n]+)~/g, '<s>$1</s>')
