<!-- Detalhes de uma pesquisa de logs (aberta pelo assistente), no estilo do monitor do Redis: informações, gráficos e
     o ranking com a máquina e a pessoa de cada IP. -->
<template>
  <div class="analise">
    <el-row :gutter="12">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="cab">
              <span><Icon class="mr-6px" icon="ep:data-analysis" />Detalhes da pesquisa</span>
              <div class="acoes">
                <el-select v-model="campo" class="!w-200px" filterable allow-create size="small" @change="trocarCampo">
                  <el-option v-for="c in opcoesCampo" :key="c" :label="`Agrupar por ${c}`" :value="c" />
                </el-select>
                <el-select v-model="soma" class="!w-230px" size="small" @change="trocarMedida">
                  <el-option v-for="m in Api.MEDIDAS" :key="m.valor" :label="`Medir: ${m.rotulo}`" :value="m.valor" />
                </el-select>
                <el-button size="small" @click="carregar"><Icon icon="ep:refresh" /></el-button>
                <el-button size="small" type="success" @click="abrirNaBusca">
                  <Icon class="mr-4px" icon="ep:search" />Abrir na busca
                </el-button>
              </div>
            </div>
          </template>
          <div v-if="pergunta" class="pergunta"><Icon class="mr-6px" icon="ep:magic-stick" />{{ pergunta }}</div>
          <el-descriptions :column="colunas" border>
            <el-descriptions-item label="Consulta:" :span="colunas">
              <code class="consulta">{{ filtro.consulta || '(tudo)' }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="Fluxo:">{{ nomeFluxo }}</el-descriptions-item>
            <el-descriptions-item label="Período:">{{ textoPeriodo }}</el-descriptions-item>
            <el-descriptions-item label="Mensagens:">
              <b>{{ contagem ? Api.numero(contagem.valor) : '…' }}</b>
            </el-descriptions-item>
            <el-descriptions-item v-if="soma" label="Banda:">
              <b>{{ contagemBanda ? fmt(contagemBanda.valor) : '…' }}</b>
            </el-descriptions-item>
            <el-descriptions-item label="Período anterior:">
              <template v-if="principal?.anterior !== undefined && principal?.anterior !== null">
                {{ fmt(principal.anterior) }}
                <span :class="['variacao', variacao.classe]">{{ variacao.texto }}</span>
              </template>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="distinto" :label="`${distinto} distintos:`">
              {{ contagemDistinta ? Api.numero(contagemDistinta.valor) : '…' }}
            </el-descriptions-item>
            <el-descriptions-item :label="`${campo} diferentes:`">
              {{ ranking ? (ranking.itens.length >= LIMITE ? `${LIMITE}+` : ranking.itens.length) : '…' }}
            </el-descriptions-item>
            <el-descriptions-item :label="soma ? 'Maior consumo:' : 'Maior valor:'">
              <template v-if="ranking?.itens[0]">
                {{ ranking.itens[0].valor }} ({{ soma ? fmt(ranking.itens[0].total) + ', ' : ''
                }}{{ pctTotal(ranking.itens[0].total) }})
              </template>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="Tempo da consulta:">{{ tempo }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="erro" class="erro"><Icon icon="ep:warning" /> {{ erro }}</div>
        </el-card>
      </el-col>

      <el-col :lg="12" :span="24" class="mt-12px">
        <el-card shadow="hover">
          <Echart v-if="ranking?.itens.length" :options="opcoesPizza" :height="380" />
          <el-empty v-else :image-size="70" description="Nada no período" class="h-380px" />
        </el-card>
      </el-col>
      <el-col :lg="12" :span="24" class="mt-12px">
        <el-card shadow="hover">
          <Echart v-if="histograma" :options="opcoesHistograma" :height="380" @click="clicarBarra" />
          <div v-else class="h-380px" v-loading="true"></div>
        </el-card>
      </el-col>

      <el-col :span="24" class="mt-12px">
        <el-card shadow="hover">
          <template #header>
            <span>{{ soma ? 'Consumo de banda' : 'Ranking' }} por {{ campo }}</span>
            <span v-if="temDonos" class="dica"> · máquina e pessoa conforme o inventário atual</span>
          </template>
          <el-table v-loading="!ranking" :data="linhas" size="small" stripe>
            <el-table-column label="#" type="index" width="50" />
            <el-table-column :label="campo" min-width="200">
              <template #default="{ row }">
                <ValorClicavel :campo="campo" :valor="row.valor" :base="filtro" />
              </template>
            </el-table-column>
            <el-table-column v-if="temDonos" label="Máquina" min-width="130">
              <template #default="{ row }">
                <router-link
                  v-if="row.ativo"
                  class="link"
                  :to="{ name: 'InventarioAtivo', query: { id: row.ativo.id } }"
                  >{{ row.ativo.nome }}</router-link
                >
              </template>
            </el-table-column>
            <el-table-column v-if="temDonos" label="Pessoa" min-width="170">
              <template #default="{ row }">{{ row.ativo?.usuarioNome || row.ativo?.usuarioLogin }}</template>
            </el-table-column>
            <el-table-column v-if="temDonos" label="Unidade" min-width="150">
              <template #default="{ row }">{{ row.ativo?.deptNome }}</template>
            </el-table-column>
            <el-table-column v-if="soma" align="right" label="Banda" width="110">
              <template #default="{ row }">{{ fmt(row.total) }}</template>
            </el-table-column>
            <el-table-column align="right" :label="soma ? 'Sessões' : 'Mensagens'" width="110">
              <template #default="{ row }">{{ Api.numero(soma ? row.mensagens : row.total) }}</template>
            </el-table-column>
            <el-table-column label="% do total" width="170">
              <template #default="{ row }">
                <el-progress :percentage="pct(row.total)" :stroke-width="10" :format="() => pctTotal(row.total)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="24" class="mt-12px">
        <el-card shadow="hover">
          <template #header>Mensagens mais recentes</template>
          <ListaMensagens :mensagens="mensagens?.lista ?? []" :colunas="[]" :base="filtro" :carregando="!mensagens" />
        </el-card>
      </el-col>
    </el-row>

    <Gavetas ref="gavetas" />
  </div>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as Api from '@/api/logs'
import * as Inventario from '@/api/inventario'
import { checkPermi } from '@/utils/permission'
import { useWindowSize } from '@vueuse/core'
import { useAppStore } from '@/store/modules/app'
import { CONTEXTO, rotuloPeriodo, quando } from '../contexto'
import Gavetas from '../components/Gavetas.vue'
import ListaMensagens from '../components/ListaMensagens.vue'
import ValorClicavel from '../components/ValorClicavel.vue'

defineOptions({ name: 'LogsAnalise' })

const LIMITE = 25
const CAMPOS_IP = ['srcip', 'dstip', 'ip', 'ip_origem']
const CAMPO_PADRAO: Record<string, string> = {
  firewall: 'hostname',
  contas: 'usuario',
  arquivos: 'usuario',
  maquinas: 'origem'
}

const route = useRoute()
const router = useRouter()
const { width } = useWindowSize()
const appStore = useAppStore()
/** Título dos gráficos: o tema escuro do ECharts não muda a cor do título */
const titulo = (text: string, subtext?: string) => ({
  text,
  subtext,
  left: 'center',
  textStyle: { fontSize: 15, color: appStore.getIsDark ? '#e5eaf3' : '#303133' },
  subtextStyle: { color: appStore.getIsDark ? '#a3a6ad' : '#909399' }
})
const colunas = computed(() => (width.value < 768 ? 1 : width.value < 1300 ? 2 : 4))

const pergunta = ref('')
const campo = ref('')
const distinto = ref('')
/** '' = contar mensagens; bytes/rcvdbyte/sentbyte = somar a banda */
const soma = ref('')
const filtro = ref<Api.Filtro>({})
const contagem = ref<Api.Contagem>()
const contagemDistinta = ref<Api.Contagem>()
const contagemBanda = ref<Api.Contagem>()
const ranking = ref<Api.Ranking>()
const histograma = ref<Api.Histograma>()
const mensagens = ref<Api.Mensagens>()
const donos = ref<Record<string, Inventario.Ativo>>({})
const erro = ref('')
const gavetas = ref<InstanceType<typeof Gavetas>>()

provide(CONTEXTO, {
  filtroGeral: ref(''),
  periodoGeral: ref(''),
  atualizacao: ref(0),
  // "adicionar ao filtro" leva para a busca com o termo somado
  filtrar: (c, v, excluir) =>
    abrirNaBusca(Api.juntar(filtro.value.consulta, (excluir ? '-' : '') + Api.termo(c, v))),
  mostrarMensagens: (titulo, f) => gavetas.value?.mostrarMensagens(titulo, f),
  abrirMensagem: (m) => gavetas.value?.abrirMensagem(m)
})

/** O assistente manda datas como AAAA-MM-DD ou AAAA-MM-DDTHH:MM */
const dataHora = (s?: string) => (!s ? undefined : s.length === 10 ? s + 'T00:00:00' : s.length === 16 ? s + ':00' : s)

const lerRota = () => {
  const q = route.query
  const de = dataHora(q.de as string)
  filtro.value = {
    consulta: (q.q as string) || '',
    fluxo: ((q.fluxo as string) || '') as Api.Fluxo | '',
    ...(de ? { de, ate: dataHora(q.ate as string) } : { periodo: (q.periodo as string) || '1d' })
  }
  pergunta.value = (q.pergunta as string) || ''
  distinto.value = (q.distinto as string) || ''
  soma.value = (q.soma as string) || ''
  campo.value = (q.campo as string) || CAMPO_PADRAO[filtro.value.fluxo || ''] || 'fluxo'
}

const opcoesCampo = computed(() => {
  const base = {
    firewall: ['hostname', 'srcip', 'catdesc', 'action', 'dstip', 'user'],
    contas: ['usuario', 'computador', 'ip_origem', 'event_id', 'acao'],
    arquivos: ['usuario', 'compartilhamento', 'objeto', 'acao', 'ip_origem'],
    maquinas: ['origem', 'aplicacao', 'event_id', 'nivel'],
    geral: ['origem', 'aplicacao']
  }[filtro.value.fluxo || ''] ?? ['fluxo', 'origem', 'aplicacao']
  return base.includes(campo.value) ? base : [campo.value, ...base]
})

const nomeFluxo = computed(() => Api.FLUXOS.find((f) => f.valor === (filtro.value.fluxo || ''))?.rotulo ?? '')
const textoPeriodo = computed(() =>
  filtro.value.de
    ? `${quando(filtro.value.de)} até ${filtro.value.ate ? quando(filtro.value.ate) : 'agora'}`
    : rotuloPeriodo(filtro.value.periodo)
)
const tempo = computed(() => {
  const ms = [contagem.value?.ms, ranking.value?.ms, histograma.value?.ms].filter((x) => x !== undefined) as number[]
  return ms.length ? `${ms.reduce((a, b) => a + b, 0)} ms` : '…'
})

/** Medida principal: a banda quando soma, senão a quantidade de mensagens */
const principal = computed(() => (soma.value ? contagemBanda.value : contagem.value))
const fmt = (n?: number) => Api.valorDe(soma.value || undefined, n)

const variacao = computed(() => {
  const c = principal.value
  if (!c || c.anterior === undefined || c.anterior === null) return { texto: '', classe: '' }
  const d = c.valor - c.anterior
  const pct = c.anterior ? Math.round((d / c.anterior) * 100) : 0
  return {
    texto: `${d >= 0 ? '+' : ''}${fmt(d)}${c.anterior ? ` / ${pct >= 0 ? '+' : ''}${pct}%` : ''}`,
    classe: d > 0 ? 'subiu' : d < 0 ? 'desceu' : ''
  }
})

// porcentagem sobre o total de mensagens que têm o campo (o ranking traz o total)
const pct = (n: number) => Math.round((n / (ranking.value?.total || 1)) * 1000) / 10
const pctTotal = (n: number) => `${pct(n).toLocaleString('pt-BR')}%`

const temDonos = computed(() => Object.keys(donos.value).length > 0)
const linhas = computed(() => (ranking.value?.itens ?? []).map((i) => ({ ...i, ativo: donos.value[i.valor] })))

const opcoesPizza = computed<EChartsOption>(() => {
  const itens = ranking.value?.itens ?? []
  // só os 10 maiores: com muitos valores, uma fatia "Outros" engolia o gráfico (a tabela traz o % do total)
  const dados = itens.slice(0, 10).map((i) => ({
    name: donos.value[i.valor] ? `${i.valor} (${donos.value[i.valor].nome})` : i.valor,
    value: i.total
  }))
  return {
    title: titulo(`Distribuição por ${campo.value}`, itens.length > 10 ? 'Os 10 maiores' : undefined),
    tooltip: {
      trigger: 'item',
      formatter: (p: any) =>
        `${p.name}<br/>${soma.value ? fmt(p.value) : Api.numero(p.value) + ' mensagens'} (${p.percent}%)`
    },
    legend: { type: 'scroll', orient: 'vertical', left: 0, top: 40, bottom: 10, textStyle: { width: 160, overflow: 'truncate' } },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['65%', '55%'],
        itemStyle: { borderRadius: 4, borderColor: 'transparent', borderWidth: 2 },
        label: { show: false },
        data: dados
      }
    ]
  }
})

const rotuloMomento = (s: string) => {
  const d = new Date(s)
  return (histograma.value?.intervalo ?? 3600) >= 86400
    ? d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    : d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

const opcoesHistograma = computed<EChartsOption>(() => ({
  title: titulo(soma.value ? 'Banda ao longo do tempo' : 'Mensagens ao longo do tempo'),
  grid: { left: soma.value ? 70 : 50, right: 16, top: 50, bottom: 30 },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v) => fmt(Number(v)) },
  xAxis: {
    type: 'category',
    data: (histograma.value?.momentos ?? []).map(rotuloMomento),
    axisLabel: { hideOverlap: true, fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { opacity: 0.25 } },
    axisLabel: { formatter: (v: number) => (soma.value ? fmt(v) : String(v)) }
  },
  series: [
    { type: 'bar', name: soma.value ? 'Banda' : 'Mensagens', data: histograma.value?.totais ?? [], itemStyle: { borderRadius: [2, 2, 0, 0] } }
  ]
}))

const local = (x: Date) => new Date(x.getTime() - x.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
const clicarBarra = (p: any) => {
  const h = histograma.value
  if (!h || p?.dataIndex === undefined) return
  const de = new Date(h.momentos[p.dataIndex])
  const ate = new Date(de.getTime() + h.intervalo * 1000)
  gavetas.value?.mostrarMensagens(rotuloMomento(h.momentos[p.dataIndex]), {
    ...filtro.value,
    periodo: undefined,
    de: local(de),
    ate: local(ate)
  })
}

const carregarRanking = async () => {
  ranking.value = undefined
  donos.value = {}
  const r = await Api.getRanking(filtro.value, campo.value, LIMITE, soma.value)
  ranking.value = r
  if (CAMPOS_IP.includes(campo.value) && r.itens.length && checkPermi(['inventario:ativo:query'])) {
    const ativos = await Inventario.getAtivosDosIps(r.itens.map((i) => i.valor)).catch(() => [])
    const m: Record<string, Inventario.Ativo> = {}
    for (const a of ativos) if (a.ip && !m[a.ip]) m[a.ip] = a
    donos.value = m
  }
}

const carregar = async () => {
  erro.value = ''
  contagem.value = contagemDistinta.value = contagemBanda.value = histograma.value = mensagens.value = undefined
  const falhou = (e: any) => {
    erro.value = e?.msg || e?.message || 'Consulta inválida'
  }
  await Promise.all([
    Api.getContagem(filtro.value).then((c) => (contagem.value = c)),
    distinto.value
      ? Api.getContagem(filtro.value, distinto.value).then((c) => (contagemDistinta.value = c))
      : Promise.resolve(),
    soma.value
      ? Api.getContagem(filtro.value, undefined, soma.value).then((c) => (contagemBanda.value = c))
      : Promise.resolve(),
    carregarRanking(),
    Api.getHistograma(filtro.value, soma.value).then((h) => (histograma.value = h)),
    Api.getMensagens(filtro.value, 1, 20).then((m) => (mensagens.value = m))
  ]).catch(falhou)
}

const trocarCampo = () => {
  router.replace({ query: { ...route.query, campo: campo.value } })
  carregarRanking().catch((e) => (erro.value = e?.msg || 'Consulta inválida'))
}

const trocarMedida = () => {
  router.replace({ query: { ...route.query, soma: soma.value || undefined } })
  carregar()
}

const abrirNaBusca = (consulta?: string) => {
  const f = filtro.value
  router.push({
    name: 'LogsBusca',
    query: {
      q: (typeof consulta === 'string' ? consulta : f.consulta) || undefined,
      fluxo: f.fluxo || undefined,
      periodo: f.periodo,
      de: f.de,
      ate: f.ate
    }
  })
}

// a mesma tela é reaberta pelo assistente com outra pesquisa
watch(
  () => route.query,
  () => {
    if (route.name !== 'LogsAnalise') return
    const antes = JSON.stringify([filtro.value, distinto.value, soma.value])
    lerRota()
    if (antes !== JSON.stringify([filtro.value, distinto.value, soma.value])) carregar()
  }
)

onMounted(() => {
  lerRota()
  carregar()
})
</script>

<style lang="scss" scoped>
.cab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;

  .acoes {
    display: flex;
    gap: 6px;
    align-items: center;
  }
}

.pergunta {
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 14px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
}

.consulta {
  font-family: Consolas, 'Courier New', monospace;
  word-break: break-all;
}

.variacao {
  margin-left: 6px;
  font-size: 12px;

  &.subiu {
    color: var(--el-color-danger);
  }

  &.desceu {
    color: var(--el-color-success);
  }
}

.erro {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.link {
  color: var(--el-color-primary);
}
</style>
