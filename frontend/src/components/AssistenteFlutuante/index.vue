<!-- Assistente de IA em janela flutuante (canto inferior direito), aberto pelo ícone da barra superior.
     Dois modos: texto (digita e lê) e voz (fala e ouve a resposta). A conversa continua ao trocar de tela. -->
<template>
  <transition name="assistente-flutuante">
    <div v-show="assistenteAberto" class="assistente-flutuante" role="dialog" aria-label="Assistente de IA">
      <!-- topo -->
      <div class="topo">
        <div class="titulo">
          <span class="avatar"><Icon icon="ep:magic-stick" :size="16" /></span>
          <div class="min-w-0">
            <div class="font-bold leading-18px">Assistente de IA</div>
            <div class="sub">{{ subtitulo }}</div>
          </div>
        </div>
        <div class="flex items-center gap-2px">
          <el-tooltip content="Nova conversa" placement="bottom">
            <el-button link :disabled="enviando" @click="novaConversa"><Icon icon="ep:plus" /></el-button>
          </el-tooltip>
          <el-tooltip content="Abrir com o histórico de conversas" placement="bottom">
            <el-button link @click="abrirPagina"><Icon icon="ep:full-screen" /></el-button>
          </el-tooltip>
          <el-tooltip content="Fechar" placement="bottom">
            <el-button link @click="fechar"><Icon icon="ep:close" /></el-button>
          </el-tooltip>
        </div>
      </div>
      <div class="modos">
        <el-radio-group v-model="modo" size="small" @change="trocarModo">
          <el-radio-button value="texto"><Icon class="mr-4px" icon="ep:chat-line-round" />Texto</el-radio-button>
          <el-radio-button value="voz"><Icon class="mr-4px" icon="ep:microphone" />Voz</el-radio-button>
        </el-radio-group>
        <el-checkbox v-if="modo === 'voz' && como" v-model="maosLivres" size="small">
          Continuar ouvindo
        </el-checkbox>
      </div>

      <!-- mensagens -->
      <div ref="rolagem" class="mensagens">
        <div v-if="!mensagens.length" class="vazio">
          <div class="mb-6px text-15px font-bold">{{ saudacao }}, {{ primeiroNome }}!</div>
          <div class="mb-12px text-12px text-[var(--el-text-color-secondary)]">
            Pergunte sobre inventário, chamados, problemas, mudanças, conversas, base de conhecimento,
            licenças, pessoas, links e logs. Respondo só com o que você tem permissão para ver.
          </div>
          <div class="flex flex-col items-stretch gap-6px">
            <el-button
              v-for="e in EXEMPLOS"
              :key="e"
              class="exemplo"
              round
              size="small"
              :disabled="enviando"
              @click="perguntar(e)"
              >{{ e }}</el-button
            >
          </div>
          <el-alert
            v-if="voz && !voz.ia"
            class="mt-12px"
            :closable="false"
            show-icon
            title="A IA está desligada ou sem provedor: configure em Atendimento → Inteligência artificial."
            type="warning"
          />
        </div>
        <div v-for="(m, i) in mensagens" :key="m.id" :class="['msg', m.papel, { erro: m.erro }]">
          <div v-if="m.papel === 'user'" class="balao">{{ m.conteudo }}</div>
          <div v-else class="balao">
            <div v-dompurify-html="markdown(m.conteudo)" class="md"></div>
            <div v-if="m.consultas?.length" class="consultas">
              <Icon class="mr-4px" icon="ep:search" />Consultou:
              <el-tag
                v-for="(c, j) in m.consultas"
                :key="j"
                class="ml-4px"
                disable-transitions
                size="small"
                type="info"
                >{{ CONSULTAS[c.ferramenta] ?? c.ferramenta }}</el-tag
              >
            </div>
            <el-button
              v-if="detalheLogs(m.consultas)"
              class="mt-6px"
              plain
              size="small"
              type="primary"
              @click="verDetalhes(i)"
            >
              <Icon class="mr-4px" icon="ep:data-analysis" />Ver detalhes da pesquisa
            </el-button>
          </div>
        </div>
        <div v-if="enviando" class="msg assistant">
          <div class="balao text-[var(--el-text-color-secondary)]">
            <Icon class="is-loading mr-6px" icon="ep:loading" />Consultando…
          </div>
        </div>
      </div>

      <!-- entrada: texto -->
      <div v-if="modo === 'texto'" class="entrada">
        <el-input
          ref="campo"
          v-model="texto"
          :autosize="{ minRows: 1, maxRows: 4 }"
          :disabled="enviando"
          maxlength="4000"
          placeholder="Pergunte algo (Enter envia)"
          resize="none"
          type="textarea"
          @keydown.enter.exact.prevent="perguntar()"
        />
        <el-button :disabled="!texto.trim()" :loading="enviando" type="primary" @click="perguntar()">
          <Icon icon="ep:promotion" />
        </el-button>
      </div>

      <!-- entrada: voz -->
      <div v-else class="voz">
        <template v-if="como">
          <button
            :class="['microfone', estadoVoz]"
            :style="{ '--nivel': nivel }"
            :aria-label="rotuloVoz"
            :disabled="estadoVoz === 'transcrevendo' || estadoVoz === 'pensando'"
            @click="tocarMicrofone"
          >
            <Icon
              :class="{ 'is-loading': estadoVoz === 'transcrevendo' || estadoVoz === 'pensando' }"
              :icon="iconeVoz"
              :size="26"
            />
          </button>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ rotuloVoz }}</div>
          <div v-if="parcial" class="parcial">“{{ parcial }}”</div>
        </template>
        <el-alert v-else :closable="false" show-icon :title="motivoSemVoz(voz)" type="info" />
      </div>
    </div>
  </transition>
</template>

<script lang="ts" setup>
import * as Api from '@/api/assistente'
import { CONSULTAS } from '@/api/assistente'
import { EXEMPLOS, detalheLogs, markdown, textoFalado, useConversa } from '@/views/assistente/conversa'
import { useUserStore } from '@/store/modules/user'
import { assistenteAberto } from './estado'
import { calar, comoOuvir, type Escuta, falar, motivoSemVoz, ouvir } from './voz'

defineOptions({ name: 'AssistenteFlutuante' })

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()

const primeiroNome = computed(() => (userStore.getUser?.nickname || '').split(' ')[0] || 'tudo bem')
const h = new Date().getHours()
const saudacao = h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite'

const rolagem = ref<HTMLElement>()
const campo = ref()
const descer = () =>
  nextTick(() => rolagem.value && (rolagem.value.scrollTop = rolagem.value.scrollHeight))
const { mensagens, enviando, nova, enviar } = useConversa(descer)

// preferências deste navegador
const lerPref = (k: string, padrao: string) => {
  try {
    return localStorage.getItem('coti.assistente.' + k) ?? padrao
  } catch {
    return padrao
  }
}
const gravarPref = (k: string, v: string) => {
  try {
    localStorage.setItem('coti.assistente.' + k, v)
  } catch {
    /* sem armazenamento: só não lembra */
  }
}
const modo = ref<'texto' | 'voz'>(lerPref('modo', 'texto') === 'voz' ? 'voz' : 'texto')
const maosLivres = ref(lerPref('maos-livres', '1') === '1')
watch(maosLivres, (v) => gravarPref('maos-livres', v ? '1' : '0'))

// ======================================================================================= texto

const texto = ref('')

const perguntar = async (pergunta?: string) => {
  const t = (pergunta ?? texto.value).trim()
  if (!t || enviando.value) return
  texto.value = ''
  const falada = modo.value === 'voz'
  if (falada) estadoVoz.value = 'pensando'
  const r = await enviar(t, falada)
  if (!r) {
    texto.value = falada ? '' : t
    estadoVoz.value = 'parado'
    return
  }
  if (falada && modo.value === 'voz' && assistenteAberto.value) await responderFalando(r.resposta)
}

// ======================================================================================= voz

const voz = ref<Api.Voz>()
const como = computed(() => comoOuvir(voz.value))
type EstadoVoz = 'parado' | 'ouvindo' | 'transcrevendo' | 'pensando' | 'falando'
const estadoVoz = ref<EstadoVoz>('parado')
const nivel = ref(0)
const parcial = ref('')
let escuta: Escuta | null = null

const rotuloVoz = computed(
  () =>
    ({
      parado: 'Toque no microfone e fale',
      ouvindo: 'Ouvindo… toque para enviar',
      transcrevendo: 'Entendendo o que você disse…',
      pensando: 'Consultando…',
      falando: 'Respondendo… toque para interromper'
    })[estadoVoz.value]
)
const iconeVoz = computed(
  () =>
    ({
      parado: 'ep:microphone',
      ouvindo: 'ep:microphone',
      transcrevendo: 'ep:loading',
      pensando: 'ep:loading',
      falando: 'ep:mute-notification'
    })[estadoVoz.value]
)

const subtitulo = computed(() =>
  modo.value === 'texto'
    ? 'Consulta os dados do CO-TI para você'
    : estadoVoz.value === 'parado'
      ? 'Conversa por voz'
      : rotuloVoz.value
)

const escutar = async () => {
  if (!como.value || enviando.value) return
  calar()
  parcial.value = ''
  estadoVoz.value = 'ouvindo'
  escuta = ouvir(como.value, {
    aoParcial: (t) => (parcial.value = t),
    aoVolume: (v) => (nivel.value = v),
    aoProcessar: () => (estadoVoz.value = 'transcrevendo')
  })
  let dito = ''
  try {
    dito = await escuta.resultado
  } catch (e: any) {
    if (e?.name === 'NotAllowedError') message.warning('O navegador não liberou o microfone.')
    else if (e?.message) message.warning(e.message)
    estadoVoz.value = 'parado'
    return
  } finally {
    escuta = null
    nivel.value = 0
  }
  parcial.value = ''
  if (!dito || modo.value !== 'voz' || !assistenteAberto.value) {
    estadoVoz.value = 'parado'
    return
  }
  await perguntar(dito)
}

const responderFalando = async (resposta: string) => {
  estadoVoz.value = 'falando'
  await falar(textoFalado(resposta))
  if (estadoVoz.value !== 'falando') return // interrompido
  estadoVoz.value = 'parado'
  if (maosLivres.value && modo.value === 'voz' && assistenteAberto.value) escutar()
}

const tocarMicrofone = () => {
  if (estadoVoz.value === 'ouvindo') escuta?.parar()
  else if (estadoVoz.value === 'falando') {
    estadoVoz.value = 'parado'
    calar()
  } else if (estadoVoz.value === 'parado') escutar()
}

const pararVoz = () => {
  escuta?.cancelar()
  escuta = null
  calar()
  parcial.value = ''
  if (estadoVoz.value === 'ouvindo' || estadoVoz.value === 'falando') estadoVoz.value = 'parado'
}

const carregarVoz = async () => {
  if (voz.value) return
  try {
    voz.value = await Api.getVoz()
  } catch {
    /* sem permissão ou fora do ar: o modo voz mostra o motivo */
  }
}

const trocarModo = () => {
  gravarPref('modo', modo.value)
  if (modo.value === 'texto') {
    pararVoz()
    nextTick(() => campo.value?.focus())
  }
}

// ======================================================================================= janela

const novaConversa = () => {
  pararVoz()
  nova()
  texto.value = ''
}

const fechar = () => {
  assistenteAberto.value = false
}

const abrirPagina = () => {
  fechar()
  router.push({ name: 'AssistenteInterno' })
}

watch(assistenteAberto, (aberto) => {
  if (aberto) {
    carregarVoz()
    if (modo.value === 'texto') nextTick(() => campo.value?.focus())
    descer()
  } else {
    pararVoz()
  }
})

const aoTecla = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && assistenteAberto.value) fechar()
}
onMounted(() => window.addEventListener('keydown', aoTecla))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', aoTecla)
  pararVoz()
})

/** Abre a tela "Detalhes da pesquisa" com a consulta de logs da resposta (a pergunta vem da mensagem anterior) */
const verDetalhes = (i: number) => {
  const anterior = mensagens.value[i - 1]
  const destino = detalheLogs(mensagens.value[i].consultas, anterior?.papel === 'user' ? anterior.conteudo : undefined)
  if (destino) router.push(destino)
}
</script>

<style lang="scss" scoped>
.assistente-flutuante {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 2000;
  display: flex;
  width: 400px;
  height: min(620px, calc(100vh - 90px));
  overflow: hidden;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  box-shadow: var(--el-box-shadow-dark);
  flex-direction: column;
}

@media (width <= 520px) {
  .assistente-flutuante {
    right: 12px;
    bottom: 12px;
    width: calc(100vw - 24px);
    height: calc(100vh - 80px);
  }
}

.topo {
  display: flex;
  padding: 10px 10px 8px 14px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.titulo {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.avatar {
  display: inline-flex;
  width: 32px;
  height: 32px;
  color: #fff;
  background: var(--el-color-primary);
  border-radius: 50%;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
}

.sub {
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modos {
  display: flex;
  padding: 0 14px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: space-between;
}

.mensagens {
  flex: 1;
  min-height: 0;
  padding: 12px 12px 4px;
  overflow-y: auto;
}

.vazio {
  padding: 8px 4px;
  text-align: center;
}

.exemplo {
  height: auto;
  min-height: 26px;
  margin: 0 !important;
  white-space: normal;
}

.msg {
  display: flex;
  margin-bottom: 10px;

  &.user {
    justify-content: flex-end;

    .balao {
      color: #fff;
      white-space: pre-wrap;
      background: var(--el-color-primary);
    }
  }

  &.assistant .balao {
    background: var(--el-fill-color-light);
  }

  &.erro .balao {
    color: var(--el-color-danger);
  }
}

.balao {
  max-width: 88%;
  padding: 7px 11px;
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
  border-radius: 10px;
}

.md {
  :deep(p) {
    margin: 0 0 6px;
  }

  :deep(table) {
    display: block;
    max-width: 100%;
    margin: 6px 0;
    overflow-x: auto;
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 3px 6px;
    border: 1px solid var(--el-border-color);
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 18px;
    margin: 4px 0;
  }
}

.consultas {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.entrada {
  display: flex;
  padding: 10px 12px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  gap: 8px;
  align-items: flex-end;
}

.voz {
  display: flex;
  min-height: 132px;
  padding: 14px 14px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.microfone {
  --nivel: 0;

  display: inline-flex;
  width: 64px;
  height: 64px;
  color: #fff;
  cursor: pointer;
  background: var(--el-color-primary);
  border: none;
  border-radius: 50%;
  outline: none;
  box-shadow: 0 0 0 calc(4px + var(--nivel) * 18px) var(--el-color-primary-light-8);
  transition:
    box-shadow 0.08s linear,
    background 0.2s;
  align-items: center;
  justify-content: center;

  &:focus-visible {
    outline: 2px solid var(--el-color-primary-dark-2);
    outline-offset: 3px;
  }

  &.ouvindo {
    background: var(--el-color-danger);
    box-shadow: 0 0 0 calc(4px + var(--nivel) * 18px) var(--el-color-danger-light-8);
  }

  &.falando {
    background: var(--el-color-success);
    animation: pulso 1.4s ease-in-out infinite;
  }

  &:disabled {
    cursor: default;
    background: var(--el-color-info);
  }
}

@keyframes pulso {
  50% {
    box-shadow: 0 0 0 12px var(--el-color-success-light-8);
  }
}

.parcial {
  max-width: 100%;
  font-size: 13px;
  font-style: italic;
  text-align: center;
}

.assistente-flutuante-enter-active,
.assistente-flutuante-leave-active {
  transition:
    opacity 0.18s,
    transform 0.18s;
}

.assistente-flutuante-enter-from,
.assistente-flutuante-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}
</style>
