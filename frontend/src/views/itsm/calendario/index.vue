<template>
  <ContentWrap>
    <el-tabs v-model="aba">
      <el-tab-pane label="Calendários" name="calendarios">
        <el-button v-hasPermi="['itsm:calendario:update']" class="mb-12px" plain type="primary" @click="abrirCal()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo calendário
        </el-button>
        <el-table v-loading="loading" :data="calendarios">
          <el-table-column label="Calendário" min-width="180" prop="nome" />
          <el-table-column label="Expediente" min-width="320">
            <template #default="{ row }">
              <div v-for="linha in resumo(row)" :key="linha" class="text-12px">{{ linha }}</div>
            </template>
          </el-table-column>
          <el-table-column label="Feriados" width="110">
            <template #default="{ row }">{{ row.feriadoIds.length }}</template>
          </el-table-column>
          <el-table-column align="center" label="Ações" width="150">
            <template #default="{ row }">
              <el-button v-hasPermi="['itsm:calendario:update']" link type="primary" @click="abrirCal(row)">Editar</el-button>
              <el-button v-hasPermi="['itsm:calendario:update']" link type="danger" @click="excluirCal(row)">Excluir</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Feriados" name="feriados">
        <el-button v-hasPermi="['itsm:calendario:update']" class="mb-12px" plain type="primary" @click="abrirFer()">
          <Icon class="mr-5px" icon="ep:plus" /> Novo feriado
        </el-button>
        <el-table :data="feriados">
          <el-table-column label="Feriado" min-width="200" prop="nome" />
          <el-table-column label="Data" width="220">
            <template #default="{ row }">{{ dataFeriado(row) }}</template>
          </el-table-column>
          <el-table-column label="Repete todo ano" width="140">
            <template #default="{ row }">{{ row.perpetuo ? 'Sim' : 'Não' }}</template>
          </el-table-column>
          <el-table-column align="center" label="Ações" width="150">
            <template #default="{ row }">
              <el-button v-hasPermi="['itsm:calendario:update']" link type="primary" @click="abrirFer(row)">Editar</el-button>
              <el-button v-hasPermi="['itsm:calendario:update']" link type="danger" @click="excluirFer(row)">Excluir</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <Dialog v-model="dlgCal" :title="cal.id ? 'Editar calendário' : 'Novo calendário'" width="720px">
    <el-form ref="calRef" :model="cal" :rules="regras" label-width="100px">
      <el-form-item label="Nome" prop="nome"><el-input v-model="cal.nome" maxlength="100" /></el-form-item>
      <el-form-item label="Descrição"><el-input v-model="cal.descricao" maxlength="500" /></el-form-item>
      <el-form-item label="Expediente">
        <div class="w-full">
          <div v-for="d in 7" :key="d" class="mb-6px flex flex-wrap items-center gap-6px">
            <span class="w-80px text-13px">{{ DIAS[d] }}</span>
            <template v-for="(p, i) in periodosDoDia(d)" :key="i">
              <el-time-select v-model="p.inicio" class="!w-100px" end="23:45" size="small" start="00:00" step="00:15" />
              <span>–</span>
              <el-time-select v-model="p.fim" class="!w-100px" end="24:00" size="small" start="00:15" step="00:15" />
              <el-button link size="small" type="danger" @click="removerPeriodo(p)"><Icon icon="ep:close" /></el-button>
            </template>
            <el-button link size="small" type="primary" @click="cal.periodos.push({ diaSemana: d, inicio: '08:00', fim: '18:00' })">
              + período
            </el-button>
          </div>
          <el-button size="small" @click="preencherComercial">Preencher seg–sex 08–12 e 13–18</el-button>
        </div>
      </el-form-item>
      <el-form-item label="Feriados">
        <el-select v-model="cal.feriadoIds" class="w-full" filterable multiple placeholder="Nenhum">
          <el-option v-for="f in feriados" :key="f.id" :label="`${f.nome} (${dataFeriado(f)})`" :value="f.id!" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvarCal">Salvar</el-button>
      <el-button @click="dlgCal = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="dlgFer" :title="fer.id ? 'Editar feriado' : 'Novo feriado'" width="480px">
    <el-form ref="ferRef" :model="fer" :rules="regrasFer" label-width="110px">
      <el-form-item label="Nome" prop="nome"><el-input v-model="fer.nome" maxlength="100" /></el-form-item>
      <el-form-item label="De" prop="dataInicio">
        <el-date-picker v-model="fer.dataInicio" type="date" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="Até">
        <el-date-picker v-model="fer.dataFim" placeholder="Mesmo dia" type="date" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="Todo ano"><el-switch v-model="fer.perpetuo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvarFer">Salvar</el-button>
      <el-button @click="dlgFer = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ItsmApi from '@/api/itsm'
import { DIAS } from '@/api/itsm'

defineOptions({ name: 'ItsmCalendario' })
const message = useMessage()

const aba = ref('calendarios')
const loading = ref(true)
const calendarios = ref<ItsmApi.Calendario[]>([])
const feriados = ref<ItsmApi.Feriado[]>([])
const dlgCal = ref(false)
const dlgFer = ref(false)
const calRef = ref()
const ferRef = ref()
const cal = ref<ItsmApi.Calendario>({ nome: '', periodos: [], feriadoIds: [] })
const fer = ref<ItsmApi.Feriado>({ nome: '', dataInicio: '', perpetuo: false })
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }
const regrasFer = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  dataInicio: [{ required: true, message: 'Informe a data', trigger: 'change' }]
}

const periodosDoDia = (d: number) => cal.value.periodos.filter((p) => p.diaSemana === d)
const removerPeriodo = (p: ItsmApi.Periodo) => cal.value.periodos.splice(cal.value.periodos.indexOf(p), 1)
const resumo = (c: ItsmApi.Calendario) => {
  const linhas: string[] = []
  for (let d = 1; d <= 7; d++) {
    const ps = c.periodos.filter((p) => p.diaSemana === d)
    if (ps.length) linhas.push(`${DIAS[d]}: ${ps.map((p) => `${p.inicio}–${p.fim}`).join(', ')}`)
  }
  return linhas.length ? linhas : ['sem expediente']
}
const dataFeriado = (f: ItsmApi.Feriado) => {
  const fmt = (d: string) => (f.perpetuo ? d.slice(8, 10) + '/' + d.slice(5, 7) : d.split('-').reverse().join('/'))
  return f.dataFim && f.dataFim !== f.dataInicio ? `${fmt(f.dataInicio)} a ${fmt(f.dataFim)}` : fmt(f.dataInicio)
}

const carregar = async () => {
  loading.value = true
  try {
    ;[calendarios.value, feriados.value] = await Promise.all([ItsmApi.getCalendarios(), ItsmApi.getFeriados()])
  } finally {
    loading.value = false
  }
}

const abrirCal = (c?: ItsmApi.Calendario) => {
  cal.value = c ? JSON.parse(JSON.stringify(c)) : { nome: '', descricao: '', periodos: [], feriadoIds: [] }
  dlgCal.value = true
}

const preencherComercial = () => {
  cal.value.periodos = []
  for (let d = 1; d <= 5; d++) {
    cal.value.periodos.push({ diaSemana: d, inicio: '08:00', fim: '12:00' }, { diaSemana: d, inicio: '13:00', fim: '18:00' })
  }
}

const salvarCal = async () => {
  if (!(await calRef.value.validate().catch(() => false))) return
  await ItsmApi.salvarCalendario(cal.value)
  message.success('Calendário salvo')
  dlgCal.value = false
  await carregar()
}

const excluirCal = async (c: ItsmApi.Calendario) => {
  await message.delConfirm(`Excluir o calendário "${c.nome}"?`)
  await ItsmApi.excluirCalendario(c.id!)
  message.success('Excluído')
  await carregar()
}

const abrirFer = (f?: ItsmApi.Feriado) => {
  fer.value = f ? { ...f } : { nome: '', dataInicio: '', perpetuo: false }
  dlgFer.value = true
}

const salvarFer = async () => {
  if (!(await ferRef.value.validate().catch(() => false))) return
  await ItsmApi.salvarFeriado(fer.value)
  message.success('Feriado salvo')
  dlgFer.value = false
  await carregar()
}

const excluirFer = async (f: ItsmApi.Feriado) => {
  await message.delConfirm(`Excluir o feriado "${f.nome}"? Ele sai de todos os calendários.`)
  await ItsmApi.excluirFeriado(f.id!)
  message.success('Excluído')
  await carregar()
}

onMounted(carregar)
</script>
