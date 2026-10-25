<!-- Rota: /monitoramento/manutencoes · Permissões: monitoramento:monitor:query (ver) e monitoramento:manutencao:update -->
<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Durante a manutenção os monitores não são verificados, não avisam e não abrem chamado. A
        disponibilidade não conta esse período.
      </span>
      <el-button v-if="pode" type="primary" @click="abrir()"
        ><Icon class="mr-4px" icon="ep:plus" />Nova manutenção</el-button
      >
    </div>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Manutenção" min-width="200">
        <template #default="{ row }">
          <div class="font-medium">{{ row.titulo }}</div>
          <div v-if="row.descricao" class="text-12px text-[var(--el-text-color-secondary)]">{{
            row.descricao
          }}</div>
        </template>
      </el-table-column>
      <el-table-column label="Quando" min-width="220">
        <template #default="{ row }">{{ janela(row) }}</template>
      </el-table-column>
      <el-table-column label="Monitores" min-width="220">
        <template #default="{ row }">
          <div v-if="row.unidadeNome">Toda a unidade {{ row.unidadeNome }}</div>
          <div class="text-12px">{{ (row.monitorNomes || []).join(', ') }}</div>
        </template>
      </el-table-column>
      <el-table-column label="Situação" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.valendo" disable-transitions type="warning">Valendo agora</el-tag>
          <el-tag v-else-if="!row.ativa" disable-transitions type="info">Desligada</el-tag>
          <el-tag v-else disable-transitions type="success">Agendada</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="pode" label="" width="210">
        <template #default="{ row }">
          <el-button link @click="ativar(row)">{{
            row.ativa
              ? row.tipo === 'manual'
                ? 'Encerrar'
                : 'Desligar'
              : row.tipo === 'manual'
                ? 'Iniciar'
                : 'Ligar'
          }}</el-button>
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !lista.length"
      :image-size="60"
      description="Nenhuma janela de manutenção."
    />
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Alterar manutenção' : 'Nova manutenção'"
    width="640px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="130px">
      <el-form-item label="Título" prop="titulo"
        ><el-input v-model="form.dados.titulo" maxlength="150"
      /></el-form-item>
      <el-form-item label="Descrição"
        ><el-input v-model="form.dados.descricao" :rows="2" type="textarea"
      /></el-form-item>
      <el-form-item label="Tipo">
        <el-radio-group v-model="form.dados.tipo">
          <el-radio-button value="unica">Uma vez</el-radio-button>
          <el-radio-button value="semanal">Toda semana</el-radio-button>
          <el-radio-button value="manual">Manual</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.dados.tipo === 'unica'" label="Período">
        <el-date-picker
          v-model="periodo"
          end-placeholder="fim"
          format="DD/MM/YYYY HH:mm"
          start-placeholder="início"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
        />
      </el-form-item>
      <template v-if="form.dados.tipo === 'semanal'">
        <el-form-item label="Dias">
          <el-checkbox-group v-model="form.dados.diasSemana">
            <el-checkbox-button v-for="d in 7" :key="d" :value="d">{{
              DIAS[d]
            }}</el-checkbox-button>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="Horário">
          <el-time-picker
            v-model="form.dados.horaInicio"
            class="!w-130px"
            format="HH:mm"
            placeholder="início"
            value-format="HH:mm:ss"
          />
          <span class="mx-8px">até</span>
          <el-time-picker
            v-model="form.dados.horaFim"
            class="!w-130px"
            format="HH:mm"
            placeholder="fim"
            value-format="HH:mm:ss"
          />
          <div class="w-full text-12px text-[var(--el-text-color-secondary)]"
            >Fim antes do início = atravessa a meia-noite.</div
          >
        </el-form-item>
      </template>
      <el-alert
        v-if="form.dados.tipo === 'manual'"
        :closable="false"
        class="mb-16px"
        show-icon
        type="info"
        title="Vale enquanto estiver ligada: ligue ao começar o serviço e encerre ao terminar."
      />
      <el-form-item label="Unidade inteira">
        <el-select
          v-model="form.dados.unidadeId"
          class="w-full"
          clearable
          filterable
          placeholder="nenhuma"
        >
          <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="Monitores">
        <el-select
          v-model="form.dados.monitorIds"
          class="w-full"
          filterable
          multiple
          placeholder="escolha os monitores"
        >
          <el-option v-for="m in monitores" :key="m.id" :label="m.nome" :value="m.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import { DIAS } from '@/api/monitoramento'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'MonitoramentoManutencao' })
const message = useMessage()
const pode = checkPermi(['monitoramento:manutencao:update'])
const regras = { titulo: [{ required: true, message: 'Informe o título', trigger: 'blur' }] }
const lista = ref<Mon.Manutencao[]>([])
const loading = ref(true)
const unidades = ref<Mon.Unidade[]>([])
const monitores = ref<{ id: number; nome: string }[]>([])
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Mon.Manutencao })
const periodo = ref<[string, string] | null>(null)

const fmt = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '?'
const janela = (m: Mon.Manutencao) => {
  if (m.tipo === 'manual') return 'Manual (enquanto ligada)'
  if (m.tipo === 'unica') return `${fmt(m.inicio)} até ${fmt(m.fim)}`
  return `${(m.diasSemana || []).map((d) => DIAS[d]).join(', ')} · ${m.horaInicio?.slice(0, 5)} às ${m.horaFim?.slice(0, 5)}`
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Mon.getManutencoes()
  } finally {
    loading.value = false
  }
}
const abrir = async (m?: Mon.Manutencao) => {
  if (!monitores.value.length) monitores.value = await Mon.getMonitoresSimples()
  form.dados = m
    ? { ...m, monitorIds: [...(m.monitorIds || [])], diasSemana: [...(m.diasSemana || [])] }
    : { titulo: '', tipo: 'unica', ativa: true, diasSemana: [], monitorIds: [] }
  periodo.value = m?.inicio && m?.fim ? [m.inicio, m.fim] : null
  form.visivel = true
}
const salvar = async () => {
  await formRef.value.validate()
  const dados = { ...form.dados }
  if (dados.tipo === 'unica') {
    dados.inicio = periodo.value?.[0]
    dados.fim = periodo.value?.[1]
  }
  await Mon.salvarManutencao(dados)
  message.success('Manutenção salva')
  form.visivel = false
  await carregar()
}
const ativar = async (m: Mon.Manutencao) => {
  await Mon.ativarManutencao(m.id!, !m.ativa)
  await carregar()
}
const excluir = async (m: Mon.Manutencao) => {
  await message.delConfirm(`Excluir a manutenção "${m.titulo}"?`)
  await Mon.excluirManutencao(m.id!)
  await carregar()
}

onMounted(async () => {
  unidades.value = await Mon.getUnidades()
  await carregar()
})
</script>
