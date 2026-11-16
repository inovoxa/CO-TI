import type { Unidade } from '@/api/monitoramento'

/** Minúsculas e sem acento, para "sao" achar "São". */
export const simples = (s?: string | null) =>
  (s || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()

/** Como o servidor compara ao salvar: sem acento e sem diferença de maiúsculas ou de espaços. */
export const chaveNome = (s?: string | null) => simples(s).trim().replace(/\s+/g, ' ')

/** Só letras e números: "U.B.S. Centro" e "UBS - Centro" ficam iguais. */
const chaveForte = (s?: string | null) => simples(s).replace(/[^a-z0-9]/g, '')

export const temPonto = (u: Unidade) =>
  u.latitude != null &&
  u.longitude != null &&
  !(Number(u.latitude) === 0 && Number(u.longitude) === 0)

/** Distância em metros entre os pontos das duas unidades. */
const distancia = (a: Unidade, b: Unidade) => {
  const rad = (g: number) => (g * Math.PI) / 180
  const dLat = rad(Number(b.latitude) - Number(a.latitude))
  const dLng = rad(Number(b.longitude) - Number(a.longitude))
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(Number(a.latitude))) * Math.cos(rad(Number(b.latitude))) * Math.sin(dLng / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.sqrt(h))
}

/** Quantas letras é preciso trocar, pôr ou tirar para um virar o outro (para no limite). */
const edicoes = (a: string, b: string, limite: number) => {
  if (Math.abs(a.length - b.length) > limite) return limite + 1
  let ant = Array.from({ length: b.length + 1 }, (_, j) => j)
  for (let i = 1; i <= a.length; i++) {
    const atual = [i]
    let menor = i
    for (let j = 1; j <= b.length; j++) {
      atual[j] = Math.min(
        ant[j] + 1,
        atual[j - 1] + 1,
        ant[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)
      )
      menor = Math.min(menor, atual[j])
    }
    if (menor > limite) return limite + 1
    ant = atual
  }
  return ant[b.length]
}

/** Até onde dois pontos ainda contam como o mesmo lugar. */
const PERTO_METROS = 50

/**
 * Por que as duas parecem a mesma unidade; vazio se não parecem.
 * @param folgado também aceita nomes em que um contém o outro ou com até 2 letras de diferença: serve para avisar
 *   enquanto se digita, não para apontar repetidas na lista
 */
export const motivos = (a: Unidade, b: Unidade, folgado = false): string[] => {
  const m: string[] = []
  const na = chaveForte(a.nome)
  const nb = chaveForte(b.nome)
  // "Escola 1" e "Escola 2" são unidades diferentes: número diferente no nome nunca é erro de digitação
  const mesmosNumeros = na.replace(/\D/g, ' ').trim() === nb.replace(/\D/g, ' ').trim()
  if (na && na === nb) m.push('mesmo nome')
  else if (!mesmosNumeros) {
    // nomes numerados diferentes: só o endereço ou o mapa podem apontar repetição
  } else if (
    na.length >= 6 &&
    nb.length >= 6 &&
    edicoes(na, nb, folgado ? 2 : 1) <= (folgado ? 2 : 1)
  )
    m.push('nome quase igual')
  else if (folgado && na.length >= 5 && nb.length >= 5 && (na.includes(nb) || nb.includes(na)))
    m.push('nome parecido')
  const ca = (a.cep || '').replace(/\D/g, '')
  const numA = chaveForte(a.numero)
  if (
    ca.length === 8 &&
    numA &&
    ca === (b.cep || '').replace(/\D/g, '') &&
    numA === chaveForte(b.numero)
  )
    m.push('mesmo CEP e número')
  else if (temPonto(a) && temPonto(b) && distancia(a, b) < PERTO_METROS)
    m.push(`a menos de ${PERTO_METROS} m no mapa`)
  return m
}

export interface Parecida {
  unidade: Unidade
  motivos: string[]
}

/** Para cada unidade, as outras que parecem ser a mesma. */
export const parecidasPorUnidade = (lista: Unidade[]) => {
  const r = new Map<number, Parecida[]>()
  for (let i = 0; i < lista.length; i++) {
    for (let j = i + 1; j < lista.length; j++) {
      const m = motivos(lista[i], lista[j])
      if (!m.length) continue
      const a = lista[i].id!
      const b = lista[j].id!
      r.set(a, [...(r.get(a) || []), { unidade: lista[j], motivos: m }])
      r.set(b, [...(r.get(b) || []), { unidade: lista[i], motivos: m }])
    }
  }
  return r
}

/** Número do grupo de cada unidade repetida, para mostrar as do mesmo grupo juntas. */
export const grupos = (parecidas: Map<number, Parecida[]>) => {
  const grupo = new Map<number, number>()
  let n = 0
  for (const id of parecidas.keys()) {
    if (grupo.has(id)) continue
    const fila = [id]
    while (fila.length) {
      const atual = fila.pop()!
      if (grupo.has(atual)) continue
      grupo.set(atual, n)
      parecidas.get(atual)?.forEach((p) => fila.push(p.unidade.id!))
    }
    n++
  }
  return grupo
}
