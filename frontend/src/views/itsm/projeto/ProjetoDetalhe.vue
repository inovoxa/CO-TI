<template>
  <el-drawer v-model="visivel" :size="largura" destroy-on-close @closed="p = undefined">
    <template #header>
      <div v-if="p" class="min-w-0">
        <div class="truncate text-17px font-bold">
          <span v-if="p.codigo" class="text-[var(--el-text-color-secondary)]"
            >{{ p.codigo }} · </span
          >{{ p.nome }}
        </div>
        <div class="mt-6px flex flex-wrap items-center gap-6px">
          <el-tag :type="cor(SITUACOES_PROJETO, p.situacao)" disable-transitions>{{
            p.situacaoRotulo
          }}</el-tag>
          <el-tag v-if="p.atrasado" disable-transitions effect="dark" type="danger"
            >Atrasado</el-tag
          >
          <span class="text-13px text-[var(--el-text-color-secondary)]"
            >{{ p.percentual }}% feito</span
          >
        </div>
      </div>
    </template>

    <template v-if="p">
      <div class="mb-12px flex flex-wrap gap-8px">
        <el-button v-if="pode('editar')" @click="abrirForm(p)"
          ><Icon class="mr-4px" icon="ep:edit" />Editar</el-button
        >
        <el-button v-if="pode('excluir')" plain type="danger" @click="excluir"
          ><Icon class="mr-4px" icon="ep:delete" />Excluir</el-button
        >
      </div>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="Responsável">{{ p.responsavel || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Unidade">{{ p.dept || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Orçamento">{{ p.orcamento || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Previsto"
          >{{ data(p.inicioPrevisto) }} a {{ data(p.fimPrevisto) }}</el-descriptions-item
        >
        <el-descriptions-item label="Real"
          >{{ data(p.inicioReal) }} a {{ data(p.fimReal) }}</el-descriptions-item
        >
        <el-descriptions-item label="Horas"
          >{{ p.horasGastas }} de {{ p.horasPrevistas }} previstas</el-descriptions-item
        >
        <el-descriptions-item label="Tarefas"
          >{{ p.tarefasConcluidas }} de {{ p.totalTarefas }} concluídas</el-descriptions-item
        >
        <el-descriptions-item label="Custos">{{ reais(p.custoTotal) }}</el-descriptions-item>
        <el-descriptions-item label="Equipe">{{
          p.membros?.map((m) => m.nome).join(', ') || '—'
        }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="aba" class="mt-12px">
        <el-tab-pane label="Cronograma" name="gantt">
          <Gantt :tarefas="p.tarefas || []" />
        </el-tab-pane>

        <el-tab-pane :label="`Tarefas (${p.tarefas?.length || 0})`" name="tarefas">
          <el-button v-if="pode('tarefas')" class="mb-8px" size="small" @click="abrirTarefa()">
            <Icon class="mr-4px" icon="ep:plus" />Nova tarefa
          </el-button>
          <el-table :data="p.tarefas" row-key="id" size="small">
            <el-table-column label="Tarefa" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span :style="{ paddingLeft: nivel(row) * 14 + 'px' }">
                  <Icon v-if="row.marco" class="mr-2px" icon="ep:flag" />{{ row.nome }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              label="Responsável"
              min-width="120"
              prop="responsavel"
              show-overflow-tooltip
            />
            <el-table-column label="Previsto" width="170">
              <template #default="{ row }">
                <span :class="{ 'text-[var(--el-color-danger)] font-bold': row.atrasada }"
                  >{{ data(row.inicioPrevisto) }} a {{ data(row.fimPrevisto) }}</span
                >
              </template>
            </el-table-column>
            <el-table-column label="Situação" width="120">
              <template #default="{ row }">
                <el-tag
                  :type="cor(SITUACOES_TAREFA, row.situacao)"
                  disable-transitions
                  size="small"
                  >{{ row.situacaoRotulo }}</el-tag
                >
              </template>
            </el-table-column>
            <el-table-column label="Feito" width="70">
              <template #default="{ row }">{{ row.percentual }}%</template>
            </el-table-column>
            <el-table-column label="Horas" width="90">
              <template #default="{ row }">{{ row.horasGastas }}/{{ row.horasPrevistas }}</template>
            </el-table-column>
            <el-table-column width="110">
              <template #default="{ row }">
                <el-button v-if="row.podeEditar" link type="primary" @click="abrirProgresso(row)"
                  >Andamento</el-button
                >
                <el-button v-if="pode('tarefas')" link type="primary" @click="abrirTarefa(row)"
                  ><Icon icon="ep:edit"
                /></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`Vínculos (${p.vinculos?.length || 0})`" name="vinculos">
          <div v-if="pode('vincular')" class="mb-10px flex flex-wrap items-center gap-8px">
            <el-select v-model="vinc.objeto" class="!w-130px" size="small">
              <el-option label="Chamado" value="chamado" />
              <el-option label="Problema" value="problema" />
              <el-option label="Mudança" value="mudanca" />
            </el-select>
            <el-input-number
              v-model="vinc.objetoId"
              :controls="false"
              :min="1"
              class="!w-110px"
              placeholder="Nº"
              size="small"
            />
            <el-button :disabled="!vinc.objetoId" size="small" @click="vincular"
              >Vincular</el-button
            >
          </div>
          <el-table :data="p.vinculos" size="small">
            <el-table-column label="Item" width="140">
              <template #default="{ row }">{{ ROTULO[row.objeto] }} #{{ row.objetoId }}</template>
            </el-table-column>
            <el-table-column label="Título" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link v-if="row.titulo" :href="link(row)" target="_blank" type="primary">{{
                  row.titulo
                }}</el-link>
                <span v-else class="text-[var(--el-text-color-placeholder)]"
                  >fora da sua unidade</span
                >
              </template>
            </el-table-column>
            <el-table-column label="Situação" prop="statusRotulo" width="160" />
            <el-table-column v-if="pode('vincular')" width="60">
              <template #default="{ row }">
                <el-button link type="danger" @click="desvincular(row)"
                  ><Icon icon="ep:close"
                /></el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Custos" name="custos">
          <CustosTabela
            :editavel="pode('custos')"
            :objeto-id="p.id!"
            objeto="projeto"
            @alterado="recarregar"
          />
        </el-tab-pane>

        <el-tab-pane label="Descrição" name="descricao">
          <div v-html="p.descricao || '<i>Sem descrição</i>'"></div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- projeto -->
    <Dialog
      v-model="form.visivel"
      :title="form.dados.id ? 'Editar projeto' : 'Novo projeto'"
      width="780px"
    >
      <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="16">
            <el-form-item label="Nome" prop="nome"
              ><el-input v-model="form.dados.nome" maxlength="150"
            /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Código" label-width="70px"
              ><el-input v-model="form.dados.codigo" maxlength="40"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Situação">
              <el-select v-model="form.dados.situacao" class="w-full">
                <el-option
                  v-for="s in SITUACOES_PROJETO"
                  :key="s.valor"
                  :label="s.rotulo"
                  :value="s.valor"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Prioridade">
              <el-select v-model="form.dados.prioridade" class="w-full">
                <el-option
                  v-for="x in PRIORIDADES"
                  :key="x.valor"
                  :label="x.rotulo"
                  :value="x.valor"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Previsto">
              <el-date-picker
                v-model="periodo"
                end-placeholder="Fim"
                start-placeholder="Início"
                type="daterange"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Responsável">
              <el-select v-model="form.dados.responsavelId" class="w-full" clearable filterable>
                <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Equipe">
              <el-select v-model="form.dados.membroIds" class="w-full" filterable multiple>
                <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Orçamento">
              <el-select v-model="form.dados.orcamentoId" class="w-full" clearable filterable>
                <el-option v-for="o in orcamentos" :key="o.id" :label="o.nome" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Unidade">
              <el-tree-select
                v-model="form.dados.deptId"
                :data="depts"
                :props="defaultProps"
                check-strictly
                class="w-full"
                clearable
                node-key="id"
                placeholder="A minha"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Andamento">
              <el-checkbox v-model="form.dados.percentualAuto">Calcular pelas tarefas</el-checkbox>
              <el-input-number
                v-if="!form.dados.percentualAuto"
                v-model="form.dados.percentual"
                :max="100"
                :min="0"
                class="ml-12px"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="Descrição">
              <Editor v-model="form.dados.descricao" directory="projetos" height="160px" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="salvar">Salvar</el-button>
        <el-button @click="form.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>

    <!-- tarefa -->
    <Dialog
      v-model="tarefa.visivel"
      :title="tarefa.dados.id ? 'Editar tarefa' : 'Nova tarefa'"
      width="680px"
    >
      <el-form :model="tarefa.dados" label-width="120px">
        <el-form-item label="Nome"
          ><el-input v-model="tarefa.dados.nome" maxlength="200"
        /></el-form-item>
        <el-form-item label="Dentro de">
          <el-select
            v-model="tarefa.dados.tarefaPaiId"
            class="w-full"
            clearable
            placeholder="Nenhuma (tarefa principal)"
          >
            <el-option
              v-for="t in (p?.tarefas || []).filter((x) => x.id !== tarefa.dados.id)"
              :key="t.id"
              :label="t.nome"
              :value="t.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Responsável">
          <el-select v-model="tarefa.dados.responsavelId" class="w-full" clearable filterable>
            <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Previsto">
          <el-date-picker
            v-model="periodoTarefa"
            end-placeholder="Fim"
            start-placeholder="Início"
            type="daterange"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="Horas previstas">
          <el-input-number
            v-model="tarefa.dados.horasPrevistas"
            :min="0"
            :precision="1"
            :step="1"
          />
          <el-checkbox v-model="tarefa.dados.marco" class="ml-16px"
            >É um marco (entrega)</el-checkbox
          >
        </el-form-item>
        <el-form-item label="Situação">
          <el-select v-model="tarefa.dados.situacao">
            <el-option
              v-for="s in SITUACOES_TAREFA"
              :key="s.valor"
              :label="s.rotulo"
              :value="s.valor"
            />
          </el-select>
          <el-input-number v-model="tarefa.dados.percentual" :max="100" :min="0" class="ml-12px" />
          <span class="ml-4px">%</span>
        </el-form-item>
        <el-form-item label="Ordem"
          ><el-input-number v-model="tarefa.dados.ordem" :min="0"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button
          v-if="tarefa.dados.id"
          class="float-left"
          plain
          type="danger"
          @click="excluirTarefa"
          >Excluir</el-button
        >
        <el-button type="primary" @click="salvarTarefa">Salvar</el-button>
        <el-button @click="tarefa.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>

    <!-- andamento -->
    <Dialog v-model="prog.visivel" :title="`Andamento: ${prog.nome}`" width="440px">
      <el-form label-width="110px">
        <el-form-item label="Situação">
          <el-select v-model="prog.situacao">
            <el-option
              v-for="s in SITUACOES_TAREFA"
              :key="s.valor"
              :label="s.rotulo"
              :value="s.valor"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Feito"><el-slider v-model="prog.percentual" :step="5" /></el-form-item>
        <el-form-item label="Horas gastas">
          <el-input-number v-model="prog.horasGastas" :min="0" :precision="1" :step="0.5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="salvarProgresso">Salvar</el-button>
        <el-button @click="prog.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as Gov from '@/api/itsm/governanca'
import { SITUACOES_PROJETO, SITUACOES_TAREFA, cor, data, reais } from '@/api/itsm/governanca'
import { PRIORIDADES } from '@/api/itsm'
import * as UserApi from '@/api/system/user'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'
import CustosTabela from '../governanca/CustosTabela.vue'
import Gantt from './Gantt.vue'

defineOptions({ name: 'ProjetoDetalhe' })
const emit = defineEmits(['alterado'])
const message = useMessage()
const ROTULO: Record<string, string> = {
  chamado: 'Chamado',
  problema: 'Problema',
  mudanca: 'Mudança'
}
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }
const largura = computed(() => (window.innerWidth < 1200 ? '100%' : '75%'))

const visivel = ref(false)
const aba = ref('gantt')
const p = ref<Gov.Projeto>()
const usuarios = ref<UserApi.UserVO[]>([])
const orcamentos = ref<Gov.Resumo[]>([])
const depts = ref<any[]>([])
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as Gov.Projeto })
const tarefa = reactive({ visivel: false, dados: {} as Gov.Tarefa })
const prog = reactive({
  visivel: false,
  id: 0,
  nome: '',
  situacao: 1,
  percentual: 0,
  horasGastas: 0
})
const vinc = reactive({ objeto: 'chamado', objetoId: undefined as number | undefined })

const pode = (a: string) => !!p.value?.acoes?.includes(a)
const intervalo = (o: any, ini: string, fim: string) =>
  computed<string[]>({
    get: () => (o()[ini] ? [o()[ini], o()[fim]] : []),
    set: (v) => {
      o()[ini] = v?.[0]
      o()[fim] = v?.[1]
    }
  })
const periodo = intervalo(() => form.dados, 'inicioPrevisto', 'fimPrevisto')
const periodoTarefa = intervalo(() => tarefa.dados, 'inicioPrevisto', 'fimPrevisto')
const nivel = (t: Gov.Tarefa) => {
  let n = 0
  let pai = t.tarefaPaiId
  while (pai && n < 10) {
    n++
    pai = p.value?.tarefas?.find((x) => x.id === pai)?.tarefaPaiId
  }
  return n
}
const link = (v: Gov.VinculoProjeto) =>
  v.objeto === 'chamado'
    ? `/servicedesk/chamados?id=${v.objetoId}`
    : `/servicedesk/${v.objeto === 'problema' ? 'problemas' : 'mudancas'}?id=${v.objetoId}`

const listas = async () => {
  if (!usuarios.value.length) {
    ;[usuarios.value, orcamentos.value, depts.value] = await Promise.all([
      UserApi.getSimpleUserList(),
      Gov.getOrcamentosSimples().catch(() => []),
      DeptApi.getSimpleDeptList().then(handleTree)
    ])
  }
}

const open = async (id: number) => {
  p.value = await Gov.getProjeto(id)
  aba.value = 'gantt'
  visivel.value = true
  if (pode('editar') || pode('tarefas')) listas()
}
const novo = () => abrirForm()
defineExpose({ open, novo })

const recarregar = async () => {
  p.value = await Gov.getProjeto(p.value!.id!)
  emit('alterado')
}

const abrirForm = async (x?: Gov.Projeto) => {
  form.dados = x
    ? { ...x, membroIds: [...(x.membroIds || [])] }
    : { nome: '', situacao: 1, prioridade: 3, percentual: 0, percentualAuto: true, membroIds: [] }
  form.visivel = true
  await listas()
}

const salvar = async () => {
  await formRef.value.validate()
  const id = await Gov.salvarProjeto(form.dados)
  message.success('Projeto salvo')
  form.visivel = false
  emit('alterado')
  await open(id)
}

const excluir = async () => {
  await message.delConfirm(
    `Excluir o projeto ${p.value!.nome}? Tarefas, equipe, vínculos e custos saem junto.`
  )
  await Gov.excluirProjeto(p.value!.id!)
  visivel.value = false
  emit('alterado')
}

const abrirTarefa = (t?: Gov.Tarefa) => {
  tarefa.dados = t
    ? { ...t }
    : {
        projetoId: p.value!.id!,
        nome: '',
        situacao: 1,
        percentual: 0,
        horasPrevistas: 0,
        horasGastas: 0,
        marco: false,
        ordem: (p.value!.tarefas?.length || 0) + 1
      }
  tarefa.visivel = true
}

const salvarTarefa = async () => {
  await Gov.salvarTarefa(tarefa.dados)
  tarefa.visivel = false
  await recarregar()
}

const excluirTarefa = async () => {
  await message.delConfirm(`Excluir a tarefa ${tarefa.dados.nome}? As subtarefas sobem um nível.`)
  await Gov.excluirTarefa(tarefa.dados.id!)
  tarefa.visivel = false
  await recarregar()
}

const abrirProgresso = (t: Gov.Tarefa) =>
  Object.assign(prog, {
    visivel: true,
    id: t.id,
    nome: t.nome,
    situacao: t.situacao,
    percentual: t.percentual,
    horasGastas: Number(t.horasGastas)
  })

const salvarProgresso = async () => {
  await Gov.progressoTarefa({
    tarefaId: prog.id,
    situacao: prog.situacao,
    percentual: prog.percentual,
    horasGastas: prog.horasGastas
  })
  prog.visivel = false
  await recarregar()
}

const vincular = async () => {
  await Gov.vincularProjeto({
    projetoId: p.value!.id!,
    objeto: vinc.objeto,
    objetoId: vinc.objetoId!
  })
  vinc.objetoId = undefined
  await recarregar()
}

const desvincular = async (v: Gov.VinculoProjeto) => {
  await message.confirm(`Desfazer o vínculo com ${ROTULO[v.objeto].toLowerCase()} #${v.objetoId}?`)
  await Gov.desvincularProjeto(v.id)
  await recarregar()
}
</script>
