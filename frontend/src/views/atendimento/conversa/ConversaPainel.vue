<template>
  <div class="painel">
    <template v-if="conversa">
      <!-- cabeçalho -->
      <div class="cabecalho">
        <div class="min-w-0 flex-1">
          <div class="truncate font-bold">{{ conversa.contato }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            #{{ conversa.id }} · {{ conversa.caixa }}
            <template v-if="conversa.telefone"> · {{ conversa.telefone }}</template>
            <el-tag
              v-if="conversa.status !== 0"
              class="ml-6px"
              disable-transitions
              size="small"
              type="info"
              >{{ conversa.statusRotulo }}</el-tag
            >
            <el-tag
              v-if="janelaFechada"
              class="ml-6px"
              disable-transitions
              size="small"
              type="warning"
              >mais de 24 h sem mensagem do contato</el-tag
            >
          </div>
        </div>
        <el-select
          :model-value="conversa.agenteId"
          class="!w-180px"
          clearable
          filterable
          placeholder="Sem atendente"
          size="small"
          @change="trocarAgente"
        >
          <el-option v-for="a in agentes" :key="a.userId" :label="a.nome" :value="a.userId">
            <span class="dot" :class="{ on: a.disponivel }"></span>{{ a.nome }}
          </el-option>
        </el-select>
        <el-button v-if="conversa.status !== 1" size="small" type="success" @click="status(1)">
          <Icon class="mr-4px" icon="ep:circle-check" />Resolver
        </el-button>
        <el-button v-else size="small" @click="status(0)"
          ><Icon class="mr-4px" icon="ep:refresh-left" />Reabrir</el-button
        >
        <el-dropdown trigger="click" @command="acao">
          <el-button size="small"><Icon icon="ep:more-filled" /></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="adiar-1h">Adiar 1 hora</el-dropdown-item>
              <el-dropdown-item command="adiar-amanha">Adiar até amanhã 8h</el-dropdown-item>
              <el-dropdown-item command="pendente">Deixar pendente</el-dropdown-item>
              <el-dropdown-item
                v-for="p in PRIORIDADES"
                :key="p.valor"
                :command="'prio-' + p.valor"
                :divided="p.valor === 1"
                >Prioridade {{ p.rotulo.toLowerCase() }}</el-dropdown-item
              >
              <el-dropdown-item command="prio-0">Sem prioridade</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- mensagens -->
      <div ref="rolagem" class="mensagens" @scroll="aoRolar">
        <div v-if="temMais" class="text-center"
          ><el-button link size="small" @click="anteriores">Mensagens anteriores</el-button></div
        >
        <template v-for="m in mensagens" :key="m.id">
          <div v-if="m.tipo === 2" class="atividade">{{ m.conteudo }} · {{ hora(m.criadaEm) }}</div>
          <div v-else class="linha" :class="{ saida: m.tipo !== 0 }">
            <div
              class="balao"
              :class="{ nota: m.privada, saida: m.tipo !== 0, falhou: m.status === 'falhou' }"
            >
              <div v-if="m.tipo !== 0 || m.privada" class="autor">
                <Icon v-if="m.privada" class="mr-2px" icon="ep:lock" :size="11" /><Icon
                  v-if="m.dados?.origem === 'celular'"
                  class="mr-2px"
                  icon="ep:iphone"
                  :size="11"
                />{{ m.privada ? 'Nota interna de ' : '' }}{{ m.remetente }}
                <span v-if="m.dados?.modelo" class="marca">modelo {{ m.dados.modelo.nome }}</span>
              </div>
              <div v-if="m.dados?.historico" class="marca mb-2px">histórico do WhatsApp</div>
              <!-- texto escapado em formatarTexto: nenhuma marcação de fora passa -->
              <div
                v-if="m.conteudo"
                class="texto"
                :class="{ reacao: m.formato === 'reacao' }"
                v-html="formatarTexto(m.conteudo)"
              ></div>
              <div v-for="(a, i) in m.anexos" :key="i" class="anexo">
                <a
                  v-if="(a.tipo || '').startsWith('image/')"
                  :href="a.url"
                  rel="noopener"
                  target="_blank"
                  ><img :alt="a.nome" :src="a.url"
                /></a>
                <audio
                  v-else-if="(a.tipo || '').startsWith('audio/')"
                  :src="a.url"
                  controls
                  preload="none"
                ></audio>
                <video
                  v-else-if="(a.tipo || '').startsWith('video/')"
                  :src="a.url"
                  controls
                  preload="none"
                ></video>
                <a v-else :href="a.url" rel="noopener" target="_blank"
                  ><Icon class="mr-4px" icon="ep:paperclip" />{{ a.nome || 'anexo' }}</a
                >
              </div>
              <div class="rodape">
                {{ hora(m.criadaEm) }}
                <template v-if="m.tipo !== 0 && !m.privada">
                  <Icon v-if="m.status === 'pendente'" icon="ep:clock" :size="11" title="Na fila" />
                  <Icon
                    v-else-if="m.status === 'enviada'"
                    icon="ep:check"
                    :size="11"
                    title="Enviada"
                  />
                  <Icon
                    v-else-if="m.status === 'entregue' || m.status === 'lida'"
                    :class="{ lida: m.status === 'lida' }"
                    icon="ep:finished"
                    :size="12"
                    :title="m.status === 'lida' ? 'Lida' : 'Entregue'"
                  />
                </template>
              </div>
              <div v-if="m.status === 'falhou'" class="erro">
                Não entregue: {{ m.erro }}
                <el-button link size="small" type="primary" @click="tentarDeNovo(m)"
                  >tentar de novo</el-button
                >
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- resposta -->
      <div class="resposta" :class="{ nota: modo === 'nota' }">
        <div class="mb-6px flex items-center gap-8px">
          <el-radio-group v-model="modo" size="small">
            <el-radio-button value="responder">Responder</el-radio-button>
            <el-radio-button value="nota">Nota interna</el-radio-button>
          </el-radio-group>
          <span class="text-12px text-[var(--el-text-color-secondary)]"
            >Enter envia · Shift+Enter quebra linha · "/" respostas prontas</span
          >
        </div>
        <el-alert
          v-if="janelaFechada && modo === 'responder'"
          :closable="false"
          class="mb-6px"
          show-icon
          type="warning"
        >
          <template v-if="conversa.canal === 'whatsapp_cloud'"
            >Passaram 24 horas desde a última mensagem do contato: o WhatsApp só aceita um
            <el-button link type="primary" @click="abrirModelo">modelo aprovado</el-button
            >.</template
          >
          <template v-else
            >Passaram 24 horas desde a última mensagem do contato: o Instagram pode recusar a
            resposta.</template
          >
        </el-alert>
        <div v-if="sugestoes.length" class="sugestoes">
          <button
            v-for="(r, i) in sugestoes"
            :key="r.id"
            :class="{ ativa: i === sugestaoAtiva }"
            type="button"
            @mousedown.prevent="usarResposta(r)"
          >
            <b>/{{ r.atalho }}</b> {{ r.conteudo }}
          </button>
        </div>
        <el-input
          ref="campo"
          v-model="texto"
          :autosize="{ minRows: 2, maxRows: 8 }"
          :disabled="conversa.status === 1 && modo === 'responder'"
          :maxlength="4096"
          :placeholder="
            conversa.status === 1 && modo === 'responder'
              ? 'Conversa resolvida: reabra para responder'
              : modo === 'nota'
                ? 'Só a equipe vê'
                : 'Mensagem para o contato'
          "
          type="textarea"
          @keydown="teclas"
        />
        <div v-if="anexos.length" class="mt-6px flex flex-wrap gap-6px">
          <el-tag
            v-for="(a, i) in anexos"
            :key="a.url"
            closable
            disable-transitions
            @close="anexos.splice(i, 1)"
            >{{ a.nome }}</el-tag
          >
        </div>
        <div class="mt-6px flex items-center">
          <el-upload
            :before-upload="anexar"
            :show-file-list="false"
            action="#"
            :http-request="() => {}"
          >
            <el-button link size="small"
              ><Icon class="mr-2px" icon="ep:paperclip" />Anexar</el-button
            >
          </el-upload>
          <el-button
            v-if="conversa.canal === 'whatsapp_cloud' && modo === 'responder'"
            link
            size="small"
            @click="abrirModelo"
            ><Icon class="mr-2px" icon="ep:document" />Modelo</el-button
          >
          <el-button
            :loading="enviando"
            class="ml-auto"
            size="small"
            type="primary"
            @click="enviar"
          >
            {{ modo === 'nota' ? 'Salvar nota' : 'Enviar' }}
          </el-button>
        </div>
      </div>
    </template>
    <el-empty v-else class="m-auto" description="Escolha uma conversa" />

    <Dialog v-model="modelo.visivel" title="Mandar modelo aprovado" width="560px">
      <el-empty
        v-if="!modelo.lista.length"
        description="Nenhum modelo aprovado. Peça para sincronizar os modelos na configuração da caixa."
        :image-size="60"
      />
      <el-form v-else label-position="top">
        <el-form-item label="Modelo">
          <el-select v-model="modelo.id" class="w-full" filterable @change="escolherModelo">
            <el-option
              v-for="t in modelo.lista"
              :key="t.id"
              :label="`${t.nome} (${t.idioma})`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-for="(n, i) in modeloEscolhido?.nomesVariaveis || []"
          :key="n"
          :label="'Variável {{' + n + '}}'"
        >
          <el-input v-model="modelo.parametros[i]" maxlength="1000" />
        </el-form-item>
        <el-form-item v-if="modeloEscolhido" label="Como o contato vai ver">
          <div class="previa" v-html="formatarTexto(previaModelo)"></div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          :disabled="!modeloEscolhido || modelo.parametros.some((p) => !p?.trim())"
          :loading="modelo.enviando"
          type="primary"
          @click="enviarModelo"
          >Enviar</el-button
        >
        <el-button @click="modelo.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import { PRIORIDADES, formatarTexto } from '@/api/atendimento'
import * as FileApi from '@/api/infra/file'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ConversaPainel' })
const props = defineProps<{
  conversaId?: number
  agentesDaCaixa: (caixaId: number) => Atd.Agente[]
}>()
const emit = defineEmits<{ alterado: [] }>()
const message = useMessage()

const conversa = ref<Atd.ConversaResumo>()
const mensagens = ref<Atd.Mensagem[]>([])
const temMais = ref(false)
const texto = ref('')
const modo = ref<'responder' | 'nota'>('responder')
const anexos = ref<Atd.Anexo[]>([])
const enviando = ref(false)
const respostas = ref<Atd.RespostaPronta[]>([])
const sugestaoAtiva = ref(0)
const rolagem = ref<HTMLElement>()
const campo = ref()
let colado = true

const agentes = computed(() => (conversa.value ? props.agentesDaCaixa(conversa.value.caixaId) : []))
const janelaFechada = computed(
  () =>
    !!conversa.value?.janela && (!conversa.value.janelaAte || conversa.value.janelaAte < Date.now())
)
const modelo = reactive({
  visivel: false,
  lista: [] as Atd.Modelo[],
  id: undefined as number | undefined,
  parametros: [] as string[],
  enviando: false
})
const modeloEscolhido = computed(() => modelo.lista.find((t) => t.id === modelo.id))
const previaModelo = computed(() => {
  const t = modeloEscolhido.value
  if (!t) return ''
  return t.corpo.replace(/\{\{\s*([A-Za-z0-9_]+)\s*}}/g, (x, n) => {
    const i = t.nomesVariaveis.indexOf(n)
    return i >= 0 && modelo.parametros[i]?.trim() ? modelo.parametros[i] : x
  })
})
const sugestoes = computed(() => {
  const m = /^\/([a-z0-9_-]*)$/.exec(texto.value)
  return m ? respostas.value.filter((r) => r.atalho.startsWith(m[1])).slice(0, 6) : []
})
const hora = (t: number) => formatDate(new Date(t), 'DD/MM HH:mm')

const descer = () =>
  nextTick(() => {
    if (rolagem.value) rolagem.value.scrollTop = rolagem.value.scrollHeight
  })
const aoRolar = () => {
  const r = rolagem.value!
  colado = r.scrollHeight - r.scrollTop - r.clientHeight < 80
}

const carregar = async (id: number) => {
  const [c, m] = await Promise.all([Atd.getConversa(id), Atd.getMensagens(id)])
  conversa.value = c
  mensagens.value = m
  temMais.value = m.length === 50
  colado = true
  descer()
  Atd.marcarLida(id)
}
watch(
  () => props.conversaId,
  (id) => {
    texto.value = ''
    anexos.value = []
    modo.value = 'responder'
    if (id) carregar(id)
    else conversa.value = undefined
  },
  { immediate: true }
)

const anteriores = async () => {
  const r = rolagem.value!
  const altura = r.scrollHeight
  const m = await Atd.getMensagens(conversa.value!.id, mensagens.value[0]?.id)
  mensagens.value = [...m, ...mensagens.value]
  temMais.value = m.length === 50
  nextTick(() => (r.scrollTop = r.scrollHeight - altura))
}

/** Mensagem nova ou atualizada (tempo real). */
const receber = (m: Atd.Mensagem) => {
  if (m.conversaId !== conversa.value?.id) return
  const i = mensagens.value.findIndex((x) => x.id === m.id)
  if (i >= 0) mensagens.value.splice(i, 1, m)
  else {
    mensagens.value.push(m)
    if (colado) descer()
    if (m.tipo === 0) Atd.marcarLida(m.conversaId)
  }
}
const atualizar = (c: Atd.ConversaResumo) => {
  if (c.id === conversa.value?.id) conversa.value = { ...conversa.value, ...c }
}
defineExpose({ receber, atualizar })

const enviar = async () => {
  if (!texto.value.trim() && !anexos.value.length) return
  enviando.value = true
  try {
    await Atd.responder({
      conversaId: conversa.value!.id,
      conteudo: texto.value,
      privada: modo.value === 'nota',
      anexos: anexos.value
    })
    texto.value = ''
    anexos.value = []
    colado = true
    emit('alterado')
  } finally {
    enviando.value = false
  }
}

const teclas = (e: KeyboardEvent) => {
  if (sugestoes.value.length) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const n = sugestoes.value.length
      sugestaoAtiva.value = (sugestaoAtiva.value + (e.key === 'ArrowDown' ? 1 : n - 1)) % n
      return
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault()
      usarResposta(sugestoes.value[Math.min(sugestaoAtiva.value, sugestoes.value.length - 1)])
      return
    }
  }
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    enviar()
  }
}
const usarResposta = (r: Atd.RespostaPronta) => {
  texto.value = r.conteudo
  sugestaoAtiva.value = 0
  campo.value?.focus()
}

const anexar = async (arquivo: File) => {
  if (arquivo.size > 16 * 1024 * 1024) {
    message.warning('Arquivo maior que 16 MB')
    return false
  }
  const url: string = await FileApi.updateFile({ file: arquivo, directory: 'atendimento' })
  anexos.value.push({ url, nome: arquivo.name, tipo: arquivo.type, tamanho: arquivo.size })
  return false
}

const trocarAgente = async (agenteId?: number) => {
  await Atd.atribuir(conversa.value!.id, agenteId || undefined)
  emit('alterado')
}
const status = async (s: number, adiadaAte?: string) => {
  await Atd.mudarStatus(conversa.value!.id, s, adiadaAte)
  emit('alterado')
}
const acao = async (cmd: string) => {
  if (cmd === 'adiar-1h')
    return status(3, formatDate(new Date(Date.now() + 3600_000), 'YYYY-MM-DD HH:mm:ss'))
  if (cmd === 'adiar-amanha') {
    const d = new Date()
    d.setDate(d.getDate() + 1)
    d.setHours(8, 0, 0, 0)
    return status(3, formatDate(d, 'YYYY-MM-DD HH:mm:ss'))
  }
  if (cmd === 'pendente') return status(2)
  if (cmd.startsWith('prio-')) {
    const p = Number(cmd.slice(5))
    await Atd.definirPrioridade(conversa.value!.id, p || undefined)
    emit('alterado')
  }
}
const abrirModelo = async () => {
  modelo.lista = await Atd.getModelos(conversa.value!.caixaId)
  modelo.id = undefined
  modelo.parametros = []
  modelo.visivel = true
}
const escolherModelo = () => {
  modelo.parametros = (modeloEscolhido.value?.nomesVariaveis || []).map(() => '')
}
const enviarModelo = async () => {
  modelo.enviando = true
  try {
    await Atd.enviarModelo({
      conversaId: conversa.value!.id,
      modeloId: modelo.id!,
      parametros: modelo.parametros
    })
    modelo.visivel = false
    colado = true
    emit('alterado')
  } finally {
    modelo.enviando = false
  }
}
const tentarDeNovo = async (m: Atd.Mensagem) => {
  await Atd.reenviar(m.conversaId, m.id)
  m.status = 'pendente'
}

onMounted(async () => {
  respostas.value = await Atd.getRespostas()
})
</script>

<style scoped>
.painel {
  display: flex;
  height: 100%;
  min-width: 0;
  flex-direction: column;
}
.cabecalho {
  display: flex;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  gap: 8px;
}
.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  background: var(--el-border-color);
  border-radius: 50%;
}
.dot.on {
  background: var(--el-color-success);
}
.mensagens {
  padding: 12px 16px;
  overflow-y: auto;
  background: var(--el-fill-color-lighter);
  flex: 1;
}
.atividade {
  margin: 8px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.linha {
  display: flex;
  margin: 6px 0;
}
.linha.saida {
  justify-content: flex-end;
}
.balao {
  max-width: 72%;
  padding: 8px 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}
.balao.saida {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}
.balao.nota {
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-5);
}
.balao.falhou {
  border-color: var(--el-color-danger);
}
.autor {
  margin-bottom: 2px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
.texto {
  overflow-wrap: anywhere;
}
.texto :deep(code) {
  padding: 0 3px;
  font-family: monospace;
  background: var(--el-fill-color);
  border-radius: 3px;
}
.texto.reacao {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.marca {
  display: inline-block;
  padding: 0 5px;
  margin-left: 4px;
  font-size: 10px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 3px;
}
.previa {
  width: 100%;
  padding: 8px 10px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
}
.anexo video {
  max-width: 300px;
  max-height: 240px;
  margin-top: 4px;
  border-radius: 6px;
}
.anexo img {
  max-width: 260px;
  max-height: 220px;
  margin-top: 4px;
  border-radius: 6px;
}
.rodape {
  display: flex;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  justify-content: flex-end;
  align-items: center;
  gap: 3px;
}
.rodape .lida {
  color: var(--el-color-primary);
}
.erro {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}
.resposta {
  position: relative;
  padding: 10px 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.resposta.nota {
  background: var(--el-color-warning-light-9);
}
.sugestoes {
  position: absolute;
  right: 14px;
  bottom: 100%;
  left: 14px;
  z-index: 10;
  overflow: hidden;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  box-shadow: var(--el-box-shadow-light);
}
.sugestoes button {
  display: block;
  width: 100%;
  padding: 6px 10px;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  background: none;
  border: 0;
}
.sugestoes button.ativa,
.sugestoes button:hover {
  background: var(--el-fill-color-light);
}
</style>
