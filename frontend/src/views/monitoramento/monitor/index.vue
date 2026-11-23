<!-- Rota: /monitoramento/monitores · Permissões: monitoramento:monitor:query, :update e monitoramento:importar -->
<template>
  <ContentWrap>
    <div class="mb-12px flex flex-wrap items-center gap-6px">
      <el-check-tag
        v-for="c in CHIPS"
        :key="c.valor"
        :checked="q.situacao === c.valor"
        @change="filtrar(c.valor)"
      >
        {{ c.rotulo }} <b class="ml-2px">{{ c.n() }}</b>
      </el-check-tag>
      <span v-if="contagens.lento" class="text-12px text-[var(--el-color-warning)]"
        >· {{ contagens.lento }} lentos</span
      >
      <span v-if="contagens.certificadoVencendo" class="text-12px text-[var(--el-color-warning)]">
        · {{ contagens.certificadoVencendo }} certificados vencem em menos de 30 dias
      </span>
    </div>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-240px"
          clearable
          placeholder="Nome, endereço ou descrição"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-select v-model="q.tipo" class="!w-200px" clearable placeholder="Tipo" @change="buscar">
          <el-option v-for="t in TIPOS" :key="t.valor" :label="t.rotulo" :value="t.valor" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.unidadeId"
          class="!w-200px"
          clearable
          filterable
          placeholder="Unidade"
          @change="buscar"
        >
          <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id!" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.papel"
          class="!w-190px"
          clearable
          placeholder="Papel"
          @change="buscar"
        >
          <el-option v-for="p in PAPEIS" :key="p.valor" :label="p.rotulo" :value="p.valor" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          v-hasPermi="['monitoramento:monitor:update']"
          type="primary"
          @click="form?.abrir()"
        >
          <Icon class="mr-4px" icon="ep:plus" />Novo monitor
        </el-button>
        <el-button plain @click="exportar">
          <Icon class="mr-4px" icon="ep:download" /> Exportar
        </el-button>
        <el-button
          v-hasPermi="['monitoramento:monitor:update']"
          plain
          @click="importadorPlanilha?.abrir()"
        >
          <Icon class="mr-4px" icon="ep:upload" /> Importar
        </el-button>
        <el-button
          v-hasPermi="['monitoramento:monitor:update']"
          plain
          @click="servidoresKuma?.abrir()"
        >
          <Icon class="mr-4px" icon="ep:connection" />Uptime Kuma ao vivo
        </el-button>
        <el-button v-hasPermi="['monitoramento:importar']" @click="importar?.abrir()">
          <Icon class="mr-4px" icon="ep:upload" />Importar do Uptime Kuma
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div
      v-hasPermi="['monitoramento:monitor:update']"
      class="mb-10px flex flex-wrap items-center gap-x-12px gap-y-6px"
    >
      <el-dropdown
        :disabled="!quantosMarcados"
        trigger="click"
        @command="(a: AcaoLote) => executar(a)"
      >
        <el-button :disabled="!quantosMarcados" :loading="executando">
          Executar ação<Icon class="ml-4px" icon="ep:arrow-down" />
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="pausar">
              <Icon class="mr-6px" icon="ep:video-pause" />Pausar
            </el-dropdown-item>
            <el-dropdown-item command="retomar">
              <Icon class="mr-6px" icon="ep:video-play" />Retomar
            </el-dropdown-item>
            <el-dropdown-item class="!text-[var(--el-color-danger)]" command="excluir" divided>
              <Icon class="mr-6px" icon="ep:delete" />Excluir
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        <template v-if="!quantosMarcados">Marque os monitores na primeira coluna.</template>
        <template v-else-if="todosDoFiltro">
          Todos os <b>{{ total }}</b> monitores{{ filtrado ? ' do filtro' : '' }} estão
          selecionados.
        </template>
        <template v-else>{{ marcados.length }} selecionado(s) nesta página.</template>
      </span>
      <el-button
        v-if="paginaInteira && !todosDoFiltro && total > lista.length"
        link
        type="primary"
        @click="todosDoFiltro = true"
      >
        Selecionar todos os {{ total }}{{ filtrado ? ' do filtro' : '' }}
      </el-button>
      <el-button v-if="quantosMarcados" link @click="limparSelecao">Limpar seleção</el-button>
    </div>
    <el-table
      ref="tabela"
      v-loading="loading"
      :data="lista"
      row-class-name="cursor-pointer"
      row-key="id"
      @selection-change="marcar"
      @sort-change="ordenar"
      @row-click="(r) => detalhe?.abrir(r.id)"
    >
      <el-table-column
        v-if="podeAlterar"
        :selectable="() => !executando"
        type="selection"
        width="44"
      />
      <el-table-column label="Monitor" min-width="260" prop="nome" sortable="custom">
        <template #default="{ row }">
          <div class="flex items-center">
            <span :class="`ponto ${classe(row)}`"></span>
            <span class="font-medium">{{ row.nome }}</span>
            <el-tag v-if="!row.ativo" class="ml-6px" disable-transitions size="small" type="info"
              >pausado</el-tag
            >
            <el-tag
              v-else-if="row.emManutencao"
              class="ml-6px"
              disable-transitions
              size="small"
              type="info"
              >manutenção</el-tag
            >
            <el-tag
              v-if="row.chamadoId"
              class="ml-6px"
              disable-transitions
              size="small"
              type="danger"
              >chamado #{{ row.chamadoId }}</el-tag
            >
          </div>
          <div class="break-all pl-15px text-12px text-[var(--el-text-color-secondary)]">
            {{ rotuloTipo(row.tipo) }} ·
            {{ row.alvo ? row.alvo + (row.porta ? `:${row.porta}` : '') : '—' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="Últimas 40 verificações"
        min-width="250"
        prop="situacao"
        sortable="custom"
      >
        <template #default="{ row }"><Batidas :historico="row.historico" /></template>
      </el-table-column>
      <el-table-column label="Resposta" prop="resposta" sortable="custom" width="115">
        <template #default="{ row }">
          <span v-if="row.estado === 0" class="text-[var(--el-color-danger)]">fora</span>
          <span
            v-else-if="row.ultimaLatenciaMs != null"
            :class="{ 'text-[var(--el-color-warning)]': row.lento }"
            >{{ row.ultimaLatenciaMs }} ms</span
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="24 h" prop="uptime24h" sortable="custom" width="95">
        <template #default="{ row }">{{ pct(row.uptime24h) }}</template>
      </el-table-column>
      <el-table-column label="30 dias" prop="uptime30d" sortable="custom" width="105">
        <template #default="{ row }">{{ pct(row.uptime30d) }}</template>
      </el-table-column>
      <el-table-column label="Unidade" min-width="150" prop="unidade" sortable="custom">
        <template #default="{ row }">
          {{ row.unidadeNome || '—' }}
          <div
            v-if="row.papel !== 'servico'"
            class="text-12px text-[var(--el-text-color-secondary)]"
          >
            {{ row.papel === 'principal' ? 'link principal' : 'link redundante' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Certificado" prop="certificado" sortable="custom" width="125">
        <template #default="{ row }">
          <el-tag
            v-if="row.certDias != null"
            :type="row.certDias < 30 ? 'warning' : 'success'"
            disable-transitions
            effect="plain"
            size="small"
            >{{ row.certDias }} dias</el-tag
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !lista.length && !filtrado"
      :image-size="70"
      description="Nenhum monitor ainda."
    >
      <span class="text-13px">Crie um monitor ou importe os do Uptime Kuma.</span>
    </el-empty>
    <Pagination
      v-model:limit="q.pageSize"
      v-model:page="q.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>

  <ContentWrap title="Mudanças de estado (7 dias)">
    <el-table :data="eventos" max-height="320" size="small">
      <el-table-column label="Quando" width="160">
        <template #default="{ row }">{{ quando(row.momento) }}</template>
      </el-table-column>
      <el-table-column label="Monitor" min-width="200">
        <template #default="{ row }">
          <el-link @click="detalhe?.abrir(row.monitorId)">{{ row.monitorNome }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="Estado" width="120">
        <template #default="{ row }">
          <el-tag :type="ESTADOS[row.estado].cor as any" disable-transitions size="small">{{
            ESTADOS[row.estado].rotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Antes, por" width="110">
        <template #default="{ row }">{{ duracao(row.duracaoAnteriorS) }}</template>
      </el-table-column>
      <el-table-column label="Mensagem" min-width="240" prop="msg" show-overflow-tooltip />
    </el-table>
  </ContentWrap>

  <MonitorForm
    ref="form"
    @salvo="
      () => {
        atualizar()
        detalhe?.recarregar()
      }
    "
  />
  <MonitorDetalhe ref="detalhe" @editar="(d) => form?.abrir(d)" @mudou="atualizar" />
  <ImportarKuma ref="importar" @importado="atualizar" />
  <KumaServidores ref="servidoresKuma" @mudou="atualizar" />
  <ImportarPlanilha
    ref="importadorPlanilha"
    :analisar="Mon.analisarImportacaoMonitores"
    :aplicar="Mon.importarMonitores"
    nome-plural="monitores"
    nome-singular="monitor"
    @importado="atualizar"
  />
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import { ESTADOS, PAPEIS, TIPOS, duracao, pct, quando } from '@/api/monitoramento'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import download from '@/utils/download'
import ImportarPlanilha from '@/components/ImportarPlanilha/index.vue'
import { ElMessageBox, type TableInstance } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import Batidas from './Batidas.vue'
import ImportarKuma from './ImportarKuma.vue'
import KumaServidores from './KumaServidores.vue'
import MonitorDetalhe from './MonitorDetalhe.vue'
import MonitorForm from './MonitorForm.vue'

defineOptions({ name: 'MonitoramentoMonitor' })
const route = useRoute()
const form = ref<InstanceType<typeof MonitorForm>>()
const detalhe = ref<InstanceType<typeof MonitorDetalhe>>()
const importar = ref<InstanceType<typeof ImportarKuma>>()
const servidoresKuma = ref<InstanceType<typeof KumaServidores>>()
const importadorPlanilha = ref<InstanceType<typeof ImportarPlanilha>>()

const lista = ref<Mon.MonitorResp[]>([])
const total = ref(0)
const loading = ref(true)
const eventos = ref<Mon.Evento[]>([])
const unidades = ref<Mon.Unidade[]>([])
const contagens = ref<Mon.Contagens>({
  todos: 0,
  noAr: 0,
  fora: 0,
  pendente: 0,
  manutencao: 0,
  pausado: 0,
  lento: 0,
  certificadoVencendo: 0
})
const q = reactive({
  pageNo: 1,
  pageSize: 50,
  situacao: undefined as string | undefined,
  busca: '',
  tipo: undefined as string | undefined,
  unidadeId: undefined as number | undefined,
  papel: undefined as string | undefined,
  ordenarPor: undefined as string | undefined,
  direcao: undefined as string | undefined
})
const filtrado = computed(() => !!(q.situacao || q.busca || q.tipo || q.unidadeId || q.papel))
const CHIPS = [
  { valor: undefined, rotulo: 'Todos', n: () => contagens.value.todos },
  {
    valor: 'problema',
    rotulo: 'Com problema',
    n: () => contagens.value.fora + contagens.value.pendente
  },
  { valor: 'fora', rotulo: 'Fora do ar', n: () => contagens.value.fora },
  { valor: 'pendente', rotulo: 'Pendentes', n: () => contagens.value.pendente },
  { valor: 'no_ar', rotulo: 'No ar', n: () => contagens.value.noAr },
  { valor: 'manutencao', rotulo: 'Manutenção', n: () => contagens.value.manutencao },
  { valor: 'pausado', rotulo: 'Pausados', n: () => contagens.value.pausado }
]

const rotuloTipo = (t: string) => TIPOS.find((x) => x.valor === t)?.rotulo ?? t
const classe = (r: Mon.MonitorResp) =>
  !r.ativo ? 'pausado' : r.estado == null ? 'novo' : r.lento ? 'lento' : `e${r.estado}`

// ------------------------------------------------------------------ seleção e ações em lote
type AcaoLote = 'excluir' | 'pausar' | 'retomar'
const podeAlterar = checkPermi(['monitoramento:monitor:update'])
const tabela = ref<TableInstance>()
const marcados = ref<Mon.MonitorResp[]>([])
/** "Selecionar todos os N": vale tudo o que o filtro alcança, não só a página aberta */
const todosDoFiltro = ref(false)
const executando = ref(false)
const message = useMessage()
const paginaInteira = computed(
  () => lista.value.length > 0 && marcados.value.length === lista.value.length
)
const quantosMarcados = computed(() => (todosDoFiltro.value ? total.value : marcados.value.length))
const marcar = (linhas: Mon.MonitorResp[]) => {
  marcados.value = linhas
  if (!paginaInteira.value) todosDoFiltro.value = false
}
const limparSelecao = () => {
  todosDoFiltro.value = false
  tabela.value?.clearSelection()
}
const VERBOS: Record<AcaoLote, [string, string]> = {
  excluir: ['Excluir', 'excluído(s)'],
  pausar: ['Pausar', 'pausado(s)'],
  retomar: ['Retomar', 'retomado(s)']
}
const executar = async (acao: AcaoLote) => {
  const n = quantosMarcados.value
  if (!n) return
  const [verbo, feito] = VERBOS[acao]
  const quais = todosDoFiltro.value
    ? `todos os ${n} monitores${filtrado.value ? ' do filtro' : ''}`
    : `${n} monitor(es)`
  try {
    if (acao === 'excluir') {
      await ElMessageBox.confirm(
        `Excluir ${quais}? O histórico de verificações deles também sai e não dá para desfazer. ` +
          'Se quiser uma cópia, use Exportar antes.',
        'Excluir monitores',
        {
          type: 'warning',
          confirmButtonText: `Excluir ${n}`,
          confirmButtonClass: 'el-button--danger',
          cancelButtonText: 'Cancelar'
        }
      )
    } else {
      await message.confirm(`${verbo} ${quais}?`)
    }
  } catch {
    return
  }
  executando.value = true
  try {
    const feitos = await Mon.executarLote({
      acao,
      ...(todosDoFiltro.value
        ? {
            todos: true,
            busca: q.busca || undefined,
            tipo: q.tipo,
            situacao: q.situacao,
            unidadeId: q.unidadeId,
            papel: q.papel
          }
        : { ids: marcados.value.map((m) => m.id) })
    })
    message.success(`${feitos} monitor(es) ${feito}.`)
    limparSelecao()
    if (acao === 'excluir') q.pageNo = 1
    await atualizar()
  } finally {
    executando.value = false
  }
}

const carregar = async () => {
  loading.value = true
  try {
    const r = await Mon.getMonitores(q)
    lista.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const atualizar = async () => {
  await Promise.all([
    carregar(),
    Mon.getContagens().then((c) => (contagens.value = c)),
    Mon.getEventos(7).then((e) => (eventos.value = e))
  ])
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}
// no servidor: a lista é paginada, e ordenar só a página aberta daria um resultado errado
const ordenar = ({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }) => {
  q.ordenarPor = order ? prop : undefined
  q.direcao = order === 'descending' ? 'desc' : order ? 'asc' : undefined
  buscar()
}
const exportar = async () => download.excel(await Mon.exportarMonitores(q), 'monitores.xlsx')
const filtrar = (s?: string) => {
  q.situacao = s
  buscar()
}

// batidas ao vivo: atualiza a linha na hora e recarrega contagens e eventos quando o estado muda
let recarga: ReturnType<typeof setTimeout> | undefined
useMonitorSocket().on('monitoramento-batida', (b: any) => {
  const linha = lista.value.find((m) => m.id === b.monitorId)
  if (linha) {
    linha.estado = b.estado
    linha.ultimaLatenciaMs = b.latenciaMs ?? undefined
    linha.ultimaMsg = b.msg
    linha.historico = [
      ...linha.historico,
      { momento: b.momento, estado: b.estado, latenciaMs: b.latenciaMs, msg: b.msg }
    ].slice(-40)
  }
  if (b.importante) {
    clearTimeout(recarga)
    recarga = setTimeout(atualizar, 800)
  }
})

onMounted(async () => {
  unidades.value = await Mon.getUnidades()
  await atualizar()
  if (route.query.id) detalhe.value?.abrir(Number(route.query.id))
})

// aviso do sino com a tela já aberta (ou guardada em cache): o onMounted não roda de novo
watch(
  () => route.query.id,
  (id) => {
    if (id && route.path === '/monitoramento/monitores') detalhe.value?.abrir(Number(id))
  }
)
</script>

<style lang="scss" scoped>
.ponto {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 6px;
  background: var(--el-border-color);
  border-radius: 50%;
  flex-shrink: 0;

  &.e0 {
    background: var(--el-color-danger);
  }

  &.e1 {
    background: var(--el-color-success);
  }

  &.e2,
  &.lento {
    background: var(--el-color-warning);
  }

  &.e3,
  &.pausado {
    background: var(--el-color-info);
  }
}
</style>
