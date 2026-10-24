<template>
  <div class="tela">
    <div class="col-lista">
      <div class="disponibilidade">
        <span class="dot" :class="disponibilidade"></span>
        <el-select
          v-model="disponibilidade"
          class="!w-130px"
          size="small"
          @change="trocarDisponibilidade"
        >
          <el-option label="Disponível" value="online" />
          <el-option label="Ocupado" value="ocupado" />
          <el-option label="Ausente" value="offline" />
        </el-select>
        <span
          class="ml-auto text-12px"
          :class="conectado ? 'text-[var(--el-color-success)]' : 'text-[var(--el-color-danger)]'"
        >
          {{ conectado ? 'ao vivo' : 'reconectando…' }}
        </span>
      </div>
      <ListaConversas
        ref="listaRef"
        :selecionada="selecionada"
        class="flex-1 min-h-0"
        @abrir="abrir"
      />
    </div>
    <ConversaPainel
      ref="painelRef"
      :agentes-da-caixa="agentesDaCaixa"
      :conversa-id="selecionada"
      class="col-painel"
      @alterado="recarregarAtual"
    />
    <ConversaLateral
      v-if="atual"
      ref="lateralRef"
      :conversa="atual"
      :etiquetas="etiquetas"
      class="col-lateral"
      @abrir="abrir"
      @alterado="recarregarAtual"
    />
  </div>
</template>

<script lang="ts" setup>
import * as Atd from '@/api/atendimento'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import { useUserStore } from '@/store/modules/user'
import ListaConversas from './ListaConversas.vue'
import ConversaPainel from './ConversaPainel.vue'
import ConversaLateral from './ConversaLateral.vue'

defineOptions({ name: 'AtendimentoConversa' })
const route = useRoute()
const router = useRouter()
const meuId = useUserStore().getUser.id

const selecionada = ref<number>()
const atual = ref<Atd.ConversaResumo>()
const etiquetas = ref<Atd.Etiqueta[]>([])
const caixas = ref<Atd.Caixa[]>([])
const disponibilidade = ref('online')
const listaRef = ref()
const painelRef = ref()
const lateralRef = ref()

// a mesma conexão do Centro de Operações (uma por navegador), assinando os avisos do atendimento
const { status, on } = useMonitorSocket()
const conectado = computed(() => status.value === 'OPEN')

const agentesDaCaixa = (caixaId: number) =>
  caixas.value.find((c) => c.id === caixaId)?.agentes || []

const abrir = async (id: number) => {
  selecionada.value = id
  atual.value = await Atd.getConversa(id)
  listaRef.value?.zerar(id)
  router.replace({ query: { ...route.query, id: String(id) } })
}

const recarregarAtual = async () => {
  if (selecionada.value) atual.value = await Atd.getConversa(selecionada.value)
}

on('atendimento-conversa', (c: Atd.ConversaResumo) => {
  listaRef.value?.atualizar(c, meuId)
  if (c.id === selecionada.value) {
    atual.value = { ...atual.value!, ...c, naoLidas: 0 }
    painelRef.value?.atualizar(c)
  }
})
on('atendimento-mensagem', (e: { conversaId: number; mensagem: Atd.Mensagem }) => {
  if (e.conversaId === selecionada.value) {
    painelRef.value?.receber(e.mensagem)
    // registro do sistema (ex.: chamado aberto) muda o painel lateral
    if (e.mensagem.tipo === 2) lateralRef.value?.recarregar?.()
  } else if (e.mensagem.tipo === 0) {
    listaRef.value?.naoLida(e.conversaId)
  }
})

const trocarDisponibilidade = (v: string) => Atd.definirDisponibilidade(v)
let relogio: ReturnType<typeof setInterval> | undefined
const sinal = async () => {
  disponibilidade.value = await Atd.sinal()
}

onMounted(async () => {
  await sinal()
  relogio = setInterval(sinal, 60_000)
  ;[etiquetas.value, caixas.value] = await Promise.all([Atd.getEtiquetas(), Atd.getMinhasCaixas()])
  const id = Number(route.query.id)
  if (id) abrir(id)
})
onBeforeUnmount(() => clearInterval(relogio))
</script>

<style scoped>
.tela {
  display: flex;
  height: calc(100vh - var(--top-tool-height, 50px) - var(--tags-view-height, 35px) - 2px);
  overflow: hidden;
  background: var(--el-bg-color);
}
.col-lista {
  display: flex;
  width: 320px;
  flex-shrink: 0;
  flex-direction: column;
}
.col-painel {
  min-width: 0;
  flex: 1;
}
.col-lateral {
  width: 300px;
  flex-shrink: 0;
}
.disponibilidade {
  display: flex;
  padding: 8px 10px;
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  gap: 6px;
}
.dot {
  width: 8px;
  height: 8px;
  background: var(--el-border-color);
  border-radius: 50%;
}
.dot.online {
  background: var(--el-color-success);
}
.dot.ocupado {
  background: var(--el-color-warning);
}
@media (width <= 1100px) {
  .col-lateral {
    display: none;
  }
}
</style>
