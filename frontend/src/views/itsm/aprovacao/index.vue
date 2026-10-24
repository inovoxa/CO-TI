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
      <el-table-column label="Chamado" width="90"
        ><template #default="{ row }">#{{ row.chamadoId }}</template></el-table-column
      >
      <el-table-column label="Pedido" min-width="260" prop="chamadoTitulo" show-overflow-tooltip />
      <el-table-column label="Solicitante" min-width="160" prop="solicitante" />
      <el-table-column label="Situação" width="130">
        <template #default="{ row }">
          <el-tag :type="corAprovacao(row.status)" disable-transitions>{{
            row.statusRotulo
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
          >#{{ atual.chamadoId }} · {{ atual.chamadoTitulo }}</div
        >
        <div class="mt-4px text-13px text-[var(--el-text-color-secondary)]">
          Pedido de {{ atual.solicitante || '—' }} · {{ formatDate(atual.pedidoEm) }}
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
      <!-- descrição já saneada no servidor -->
      <div class="descricao" v-html="atual.chamadoDescricao || ''"></div>
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
        :title="`${atual.statusRotulo} em ${formatDate(atual.respondidoEm!)}${atual.comentarioResposta ? ': ' + atual.comentarioResposta : ''}`"
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

const loading = ref(true)
const pendentes = ref(true)
const lista = ref<ItsmApi.Aprovacao[]>([])
const visivel = ref(false)
const atual = ref<ItsmApi.Aprovacao>()
const comentario = ref('')
const enviando = ref(false)
const largura = computed(() => (window.innerWidth < 1000 ? '100%' : '55%'))

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await ItsmApi.getMinhasAprovacoes(pendentes.value)
  } finally {
    loading.value = false
  }
}

const abrir = (a: ItsmApi.Aprovacao) => {
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
    await ItsmApi.responderAprovacao({
      id: atual.value!.id,
      aprovar,
      comentario: comentario.value.trim() || undefined
    })
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
</style>
