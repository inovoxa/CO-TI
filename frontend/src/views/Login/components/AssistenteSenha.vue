<!-- "Esqueci minha senha": conversa com o assistente da TI, que abre o pedido 1.2 (reset de senha) do catálogo -->
<template>
  <el-drawer
    v-model="aberto"
    :size="largura"
    :with-header="false"
    append-to-body
    class="assistente-senha"
    @opened="aoAbrir"
  >
    <div class="assistente">
      <header class="assistente__topo">
        <div class="assistente__avatar"><Icon icon="ep:key" :size="20" /></div>
        <div class="min-w-0 flex-1">
          <div class="text-16px font-600">Esqueci minha senha</div>
          <div class="text-12px opacity-80">Assistente da TI · pedido de reset de senha (1.2)</div>
        </div>
        <el-tooltip content="Recomeçar a conversa">
          <el-button circle text @click="recomecar"><Icon icon="ep:refresh-right" /></el-button>
        </el-tooltip>
        <el-button circle text @click="aberto = false"><Icon icon="ep:close" /></el-button>
      </header>

      <div ref="listaRef" class="assistente__lista">
        <div
          v-for="m in mensagens"
          :key="m.id"
          :class="['balao', m.de === 'voce' ? 'balao--voce' : 'balao--ti']"
        >
          <div v-if="m.de === 'atendente'" class="balao__autor">Equipe de TI</div>
          <!-- texto escapado; só *negrito*, _itálico_ e quebras de linha viram HTML -->
          <div class="balao__texto" v-html="formatar(m.texto)"></div>
          <div class="balao__hora">{{ hora(m.em) }}</div>
        </div>
        <div
          v-if="esperando"
          class="balao balao--ti balao--digitando"
          aria-label="O assistente está escrevendo"
        >
          <span></span><span></span><span></span>
        </div>
        <el-alert
          v-if="erro"
          :closable="false"
          :title="erro"
          class="mt-8px"
          show-icon
          type="error"
        />
      </div>

      <div v-if="opcoes.length" class="assistente__opcoes">
        <el-button
          v-for="o in opcoes"
          :key="o.valor"
          :disabled="enviando"
          round
          size="small"
          @click="enviar(o.valor)"
          >{{ o.rotulo }}</el-button
        >
      </div>

      <form class="assistente__entrada" @submit.prevent="enviar()">
        <el-input
          ref="campoRef"
          v-model="texto"
          :disabled="!token"
          :maxlength="1000"
          autocomplete="off"
          placeholder="Escreva a resposta"
          size="large"
        />
        <el-button
          :disabled="!texto.trim() || !token"
          :loading="enviando"
          native-type="submit"
          size="large"
          type="primary"
          ><Icon icon="ep:promotion"
        /></el-button>
      </form>
      <p class="assistente__aviso">
        Nunca informe a sua senha atual. Depois do envio, CPF e outros dados pessoais ficam ocultos
        nesta conversa.
      </p>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import {
  enviarMensagem,
  iniciarVisita,
  listarMensagens,
  type MensagemWidget
} from '@/api/atendimento/widget'

defineOptions({ name: 'AssistenteSenha' })

const aberto = defineModel<boolean>({ default: false })

// a visita sobrevive a um recarregar da página (mesma aba), não ao fechar o navegador
const CHAVE = 'coti-assistente-senha'
const lerToken = () => {
  try {
    return sessionStorage.getItem(CHAVE) || ''
  } catch {
    return ''
  }
}
const guardarToken = (t: string) => {
  try {
    if (t) sessionStorage.setItem(CHAVE, t)
    else sessionStorage.removeItem(CHAVE)
  } catch {
    // navegação privada
  }
}

const token = ref(lerToken())
const mensagens = ref<MensagemWidget[]>([])
const texto = ref('')
const enviando = ref(false)
const erro = ref('')
const listaRef = ref<HTMLElement>()
const campoRef = ref()
const largura = computed(() => (window.innerWidth < 520 ? '100%' : '440px'))
let timer: number | undefined

const ultima = computed(() => mensagens.value[mensagens.value.length - 1])
/** a última fala é do visitante: o assistente ainda vai responder */
const esperando = computed(() => enviando.value || ultima.value?.de === 'voce')

/** "1️⃣ Sim" ou "1) Leitura" na última fala do assistente viram botões */
const opcoes = computed(() => {
  const m = ultima.value
  if (!m || m.de === 'voce') return []
  const lista: { valor: string; rotulo: string }[] = []
  for (const linha of (m.texto || '').split('\n')) {
    const r = linha.trim().match(/^([1-9])(?:️?⃣|\))\s*(.+)$/)
    if (r) lista.push({ valor: r[1], rotulo: `${r[1]} · ${r[2].replace(/[*_]/g, '')}` })
  }
  return lista.slice(0, 6)
})

const escapar = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const formatar = (s: string) =>
  escapar(s || '')
    .replace(/\*([^*\n]+)\*/g, '<strong>$1</strong>')
    .replace(/(^|\s)_([^_\n]+)_/g, '$1<em>$2</em>')
    .replace(/\n/g, '<br>')

// o backend manda a data como milissegundos (padrão do yudao); aceita texto ISO também
const hora = (em: string | number) => {
  const d = new Date(typeof em === 'number' ? em : String(em || '').replace(' ', 'T'))
  return isNaN(d.getTime())
    ? ''
    : d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
}

const rolar = async () => {
  await nextTick()
  listaRef.value?.scrollTo({ top: listaRef.value.scrollHeight, behavior: 'smooth' })
}

const buscar = async () => {
  if (!token.value) return
  try {
    const depois = ultima.value?.id || 0
    const novas = await listarMensagens(token.value, depois)
    if (novas.length) {
      mensagens.value.push(...novas.filter((n) => !mensagens.value.some((m) => m.id === n.id)))
      rolar()
    }
    erro.value = ''
  } catch {
    erro.value = 'Sem conexão com o servidor. Tentando de novo...'
  }
}

const enviar = async (valor?: string) => {
  const t = (valor ?? texto.value).trim()
  if (!t || !token.value || enviando.value) return
  enviando.value = true
  try {
    await enviarMensagem(token.value, t)
    if (valor === undefined) texto.value = ''
    await buscar()
  } finally {
    enviando.value = false
    campoRef.value?.focus()
  }
}

const comecar = async () => {
  mensagens.value = []
  erro.value = ''
  try {
    token.value = await iniciarVisita()
    guardarToken(token.value)
    await enviar('Esqueci minha senha')
  } catch {
    erro.value = 'O assistente não está disponível agora. Procure a equipe de TI.'
  }
}

const recomecar = () => {
  guardarToken('')
  token.value = ''
  comecar()
}

const aoAbrir = async () => {
  if (!token.value) {
    await comecar()
  } else if (!mensagens.value.length) {
    await buscar()
  }
  rolar()
  campoRef.value?.focus()
}

// consulta as respostas enquanto a janela está aberta
watch(
  aberto,
  (v) => {
    window.clearInterval(timer)
    if (v) timer = window.setInterval(buscar, 1500)
  },
  { immediate: true }
)
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style lang="scss" scoped>
.assistente {
  display: flex;
  height: 100%;
  flex-direction: column;

  &__topo {
    display: flex;
    padding: 14px 12px 14px 18px;
    color: #fff;
    background: linear-gradient(120deg, #2ba177, #3a6fb0 60%, #6a5fb0);
    align-items: center;
    gap: 12px;

    :deep(.el-button) {
      color: #fff;
    }
  }

  &__avatar {
    display: flex;
    width: 38px;
    height: 38px;
    background: rgb(255 255 255 / 20%);
    border-radius: 50%;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__lista {
    display: flex;
    padding: 16px;
    overflow-y: auto;
    background: var(--el-fill-color-lighter);
    flex: 1;
    flex-direction: column;
    gap: 8px;
  }

  &__opcoes {
    display: flex;
    padding: 10px 12px 0;
    border-top: 1px solid var(--el-border-color-lighter);
    flex-wrap: wrap;
    gap: 6px;

    .el-button + .el-button {
      margin-left: 0;
    }
  }

  &__entrada {
    display: flex;
    padding: 12px;
    gap: 8px;
  }

  &__aviso {
    padding: 0 14px 12px;
    margin: 0;
    font-size: 11px;
    line-height: 1.4;
    color: var(--el-text-color-secondary);
  }
}

.balao {
  max-width: 85%;
  padding: 9px 12px 6px;
  font-size: 14px;
  line-height: 1.45;
  overflow-wrap: anywhere;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 6%);

  &--ti {
    color: var(--el-text-color-primary);
    background: var(--el-bg-color);
    border-bottom-left-radius: 4px;
    align-self: flex-start;
  }

  &--voce {
    color: #fff;
    background: var(--el-color-primary);
    border-bottom-right-radius: 4px;
    align-self: flex-end;

    .balao__hora {
      color: rgb(255 255 255 / 75%);
    }
  }

  &__autor {
    margin-bottom: 2px;
    font-size: 12px;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__hora {
    margin-top: 2px;
    font-size: 10px;
    color: var(--el-text-color-secondary);
    text-align: right;
  }

  &--digitando {
    display: flex;
    padding: 12px 14px;
    gap: 4px;

    span {
      width: 7px;
      height: 7px;
      background: var(--el-text-color-placeholder);
      border-radius: 50%;
      animation: digitando 1.2s infinite ease-in-out;

      &:nth-child(2) {
        animation-delay: 0.15s;
      }

      &:nth-child(3) {
        animation-delay: 0.3s;
      }
    }
  }
}

@keyframes digitando {
  0%,
  80%,
  100% {
    opacity: 0.3;
    transform: translateY(0);
  }

  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}
</style>

<style lang="scss">
.assistente-senha .el-drawer__body {
  padding: 0;
}
</style>
