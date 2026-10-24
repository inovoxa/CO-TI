<template>
  <ContentWrap>
    <el-radio-group v-model="pendentes" @change="carregar">
      <el-radio-button :value="true">Aguardando minha resposta</el-radio-button>
      <el-radio-button :value="false">Todas</el-radio-button>
    </el-radio-group>
  </ContentWrap>

  <ContentWrap>
    <el-empty
      v-if="!loading && !lista.length"
      :description="pendentes ? 'Nada aguardando a sua aprovação' : 'Nenhum pedido de aprovação'"
    />
    <el-table v-else v-loading="loading" :data="lista" class="cursor-pointer" @row-click="abrir">
      <el-table-column label="Pedido" width="150">
        <template #default="{ row }">{{ row.rotuloOrigem }} #{{ row.numero }}</template>
      </el-table-column>
      <el-table-column label="Assunto" min-width="260" prop="titulo" show-overflow-tooltip />
      <el-table-column label="Pedido por" min-width="160" prop="pedidoPor" />
      <el-table-column label="Situação" width="130">
        <template #default="{ row }">
          <el-tag :type="corAprovacao(row.status)" disable-transitions>{{
            ROTULO[row.status]
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" label="Pedido em" prop="pedidoEm" width="160" />
    </el-table>
  </ContentWrap>

  <el-drawer v-model="visivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="atual" class="min-w-0">
        <div class="truncate text-17px font-bold"
          >{{ atual.rotuloOrigem }} #{{ atual.numero }} · {{ atual.titulo }}</div
        >
        <div class="mt-4px text-13px text-[var(--el-text-color-secondary)]">
          Pedido de {{ atual.pedidoPor || '—' }} · {{ formatDate(atual.pedidoEm) }}
        </div>
      </div>
    </template>
    <template v-if="atual">
      <el-alert
        v-if="atual.comentarioPedido"
        :closable="false"
        :title="atual.comentarioPedido"
        class="mb-12px"
        type="info"
      />
      <!-- textos já saneados no servidor -->
      <div v-if="atual.origem === 'chamado'" class="descricao" v-html="atual.descricao || ''"></div>
      <template v-else>
        <el-descriptions :column="1" border class="mb-12px" size="small">
          <el-descriptions-item label="Tipo">{{ atual.tipoRotulo }}</el-descriptions-item>
          <el-descriptions-item label="Janela">
            <template v-if="atual.inicioPrevisto"
              >{{ formatDate(atual.inicioPrevisto) }} até
              {{ formatDate(atual.fimPrevisto!) }}</template
            >
            <template v-else>não marcada</template>
          </el-descriptions-item>
        </el-descriptions>
        <div class="mb-4px text-13px font-bold">Análise de impacto</div>
        <div class="texto" v-html="atual.analiseImpacto || '<i>Não preenchida</i>'"></div>
        <div class="mb-4px mt-12px text-13px font-bold">Plano de retorno</div>
        <div class="texto" v-html="atual.planoRetorno || '<i>Não preenchido</i>'"></div>
      </template>
      <el-card v-if="atual.status === 2" class="mt-16px" shadow="never">
        <el-input
          v-model="comentario"
          maxlength="2000"
          placeholder="Comentário (obrigatório para recusar)"
          type="textarea"
        />
        <div class="mt-10px flex gap-8px">
          <el-button :loading="enviando" type="success" @click="responder(true)">
            <Icon class="mr-4px" icon="ep:check" /> Aprovar
          </el-button>
          <el-button :loading="enviando" type="danger" @click="responder(false)">
            <Icon class="mr-4px" icon="ep:close" /> Recusar
          </el-button>
        </div>
      </el-card>
      <el-alert
        v-else
        :closable="false"
        :title="`${ROTULO[atual.status]} em ${formatDate(atual.respondidoEm!)}${atual.comentarioResposta ? ': ' + atual.comentarioResposta : ''}`"
        :type="atual.status === 3 ? 'success' : 'error'"
        class="mt-16px"
      />
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as ItsmApi from '@/api/itsm'
import { corAprovacao } from '@/api/itsm'

defineOptions({ name: 'ItsmAprovacao' })
const message = useMessage()

const ROTULO: Record<number, string> = { 2: 'Aguardando', 3: 'Aprovado', 4: 'Recusado' }

/** Pedido de aprovação de chamado ou de mudança, no mesmo formato para a lista. */
interface Pedido {
  origem: 'chamado' | 'mudanca'
  rotuloOrigem: string
  id: number
  numero: number
  titulo?: string
  pedidoPor?: string
  status: number
  comentarioPedido?: string
  comentarioResposta?: string
  pedidoEm: number
  respondidoEm?: number
  descricao?: string
  tipoRotulo?: string
  inicioPrevisto?: number
  fimPrevisto?: number
  analiseImpacto?: string
  planoRetorno?: string
}

const loading = ref(true)
const pendentes = ref(true)
const lista = ref<Pedido[]>([])
const visivel = ref(false)
const atual = ref<Pedido>()
const comentario = ref('')
const enviando = ref(false)
const largura = computed(() => (window.innerWidth < 1000 ? '100%' : '55%'))

const carregar = async () => {
  loading.value = true
  try {
    const [chamados, mudancas] = await Promise.all([
      ItsmApi.getMinhasAprovacoes(pendentes.value),
      ItsmApi.getMinhasAprovacoesMudanca(pendentes.value)
    ])
    lista.value = [
      ...chamados.map(
        (a): Pedido => ({
          origem: 'chamado',
          rotuloOrigem: 'Chamado',
          id: a.id,
          numero: a.chamadoId,
          titulo: a.chamadoTitulo,
          pedidoPor: a.solicitante,
          status: a.status,
          comentarioPedido: a.comentarioPedido,
          comentarioResposta: a.comentarioResposta,
          pedidoEm: a.pedidoEm,
          respondidoEm: a.respondidoEm,
          descricao: a.chamadoDescricao
        })
      ),
      ...mudancas.map(
        (a): Pedido => ({
          origem: 'mudanca',
          rotuloOrigem: 'Mudança',
          id: a.id,
          numero: a.mudancaId,
          titulo: a.mudancaTitulo,
          pedidoPor: a.solicitadoPorNome,
          status: a.status,
          comentarioPedido: a.comentarioPedido,
          comentarioResposta: a.comentarioResposta,
          pedidoEm: a.pedidoEm,
          respondidoEm: a.respondidoEm,
          tipoRotulo: a.tipoRotulo,
          inicioPrevisto: a.inicioPrevisto,
          fimPrevisto: a.fimPrevisto,
          analiseImpacto: a.analiseImpacto,
          planoRetorno: a.planoRetorno
        })
      )
    ].sort((x, y) => y.pedidoEm - x.pedidoEm)
  } finally {
    loading.value = false
  }
}

const abrir = (a: Pedido) => {
  atual.value = a
  comentario.value = ''
  visivel.value = true
}

const responder = async (aprovar: boolean) => {
  if (!aprovar && !comentario.value.trim()) {
    message.warning('Escreva o motivo da recusa')
    return
  }
  enviando.value = true
  try {
    const texto = comentario.value.trim() || undefined
    if (atual.value!.origem === 'chamado') {
      await ItsmApi.responderAprovacao({ id: atual.value!.id, aprovar, comentario: texto })
    } else {
      await ItsmApi.responderAprovacaoMudanca({
        aprovacaoId: atual.value!.id,
        aprovar,
        comentario: texto
      })
    }
    message.success(aprovar ? 'Pedido aprovado' : 'Pedido recusado')
    visivel.value = false
    await carregar()
  } finally {
    enviando.value = false
  }
}

onMounted(carregar)
</script>

<style scoped>
.descricao :deep(table) {
  width: 100%;
  margin-bottom: 12px;
  border-collapse: collapse;
}
.descricao :deep(td) {
  padding: 6px 8px;
  vertical-align: top;
  border: 1px solid var(--el-border-color-lighter);
}
.descricao :deep(td:first-child) {
  width: 40%;
  color: var(--el-text-color-secondary);
}
.texto {
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
</style>
