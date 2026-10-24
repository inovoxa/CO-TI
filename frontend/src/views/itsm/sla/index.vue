<template>
  <ContentWrap>
    <el-tabs v-model="aba">
      <el-tab-pane label="SLAs" name="slas">
        <div class="mb-12px flex gap-8px">
          <el-button v-hasPermi="['itsm:sla:update']" plain type="primary" @click="abrir()">
            <Icon class="mr-5px" icon="ep:plus" /> Novo SLA
          </el-button>
          <el-button v-hasPermi="['itsm:sla:update']" @click="rodarAgora">
            <Icon class="mr-5px" icon="ep:video-play" /> Rodar escalonamento agora
          </el-button>
        </div>
        <el-table v-loading="loading" :data="slas">
          <el-table-column label="SLA" min-width="200" prop="nome" />
          <el-table-column label="Mede" width="130">
            <template #default="{ row }">
              <el-tag :type="row.tipo === 1 ? 'warning' : 'success'" disable-transitions>
                {{ row.tipo === 1 ? 'Atendimento' : 'Solução' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Prazo" width="200">
            <template #default="{ row }">
              {{ row.tempo }} {{ rotuloUnidade(row.unidade) }}<span v-if="row.fimExpediente"> (fim do expediente)</span>
            </template>
          </el-table-column>
          <el-table-column label="Calendário" width="160">
            <template #default="{ row }">{{ nomeCalendario(row.calendarioId) }}</template>
          </el-table-column>
          <el-table-column label="Escalonamento" min-width="260">
            <template #default="{ row }">
              <div v-for="n in row.niveis" :key="n.id" class="text-12px">
                <b>{{ n.nome }}</b>: {{ quando(n.minutos) }}<span v-if="!n.ativo"> (inativo)</span>
              </div>
              <span v-if="!row.niveis.length" class="text-[var(--el-text-color-placeholder)]">—</span>
            </template>
          </el-table-column>
          <el-table-column align="center" label="Ativo" width="80">
            <template #default="{ row }">{{ row.ativo ? 'Sim' : 'Não' }}</template>
          </el-table-column>
          <el-table-column align="center" label="Ações" width="150">
            <template #default="{ row }">
              <el-button v-hasPermi="['itsm:sla:update']" link type="primary" @click="abrir(row)">Editar</el-button>
              <el-button v-hasPermi="['itsm:sla:update']" link type="danger" @click="excluir(row)">Excluir</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Padrão por prioridade" name="prioridade">
        <el-alert
          :closable="false"
          class="mb-12px"
          show-icon
          title="Usado quando a categoria do chamado não define SLA. A categoria sempre vence."
          type="info"
        />
        <el-table :data="porPrioridade">
          <el-table-column label="Prioridade" width="160">
            <template #default="{ row }">{{ PRIORIDADES.find((p) => p.valor === row.prioridade)?.rotulo }}</template>
          </el-table-column>
          <el-table-column label="SLA de atendimento">
            <template #default="{ row }">
              <el-select v-model="row.slaAtendimentoId" class="w-full" clearable placeholder="Sem SLA">
                <el-option v-for="s in slasDoTipo(1)" :key="s.id" :label="s.nome" :value="s.id!" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="SLA de solução">
            <template #default="{ row }">
              <el-select v-model="row.slaSolucaoId" class="w-full" clearable placeholder="Sem SLA">
                <el-option v-for="s in slasDoTipo(0)" :key="s.id" :label="s.nome" :value="s.id!" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
        <el-button v-hasPermi="['itsm:sla:update']" class="mt-12px" type="primary" @click="salvarPrioridades">Salvar</el-button>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <Dialog v-model="dialogo" :title="form.id ? 'Editar SLA' : 'Novo SLA'" width="860px">
    <el-form ref="formRef" :model="form" :rules="regras" label-width="130px">
      <el-form-item label="Nome" prop="nome"><el-input v-model="form.nome" maxlength="100" /></el-form-item>
      <el-form-item label="Mede">
        <el-radio-group v-model="form.tipo">
          <el-radio-button :value="1">Tempo para assumir (atendimento)</el-radio-button>
          <el-radio-button :value="0">Tempo para solucionar</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Prazo">
        <el-input-number v-model="form.tempo" :max="100000" :min="0" class="!w-140px" />
        <el-select v-model="form.unidade" class="ml-8px !w-140px">
          <el-option v-for="u in UNIDADES" :key="u.valor" :label="u.rotulo" :value="u.valor" />
        </el-select>
        <el-checkbox v-if="form.unidade === 'dia' || form.unidade === 'mes'" v-model="form.fimExpediente" class="ml-12px">
          terminar no fim do expediente
        </el-checkbox>
      </el-form-item>
      <el-form-item label="Calendário">
        <el-select v-model="form.calendarioId" class="!w-300px" clearable placeholder="24 h, 7 dias por semana">
          <el-option v-for="c in calendarios" :key="c.id" :label="c.nome" :value="c.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="Descrição"><el-input v-model="form.descricao" maxlength="500" type="textarea" /></el-form-item>
      <el-form-item label="Ativo"><el-switch v-model="form.ativo" /></el-form-item>

      <el-divider content-position="left">Escalonamento</el-divider>
      <el-table :data="form.niveis" size="small">
        <el-table-column label="Nível" min-width="150">
          <template #default="{ row }"><el-input v-model="row.nome" maxlength="100" placeholder="Ex.: acionar N2" /></template>
        </el-table-column>
        <el-table-column label="Quando (min)" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.minutos" :max="525600" :min="-525600" controls-position="right" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="Incluir equipe" width="170">
          <template #default="{ row }">
            <el-select v-model="row.atribuirEquipeId" clearable size="small">
              <el-option v-for="e in equipes" :key="e.id" :label="e.nome" :value="e.id!" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Incluir técnico" width="170">
          <template #default="{ row }">
            <el-select v-model="row.atribuirTecnicoId" clearable filterable size="small">
              <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Prioridade" width="130">
          <template #default="{ row }">
            <el-select v-model="row.novaPrioridade" clearable size="small">
              <el-option v-for="p in PRIORIDADES" :key="p.valor" :label="p.rotulo" :value="p.valor" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column width="50">
          <template #default="{ $index }">
            <el-button link type="danger" @click="form.niveis.splice($index, 1)"><Icon icon="ep:delete" /></el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-8px flex items-center justify-between text-12px text-[var(--el-text-color-secondary)]">
        <span>Minutos em relação ao prazo: −30 = meia hora antes de vencer; 60 = uma hora depois de vencido.</span>
        <el-button size="small" @click="form.niveis.push({ nome: '', minutos: -30, ativo: true })">
          <Icon class="mr-4px" icon="ep:plus" />Nível
        </el-button>
      </div>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="dialogo = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ItsmApi from '@/api/itsm'
import { PRIORIDADES, UNIDADES } from '@/api/itsm'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ItsmSla' })
const message = useMessage()

const aba = ref('slas')
const loading = ref(true)
const slas = ref<ItsmApi.Sla[]>([])
const porPrioridade = ref<ItsmApi.SlaPorPrioridade[]>([])
const calendarios = ref<ItsmApi.Calendario[]>([])
const equipes = ref<ItsmApi.Equipe[]>([])
const usuarios = ref<UserApi.UserVO[]>([])
const dialogo = ref(false)
const formRef = ref()
const vazio = (): ItsmApi.Sla => ({ nome: '', tipo: 0, tempo: 4, unidade: 'hora', fimExpediente: false, ativo: true, niveis: [] })
const form = ref<ItsmApi.Sla>(vazio())
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }

const rotuloUnidade = (u: string) => UNIDADES.find((x) => x.valor === u)?.rotulo || u
const nomeCalendario = (id?: number) => (id ? calendarios.value.find((c) => c.id === id)?.nome || '#' + id : '24 × 7')
const slasDoTipo = (tipo: number) => slas.value.filter((s) => s.tipo === tipo && s.ativo)
const quando = (min: number) =>
  min === 0 ? 'ao vencer' : min < 0 ? `${-min} min antes de vencer` : `${min} min depois de vencido`

const carregar = async () => {
  loading.value = true
  try {
    ;[slas.value, porPrioridade.value, calendarios.value] = await Promise.all([
      ItsmApi.getSlas(),
      ItsmApi.getSlaPorPrioridade(),
      ItsmApi.getCalendarios()
    ])
  } finally {
    loading.value = false
  }
}

const abrir = (s?: ItsmApi.Sla) => {
  form.value = s ? JSON.parse(JSON.stringify(s)) : vazio()
  dialogo.value = true
}

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  if (form.value.unidade !== 'dia' && form.value.unidade !== 'mes') form.value.fimExpediente = false
  if (form.value.niveis.some((n) => !n.nome.trim())) {
    message.warning('Dê um nome a cada nível de escalonamento')
    return
  }
  await ItsmApi.salvarSla(form.value)
  message.success('SLA salvo')
  dialogo.value = false
  await carregar()
}

const excluir = async (s: ItsmApi.Sla) => {
  await message.delConfirm(`Excluir o SLA "${s.nome}"?`)
  await ItsmApi.excluirSla(s.id!)
  message.success('Excluído')
  await carregar()
}

const salvarPrioridades = async () => {
  await ItsmApi.salvarSlaPorPrioridade(porPrioridade.value)
  message.success('SLA por prioridade salvo')
}

const rodarAgora = async () => {
  const r = await ItsmApi.executarAutomacao()
  message.success(`${r.escalonamentos} escalonamento(s) e ${r.fechados} fechamento(s) automático(s)`)
}

onMounted(async () => {
  ;[equipes.value, usuarios.value] = await Promise.all([ItsmApi.getEquipes(true), UserApi.getSimpleUserList()])
  await carregar()
})
</script>
