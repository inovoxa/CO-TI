import * as Api from '@/api/assistente'

/**
 * Voz do assistente.
 * - Ouvir: se o servidor transcreve (provedor de IA com modelo de áudio), grava o microfone e para sozinho quando a
 *   pessoa fica em silêncio; senão usa o reconhecimento de fala do navegador (Chrome/Edge).
 * - Falar: a síntese de voz do próprio navegador, em português.
 * O microfone só funciona em página segura (https ou localhost): é regra do navegador.
 */

export type ComoOuvir = 'servidor' | 'navegador' | null

const Reconhecimento: any =
  typeof window === 'undefined'
    ? undefined
    : (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition

export const microfonePermitido = () =>
  typeof window !== 'undefined' && window.isSecureContext && !!navigator.mediaDevices?.getUserMedia

export const podeFalar = () => typeof window !== 'undefined' && 'speechSynthesis' in window

/** Como esta página consegue ouvir; null = não consegue (e o motivo em {@link motivoSemVoz}) */
export const comoOuvir = (voz?: Api.Voz): ComoOuvir => {
  if (!microfonePermitido()) return null
  if (voz?.transcricao && typeof MediaRecorder !== 'undefined') return 'servidor'
  if (Reconhecimento) return 'navegador'
  return null
}

export const motivoSemVoz = (voz?: Api.Voz) => {
  if (typeof window !== 'undefined' && !window.isSecureContext) {
    return 'O navegador só libera o microfone em endereço seguro: abra o CO-TI por https (ou em localhost no próprio servidor).'
  }
  if (!voz?.transcricao && !Reconhecimento) {
    return 'Este navegador não reconhece fala e o servidor não tem transcrição: configure um modelo de áudio em Atendimento → Inteligência artificial, ou use Chrome/Edge.'
  }
  return 'Microfone indisponível neste navegador.'
}

export interface Escuta {
  /** texto falado ('' se a pessoa não disse nada) */
  resultado: Promise<string>
  /** encerra agora e usa o que já foi dito */
  parar: () => void
  /** desiste: descarta o que foi dito */
  cancelar: () => void
}

interface Opcoes {
  /** texto parcial enquanto a pessoa fala (só no reconhecimento do navegador) */
  aoParcial?: (t: string) => void
  /** nível do som de 0 a 1, para animar o botão */
  aoVolume?: (v: number) => void
  /** a gravação terminou e o texto está sendo preparado */
  aoProcessar?: () => void
}

export function ouvir(como: Exclude<ComoOuvir, null>, op: Opcoes = {}): Escuta {
  return como === 'servidor' ? ouvirGravando(op) : ouvirNavegador(op)
}

// silêncio que encerra a fala, tempo máximo esperando começar e duração máxima
const SILENCIO_MS = 1500
const ESPERA_MS = 8000
const MAXIMO_MS = 90000

function ouvirGravando(op: Opcoes): Escuta {
  let encerrar: (descartar: boolean) => void = () => {}
  const resultado = new Promise<string>((resolve, reject) => {
    navigator.mediaDevices
      .getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true } })
      .then((stream) => {
        const tipo = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/webm'].find((t) =>
          MediaRecorder.isTypeSupported(t)
        )
        const gravador = new MediaRecorder(stream, tipo ? { mimeType: tipo } : undefined)
        const pedacos: Blob[] = []
        const ctx = new AudioContext()
        const analisador = ctx.createAnalyser()
        analisador.fftSize = 512
        ctx.createMediaStreamSource(stream).connect(analisador)
        const amostras = new Uint8Array(analisador.fftSize)
        const inicio = Date.now()
        let falou = false
        let ultimoSom = Date.now()
        let descartar = false
        let quadro = 0

        const medir = () => {
          analisador.getByteTimeDomainData(amostras)
          let soma = 0
          for (const a of amostras) soma += ((a - 128) / 128) ** 2
          const nivel = Math.min(1, Math.sqrt(soma / amostras.length) * 6)
          op.aoVolume?.(nivel)
          const agora = Date.now()
          if (nivel > 0.12) {
            falou = true
            ultimoSom = agora
          }
          if (
            (falou && agora - ultimoSom > SILENCIO_MS) ||
            (!falou && agora - inicio > ESPERA_MS) ||
            agora - inicio > MAXIMO_MS
          ) {
            encerrar(false)
            return
          }
          quadro = requestAnimationFrame(medir)
        }

        encerrar = (d: boolean) => {
          descartar = d || !falou
          cancelAnimationFrame(quadro)
          if (gravador.state !== 'inactive') gravador.stop()
        }
        gravador.ondataavailable = (e) => e.data.size && pedacos.push(e.data)
        gravador.onstop = async () => {
          stream.getTracks().forEach((t) => t.stop())
          ctx.close()
          op.aoVolume?.(0)
          if (descartar || !pedacos.length) {
            resolve('')
            return
          }
          op.aoProcessar?.()
          try {
            resolve((await Api.transcrever(new Blob(pedacos, { type: gravador.mimeType }))).trim())
          } catch (e) {
            reject(e)
          }
        }
        gravador.start(250)
        medir()
      })
      .catch(reject)
  })
  return { resultado, parar: () => encerrar(false), cancelar: () => encerrar(true) }
}

function ouvirNavegador(op: Opcoes): Escuta {
  const rec = new Reconhecimento()
  rec.lang = 'pt-BR'
  rec.interimResults = true
  rec.continuous = false
  rec.maxAlternatives = 1
  let final = ''
  let parcial = ''
  let descartar = false
  const resultado = new Promise<string>((resolve, reject) => {
    rec.onresult = (e: any) => {
      parcial = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i]
        if (r.isFinal) final += r[0].transcript
        else parcial += r[0].transcript
      }
      op.aoParcial?.((final + ' ' + parcial).trim())
      op.aoVolume?.(0.6)
    }
    rec.onerror = (e: any) => {
      if (e.error === 'no-speech' || e.error === 'aborted') return
      reject(new Error(e.error === 'not-allowed' ? 'O navegador não liberou o microfone.' : e.error))
    }
    rec.onend = () => {
      op.aoVolume?.(0)
      resolve(descartar ? '' : (final + ' ' + parcial).trim())
    }
    rec.start()
  })
  return {
    resultado,
    parar: () => rec.stop(),
    cancelar: () => {
      descartar = true
      rec.abort()
    }
  }
}

// ================================================================================================ falar

let vozEscolhida: SpeechSynthesisVoice | null | undefined

const escolherVoz = () => {
  if (vozEscolhida !== undefined && vozEscolhida !== null) return vozEscolhida
  const vozes = window.speechSynthesis.getVoices().filter((v) => v.lang.toLowerCase().startsWith('pt'))
  const preferidas = [/francisca/i, /thalita/i, /maria/i, /google.*portugu/i, /luciana/i]
  vozEscolhida =
    preferidas.map((p) => vozes.find((v) => p.test(v.name) && /br/i.test(v.lang))).find(Boolean) ||
    vozes.find((v) => /br/i.test(v.lang)) ||
    vozes[0] ||
    null
  return vozEscolhida
}

if (podeFalar()) {
  window.speechSynthesis.onvoiceschanged = () => {
    vozEscolhida = undefined
  }
}

/** Lê o texto em voz alta; termina quando acaba de falar (ou ao calar). */
export function falar(texto: string): Promise<void> {
  if (!podeFalar() || !texto) return Promise.resolve()
  window.speechSynthesis.cancel()
  // o Chrome corta falas longas: uma frase por vez
  const frases = texto.match(/[^.!?;:]+[.!?;:]*/g)?.map((f) => f.trim()).filter(Boolean) ?? [texto]
  const voz = escolherVoz()
  return new Promise((resolve) => {
    frases.forEach((f, i) => {
      const u = new SpeechSynthesisUtterance(f)
      u.lang = voz?.lang || 'pt-BR'
      if (voz) u.voice = voz
      u.rate = 1.05
      if (i === frases.length - 1) {
        u.onend = () => resolve()
        u.onerror = () => resolve()
      }
      window.speechSynthesis.speak(u)
    })
  })
}

export function calar() {
  if (podeFalar()) window.speechSynthesis.cancel()
}
