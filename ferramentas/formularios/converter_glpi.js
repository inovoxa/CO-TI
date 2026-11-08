#!/usr/bin/env node
// Converte um formulário exportado do GLPI 11 para o formato de importação do CO-TI, com perguntas, opções e condições.
// Não leva responsáveis, grupos, aprovadores nem o token de acesso direto.
//
//   node ferramentas/formularios/converter_glpi.js exportado.json saida.json opcoes.json
//
// opcoes.json: { codigo, nome, grupo, categoria, icon, descricao, titulo_modelo, destaque, ordem,
//                pergunta_sistemas (início do texto da pergunta de sistemas),
//                superior: { pergunta (início do texto), label },
//                textos: { "texto original": "texto corrigido" } }
const fs = require('fs')

const [, , entrada, saida, arquivoOpcoes] = process.argv
if (!entrada || !saida || !arquivoOpcoes) {
  console.error('Uso: converter_glpi.js exportado.json saida.json opcoes.json')
  process.exit(2)
}
const op = JSON.parse(fs.readFileSync(arquivoOpcoes, 'utf8'))
const form = JSON.parse(fs.readFileSync(entrada, 'utf8')).forms[0]

const semHtml = (h) =>
  (h || '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&#34;|&quot;/g, '"')
    .replace(/&amp;/g, '&')
    .replace(/\s+/g, ' ')
    .trim()
const corrigir = (t) => {
  const limpo = t.replace(/\s+/g, ' ').trim()
  return (op.textos && op.textos[limpo]) || limpo
}
// ";" separa as respostas de múltipla escolha no CO-TI
const opcao = (t) => corrigir(t).replace(/;/g, ',')
const tipoGlpi = (q) => q.type.split('\\').pop().replace('QuestionType', '')
const extra = (q) => (typeof q.extra_data === 'string' ? JSON.parse(q.extra_data) : q.extra_data) || {}

const rotuloOpcao = {}
const nomes = {}
const usados = new Set()
const nomeTecnico = (texto) => {
  const base =
    texto
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^[^a-z]+|_+$/g, '')
      .slice(0, 48)
      .replace(/_+$/, '') || 'pergunta'
  let nome = base
  for (let n = 2; usados.has(nome); n++) nome = `${base}_${n}`
  usados.add(nome)
  return nome
}

const comeca = (q, inicio) => q.name.startsWith(inicio)
const sistemas = form.questions.find((q) => comeca(q, op.pergunta_sistemas))
if (!sistemas) throw new Error('Pergunta de sistemas não encontrada')
for (const q of form.questions) {
  const e = extra(q)
  if (e.options) for (const [id, t] of Object.entries(e.options)) rotuloOpcao[id] = opcao(t)
  nomes[q.uuid] = comeca(q, op.superior.pergunta) ? 'email_superior' : nomeTecnico(q.name)
}
const opcoesSistemas = Object.entries(extra(sistemas).options)

const condicoes = (lista) =>
  (lista || [])
    .filter((c) => c.item_type === 'question')
    .map((c) => {
      if (c.logic_operator && c.logic_operator !== 'and') throw new Error('Condição OU não suportada')
      if (!['equals', 'contains'].includes(c.value_operator)) throw new Error('Operador: ' + c.value_operator)
      return {
        campo: nomes[c.item_uuid],
        valores: [].concat(c.value).map((v) => {
          if (!rotuloOpcao[v]) throw new Error('Opção desconhecida: ' + v)
          return rotuloOpcao[v]
        })
      }
    })

// No GLPI, seção de sistema sempre visível obriga todo mundo a responder; aqui ela só vale para o próprio sistema.
const condicoesDaSecao = (s) => {
  const c = condicoes(s.conditions)
  const numero = (s.name.match(/^(\d+)\./) || [])[1]
  const dela = numero && opcoesSistemas.find(([, t]) => t.trim().startsWith(numero + '.'))
  if (s.visibility_strategy === 'always_visible' && dela) {
    console.log('Seção sempre visível passou a depender do sistema: ' + s.name)
    c.push({ campo: nomes[sistemas.uuid], valores: [opcao(dela[1])] })
  }
  return c
}

const contagem = {}
const pergunta = (q, daSecao) => {
  const e = extra(q)
  const g = tipoGlpi(q)
  const base = { name: nomes[q.uuid], label: corrigir(q.name), required: !!q.is_mandatory }
  contagem[g] = (contagem[g] || 0) + 1
  if (comeca(q, op.superior.pergunta)) {
    return { ...base, name: 'email_superior', label: op.superior.label, type: 'email', required: true }
  }
  const valores = () => [...new Set(Object.values(e.options || {}).map(opcao))]
  switch (g) {
    case 'Number': Object.assign(base, { type: 'number' }); break
    case 'ShortText': Object.assign(base, { type: 'text' }); break
    case 'LongText': Object.assign(base, { type: 'textarea' }); break
    case 'Email': Object.assign(base, { type: 'email' }); break
    case 'DateTime': Object.assign(base, { type: 'date' }); break
    case 'ItemDropdown': Object.assign(base, { type: 'location' }); break
    case 'Item': Object.assign(base, { type: 'usuario' }); break
    case 'Radio': Object.assign(base, { type: 'radios', values: valores() }); break
    case 'Checkbox': Object.assign(base, { type: 'multipla', values: valores() }); break
    case 'Dropdown':
      Object.assign(base, { type: e.is_multiple_dropdown ? 'multipla' : 'select', values: valores() })
      break
    default:
      throw new Error('Tipo não tratado: ' + g + ' em ' + q.name)
  }
  const ajuda = semHtml(q.description)
  if (ajuda) base.ajuda = corrigir(ajuda)
  const visivelSe = [...daSecao, ...condicoes(q.conditions)]
  if (visivelSe.length) base.visivelSe = visivelSe
  return base
}

const secoes = [...form.sections]
  .sort((a, b) => a.rank - b.rank)
  .map((s) => {
    const daSecao = condicoesDaSecao(s)
    return {
      nome: s.name.trim(),
      perguntas: form.questions
        .filter((q) => q.section_id === s.id)
        .sort((a, b) => a.vertical_rank - b.vertical_rank || a.horizontal_rank - b.horizontal_rank)
        .map((q) => pergunta(q, daSecao))
    }
  })

const total = secoes.reduce((n, s) => n + s.perguntas.length, 0)
if (total !== form.questions.length) throw new Error(`Perdeu perguntas: ${total} de ${form.questions.length}`)

fs.writeFileSync(
  saida,
  JSON.stringify(
    {
      _meta: { origem: 'Exportação de formulário do GLPI 11, convertida por ferramentas/formularios/converter_glpi.js' },
      formularios: [
        {
          codigo: op.codigo,
          nome: op.nome,
          descricao: op.descricao || semHtml(form.description),
          grupo: op.grupo,
          categoria: op.categoria,
          icon: op.icon,
          ticket: { type: 2, urgency: 2, impact: 2 },
          titulo_modelo: op.titulo_modelo,
          aprovacao: 'campo',
          aprovador_campo: 'email_superior',
          destaque: !!op.destaque,
          ordem: op.ordem ?? 0,
          secoes
        }
      ]
    },
    null,
    1
  ) + '\n'
)
console.log(`Seções: ${secoes.length}. Perguntas: ${total}. Tipos: ${JSON.stringify(contagem)}`)
