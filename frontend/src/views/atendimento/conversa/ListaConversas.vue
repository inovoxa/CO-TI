<template>
  <div class="lista">
    <div class="topo">
      <div class="flex items-center gap-6px">
        <el-select
          v-model="filtro.caixaId"
          class="flex-1"
          clearable
          placeholder="Todas as caixas"
          size="small"
        >
          <el-option v-for="c in caixas" :key="c.id" :label="c.nome" :value="c.id!" />
        </el-select>
        <el-select v-model="filtro.status" class="!w-120px" size="small">
          <el-option
            v-for="s in STATUS_CONVERSA"
            :key="s.valor"
            :label="s.rotulo"
            :value="s.valor"
          />
        </el-select>
      </div>
      <el-input
        v-model="filtro.busca"
        class="mt-6px"
        clearable
        placeholder="Nome, telefone ou #nº"
        size="small"
      >
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <div class="abas">
        <button
          v-for="a in ABAS"
          :key="a.valor"
          :class="{ ativa: filtro.aba === a.valor }"
          type="button"
          @click="filtro.aba = a.valor"
        >
          {{ a.rotulo }}
          <span v-if="contagens[a.valor] !== undefined" class="n">{{ contagens[a.valor] }}</span>
        </button>
      </div>
    </div>

    <div v-loading="carregando" class="itens">
      <el-empty
        v-if="!carregando && !itens.length"
        :image-size="60"
        description="Nenhuma conversa aqui"
      />
      <button
        v-for="c in itens"
        :key="c.id"
        :class="{ selecionada: c.id === selecionada }"
        class="item"
        type="button"
        @click="emit('abrir', c.id)"
      >
        <span class="avatar" :style="{ background: corAvatar(c.contato) }">{{
          iniciais(c.contato)
        }}</span>
        <span class="corpo">
          <span class="linha1">
            <span class="nome">{{ c.contato }}</span>
            <span class="hora">{{ quando(c.ultimaAtividadeEm) }}</span>
          </span>
          <span class="linha2">
            <Icon
              v-if="c.ultimaMensagemTipo === 1"
              class="mr-2px shrink-0"
              icon="ep:back"
              :size="12"
            />
            <span class="previa">{{ c.ultimaMensagem || '—' }}</span>
            <span v-if="c.naoLidas && c.id !== selecionada" class="badge">{{ c.naoLidas }}</span>
          </span>
          <span class="linha3">
            <span class="canal"
              ><Icon :icon="CANAIS[c.canal || '']?.icone || 'ep:chat-round'" :size="11" />
              {{ c.caixa }}</span
            >
            <span v-if="c.prioridade" class="prio" :class="'p' + c.prioridade">{{
              PRIORIDADES.find((p) => p.valor === c.prioridade)?.rotulo
            }}</span>
            <span v-for="e in c.etiquetas" :key="e.id" class="etq" :title="e.nome">
              <i :style="{ background: e.cor }"></i>{{ e.nome }}
            </span>
          </span>
        </span>
      </button>
      <el-button
        v-if="itens.length < total"
        class="mx-auto my-8px block"
        link
        type="primary"
        @click="mais"
        >Carregar mais</el-button
      >
    </div>
  </div>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import { CANAIS, PRIORIDADES, STATUS_CONVERSA } from '@/api/atendimento'

defineOptions({ name: 'ListaConversas' })
const props = defineProps<{ selecionada?: number }>()
const emit = defineEmits<{ abrir: [id: number] }>()

const ABAS = [
  { valor: 'minhas', rotulo: 'Minhas' },
  { valor: 'nao_atribuidas', rotulo: 'Sem atendente' },
  { valor: 'todas', rotulo: 'Todas' }
]
const filtro = reactive({
  aba: 'minhas',
  status: 0,
  caixaId: undefined as number | undefined,
  busca: ''
})
const itens = ref<Atd.ConversaResumo[]>([])
const total = ref(0)
const pagina = ref(1)
const carregando = ref(false)
const caixas = ref<Atd.Caixa[]>([])
const contagens = ref<Record<string, number>>({})

const carregar = async (acumular = false) => {
  carregando.value = !acumular
  try {
    const r = await Atd.getConversas({ ...filtro, pageNo: pagina.value, pageSize: 30 })
    itens.value = acumular ? [...itens.value, ...r.list] : r.list
    total.value = r.total
    contagens.value = await Atd.getContagens(filtro.caixaId)
  } finally {
    carregando.value = false
  }
}
const mais = () => {
  pagina.value++
  carregar(true)
}
let espera: ReturnType<typeof setTimeout> | undefined
watch(
  () => ({ ...filtro }),
  () => {
    clearTimeout(espera)
    espera = setTimeout(() => {
      pagina.value = 1
      carregar()
    }, 250)
  }
)

/** A conversa ainda pertence a esta lista (aba, situação e caixa)? */
const pertence = (c: Atd.ConversaResumo, meuId: number) =>
  c.status === filtro.status &&
  (!filtro.caixaId || c.caixaId === filtro.caixaId) &&
  (filtro.aba === 'todas' || (filtro.aba === 'minhas' ? c.agenteId === meuId : !c.agenteId))

/** Atualização em tempo real de uma conversa. */
const atualizar = (c: Atd.ConversaResumo, meuId: number) => {
  const i = itens.value.findIndex((x) => x.id === c.id)
  const naoLidas = i >= 0 ? itens.value[i].naoLidas : 0
  if (i >= 0) itens.value.splice(i, 1)
  if (pertence(c, meuId) && !filtro.busca) {
    itens.value.unshift({ ...c, naoLidas })
  }
  Atd.getContagens(filtro.caixaId).then((r) => (contagens.value = r))
}
/** Mensagem nova do contato numa conversa que não está aberta: soma nas não lidas. */
const naoLida = (conversaId: number) => {
  const c = itens.value.find((x) => x.id === conversaId)
  if (c && c.id !== props.selecionada) c.naoLidas = (c.naoLidas || 0) + 1
}
const zerar = (conversaId: number) => {
  const c = itens.value.find((x) => x.id === conversaId)
  if (c) c.naoLidas = 0
}
defineExpose({ atualizar, naoLida, zerar, recarregar: () => carregar() })

const iniciais = (n: string) =>
  (n || '?')
    .replace(/^\+/, '')
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase()
const PALETA = ['#2a78d6', '#eb6834', '#1f9d8a', '#8a5cc7', '#c2477a', '#5b7083']
const corAvatar = (n: string) =>
  PALETA[[...(n || '')].reduce((s, ch) => s + ch.charCodeAt(0), 0) % PALETA.length]
const quando = (t: number) => {
  const d = new Date(t)
  const hoje = new Date()
  return d.toDateString() === hoje.toDateString()
    ? d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

const route = useRoute()
onMounted(async () => {
  caixas.value = await Atd.getMinhasCaixas()
  // vindo da tela de contatos: procura as conversas dele em todas as filas
  if (route.query.busca) {
    filtro.busca = String(route.query.busca)
    filtro.aba = 'todas'
    return // o watch do filtro carrega
  }
  await carregar()
})
</script>

<style scoped>
.lista {
  display: flex;
  height: 100%;
  flex-direction: column;
  border-right: 1px solid var(--el-border-color-lighter);
}
.topo {
  padding: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.abas {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}
.abas button {
  padding: 4px 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: none;
  border: 0;
  border-bottom: 2px solid transparent;
}
.abas button.ativa {
  font-weight: 600;
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
}
.abas .n {
  margin-left: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.itens {
  overflow-y: auto;
  flex: 1;
}
.item {
  display: flex;
  width: 100%;
  padding: 10px;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  gap: 10px;
}
.item:hover {
  background: var(--el-fill-color-light);
}
.item.selecionada {
  background: var(--el-color-primary-light-9);
  box-shadow: inset 3px 0 0 var(--el-color-primary);
}
.avatar {
  display: flex;
  width: 36px;
  height: 36px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  border-radius: 50%;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
}
.corpo {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}
.linha1,
.linha2,
.linha3 {
  display: flex;
  align-items: center;
  gap: 6px;
}
.nome {
  overflow: hidden;
  font-weight: 600;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.hora {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.previa {
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.badge {
  min-width: 18px;
  padding: 0 5px;
  font-size: 11px;
  line-height: 18px;
  color: #fff;
  text-align: center;
  background: var(--el-color-success);
  border-radius: 9px;
}
.linha3 {
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.etq i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 3px;
  border-radius: 50%;
}
.prio {
  padding: 0 4px;
  border: 1px solid currentcolor;
  border-radius: 3px;
}
.prio.p3 {
  color: var(--el-color-warning);
}
.prio.p4 {
  color: var(--el-color-danger);
}
</style>
