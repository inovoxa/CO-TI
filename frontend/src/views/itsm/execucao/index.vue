<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-12px">
      <el-select
        v-model="status"
        class="!w-220px"
        clearable
        placeholder="Todas as situações"
        @change="buscar"
      >
        <el-option v-for="(s, k) in STATUS_EXECUCAO" :key="k" :label="s.rotulo" :value="k" />
      </el-select>
      <el-button @click="carregar"><Icon class="mr-4px" icon="ep:refresh" /> Atualizar</el-button>
      <el-tag
        :type="modo === 'ssh' ? 'success' : modo === 'simulado' ? 'warning' : 'info'"
        disable-transitions
        effect="plain"
      >
        Automação no AD: {{ ROTULO_MODO[modo] || modo }}
      </el-tag>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="px-16px">
            <div v-if="row.mensagem"><b>Mensagem:</b> {{ row.mensagem }}</div>
            <div v-if="row.resultado" class="mt-4px"
              ><b>Resultado:</b> <code>{{ row.resultado }}</code></div
            >
            <div class="mt-4px"
              ><b>Dados enviados ao script:</b> <code class="break-all">{{ row.dados }}</code></div
            >
            <div v-if="row.senhaVistaEm" class="mt-4px"
              >Senha provisória vista pelo solicitante em {{ formatDate(row.senhaVistaEm) }}.</div
            >
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Chamado" width="90"
        ><template #default="{ row }">#{{ row.chamadoId }}</template></el-table-column
      >
      <el-table-column label="Pedido" min-width="240" prop="chamadoTitulo" show-overflow-tooltip />
      <el-table-column label="Operação" width="160">
        <template #default="{ row }">{{
          ACOES_AD.find((a) => a.valor === row.acao)?.rotulo || row.acao
        }}</template>
      </el-table-column>
      <el-table-column label="Situação" width="180">
        <template #default="{ row }">
          <el-tag :type="(STATUS_EXECUCAO[row.status]?.cor as any) || 'info'" disable-transitions>
            {{ STATUS_EXECUCAO[row.status]?.rotulo || row.status }}
          </el-tag>
          <el-tag
            v-if="row.senhaGuardada"
            class="ml-4px"
            disable-transitions
            effect="plain"
            size="small"
            >senha guardada</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Tentativas" width="100">
        <template #default="{ row }">
          {{ row.tentativas }}
          <div
            v-if="row.status === 'pronta' && row.proximaTentativa"
            class="text-12px text-[var(--el-text-color-secondary)]"
          >
            próxima {{ formatDate(row.proximaTentativa, 'HH:mm') }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Mensagem" min-width="240" prop="mensagem" show-overflow-tooltip />
      <el-table-column
        :formatter="dateFormatter"
        label="Executada em"
        prop="executadoEm"
        width="160"
      />
      <el-table-column align="center" label="Ações" width="150">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'aguardando_confirmacao'"
            v-hasPermi="['itsm:execucao:update']"
            link
            type="primary"
            @click="confirmar(row)"
          >
            Confirmar
          </el-button>
          <el-button
            v-if="row.status === 'erro' || row.status === 'cancelada'"
            v-hasPermi="['itsm:execucao:update']"
            link
            type="primary"
            @click="repetir(row)"
          >
            Repetir
          </el-button>
          <el-button
            v-if="['erro', 'pronta', 'aguardando_aprovacao', 'aguardando_confirmacao'].includes(row.status)"
            v-hasPermi="['itsm:execucao:update']"
            link
            type="danger"
            @click="cancelar(row)"
          >
            Cancelar
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="query.pageSize"
      v-model:page="query.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import * as ItsmApi from '@/api/itsm'
import { ACOES_AD, STATUS_EXECUCAO } from '@/api/itsm'

defineOptions({ name: 'ItsmExecucaoAd' })
const message = useMessage()
const ROTULO_MODO: Record<string, string> = {
  desligado: 'desligada (técnicos executam)',
  simulado: 'simulada',
  ssh: 'ligada (SSH)'
}

const loading = ref(true)
const lista = ref<ItsmApi.Execucao[]>([])
const total = ref(0)
const status = ref<string>()
const modo = ref('')
const query = reactive({ pageNo: 1, pageSize: 20 })

const carregar = async () => {
  loading.value = true
  try {
    const data = await ItsmApi.getExecucoes({ ...query, status: status.value })
    lista.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  query.pageNo = 1
  carregar()
}

const confirmar = async (e: ItsmApi.Execucao) => {
  await message.confirm(`Confirmar a operação do chamado #${e.chamadoId}? A conta será bloqueada no AD.`)
  await ItsmApi.confirmarExecucao(e.id)
  message.success('Confirmada')
  await carregar()
}

const repetir = async (e: ItsmApi.Execucao) => {
  await message.confirm(
    e.acao === 'Criar' || e.acao === 'Transferir'
      ? 'Confira no AD se a operação não foi feita em parte antes de repetir. Repetir agora?'
      : 'Colocar a operação na fila de novo?'
  )
  await ItsmApi.repetirExecucao(e.id)
  message.success('Na fila')
  await carregar()
}

const cancelar = async (e: ItsmApi.Execucao) => {
  const { value } = await ElMessageBox.prompt(
    'Motivo (ex.: feito manualmente)',
    'Cancelar execução',
    {
      confirmButtonText: 'Cancelar execução',
      cancelButtonText: 'Voltar'
    }
  )
  await ItsmApi.cancelarExecucao(e.id, value)
  await carregar()
}

onMounted(async () => {
  await carregar()
  modo.value = await ItsmApi.getModoAd()
})
</script>
