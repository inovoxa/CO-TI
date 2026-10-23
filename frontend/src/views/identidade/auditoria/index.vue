<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams" class="-mb-15px" label-width="80px">
      <el-form-item label="Ação" prop="acao">
        <el-select v-model="queryParams.acao" class="!w-240px" clearable filterable placeholder="Todas">
          <el-option v-for="a in ACOES" :key="a.valor" :label="a.rotulo" :value="a.valor" />
        </el-select>
      </el-form-item>
      <el-form-item label="Usuário" prop="usuario">
        <el-input
          v-model="queryParams.usuario"
          class="!w-200px"
          clearable
          placeholder="Login"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="Período" prop="momento">
        <el-date-picker
          v-model="queryParams.momento"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-260px"
          end-placeholder="Fim"
          start-placeholder="Início"
          type="daterange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" /> Limpar</el-button>
        <el-button
          v-hasPermi="['identidade:auditoria:verificar']"
          :loading="verificando"
          plain
          type="primary"
          @click="verificar"
        >
          <Icon class="mr-5px" icon="ep:circle-check" /> Verificar integridade
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap v-if="verificacao">
    <el-alert
      v-if="verificacao.integra"
      :closable="false"
      show-icon
      type="success"
      :title="`Trilha íntegra: ${verificacao.registrosConferidos} registros conferidos, nenhuma alteração ou remoção.`"
    />
    <el-alert
      v-else
      :closable="false"
      show-icon
      type="error"
      :title="`Trilha ADULTERADA a partir do registro ${verificacao.primeiroIdQuebrado}: ${verificacao.motivo}.`"
      description="Os registros anteriores a ele estão íntegros. Preserve o banco como está e acione a equipe de segurança."
    />
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column align="center" label="Nº" prop="id" width="90" />
      <el-table-column :formatter="dateFormatter" align="center" label="Quando" prop="momento" width="180" />
      <el-table-column label="Ação" min-width="200" prop="acao">
        <template #default="{ row }">
          <el-tag :type="tipoTag(row.acao)" disable-transitions>{{ rotulo(row.acao) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Usuário" prop="usuario" width="160">
        <template #default="{ row }">{{ row.usuario || (row.userId ? `#${row.userId}` : 'sistema') }}</template>
      </el-table-column>
      <el-table-column label="IP" prop="ip" width="140" />
      <el-table-column label="Detalhes" min-width="260" prop="detalhes" show-overflow-tooltip>
        <template #default="{ row }">
          <code class="text-12px">{{ row.detalhes === '{}' ? '' : row.detalhes }}</code>
        </template>
      </el-table-column>
      <el-table-column label="Hash" width="120">
        <template #default="{ row }">
          <el-tooltip :content="row.hash" placement="left">
            <code class="text-12px">{{ row.hash.substring(0, 10) }}…</code>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import * as IdentidadeApi from '@/api/identidade'

defineOptions({ name: 'IdentidadeAuditoria' })

const ACOES = [
  { valor: 'login.sucesso', rotulo: 'Login' },
  { valor: 'login.falha', rotulo: 'Login recusado' },
  { valor: 'login.mfa_pendente', rotulo: 'Login aguardando 2ª etapa' },
  { valor: 'login.mfa_falha', rotulo: 'Código da 2ª etapa errado' },
  { valor: 'logout', rotulo: 'Saída' },
  { valor: 'mfa.ativado', rotulo: '2 etapas ativada' },
  { valor: 'mfa.desativado', rotulo: '2 etapas desativada' },
  { valor: 'mfa.redefinido_por_admin', rotulo: '2 etapas redefinida por admin' },
  { valor: 'mfa.codigo_recuperacao_usado', rotulo: 'Código de recuperação usado' },
  { valor: 'ad.usuario_criado', rotulo: 'AD: usuário criado' },
  { valor: 'ad.usuario_status', rotulo: 'AD: situação alterada' },
  { valor: 'ad.usuario_desativado', rotulo: 'AD: usuário desativado' },
  { valor: 'ad.perfis_alterados', rotulo: 'AD: perfis alterados' },
  { valor: 'ad.sincronizacao', rotulo: 'AD: sincronização' },
  { valor: 'admin.senha_inicial_definida', rotulo: 'Senha inicial do admin' },
  { valor: 'auditoria.verificada', rotulo: 'Trilha verificada' }
]
const ROTULOS = Object.fromEntries(ACOES.map((a) => [a.valor, a.rotulo]))
const rotulo = (acao: string) => ROTULOS[acao] || acao
const tipoTag = (acao: string) => {
  if (acao.endsWith('falha') || acao.includes('desativ') || acao.includes('redefinido')) return 'danger'
  if (acao.startsWith('mfa.') || acao.startsWith('admin.')) return 'warning'
  if (acao.startsWith('ad.')) return 'info'
  return 'success'
}

const loading = ref(true)
const total = ref(0)
const list = ref<IdentidadeApi.AuditoriaVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  acao: undefined,
  usuario: undefined,
  momento: []
})
const queryFormRef = ref()
const verificando = ref(false)
const verificacao = ref<IdentidadeApi.VerificacaoAuditoria>()

const getList = async () => {
  loading.value = true
  try {
    const data = await IdentidadeApi.getAuditoriaPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const verificar = async () => {
  verificando.value = true
  try {
    verificacao.value = await IdentidadeApi.verificarAuditoria()
    await getList()
  } finally {
    verificando.value = false
  }
}

onMounted(getList)
</script>
