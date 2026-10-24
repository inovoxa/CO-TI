<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-10px">
      <el-radio-group v-model="atalho" @change="aplicarAtalho">
        <el-radio-button :value="1">Hoje</el-radio-button>
        <el-radio-button :value="7">7 dias</el-radio-button>
        <el-radio-button :value="30">30 dias</el-radio-button>
        <el-radio-button :value="90">90 dias</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-model="periodo"
        :clearable="false"
        end-placeholder="Até"
        start-placeholder="De"
        type="daterange"
        value-format="YYYY-MM-DD"
        @change="periodoManual"
      />
      <el-select
        v-model="caixaId"
        class="!w-200px"
        clearable
        placeholder="Todas as caixas"
        @change="carregar"
      >
        <el-option v-for="c in caixas" :key="c.id" :label="c.nome" :value="c.id" />
      </el-select>
      <el-select
        v-model="agenteId"
        class="!w-200px"
        clearable
        filterable
        placeholder="Todos os atendentes"
        @change="carregar"
      >
        <el-option v-for="u in atendentes" :key="u.id" :label="u.nome" :value="u.id" />
      </el-select>
      <el-checkbox v-model="util" @change="carregar">só em horário de atendimento</el-checkbox>
      <el-button @click="carregar"><Icon class="mr-4px" icon="ep:refresh" /> Atualizar</el-button>
    </div>
  </ContentWrap>

  <div v-loading="loading">
    <template v-if="r">
      <div class="tiles">
        <div v-for="t in tiles" :key="t.rotulo" class="tile">
          <div class="rotulo">{{ t.rotulo }}</div>
          <div class="valor">{{ t.valor }}</div>
          <div class="nota">{{ t.nota }}</div>
        </div>
      </div>

      <el-row :gutter="12">
        <el-col :lg="16" :span="24">
          <ContentWrap title="Conversas novas e resolvidas por dia">
            <Echart :height="280" :options="opSerie" />
          </ContentWrap>
        </el-col>
        <el-col :lg="8" :span="24">
          <ContentWrap title="Notas da pesquisa de satisfação">
            <el-empty
              v-if="!r.satisfacao.respondidas"
              :image-size="60"
              description="Nenhuma resposta no período"
            />
            <Echart v-else :height="280" :options="opNotas" />
          </ContentWrap>
        </el-col>
      </el-row>

      <ContentWrap
        title="Quando os contatos escrevem (mensagens recebidas por dia da semana e hora)"
      >
        <Echart :height="250" :options="opMapa" />
      </ContentWrap>

      <el-row :gutter="12">
        <el-col :lg="14" :span="24">
          <ContentWrap title="Atendentes">
            <el-table :data="r.porAtendente" size="small">
              <el-table-column label="Atendente" min-width="150" prop="nome" />
              <el-table-column align="right" label="Resolvidas" prop="resolvidas" width="100" />
              <el-table-column align="right" label="1ª resposta" width="110">
                <template #default="{ row }">{{ duracao(row.primeiraRespostaMediaSeg) }}</template>
              </el-table-column>
              <el-table-column align="right" label="Resolução" width="110">
                <template #default="{ row }">{{ duracao(row.resolucaoMediaSeg) }}</template>
              </el-table-column>
              <el-table-column align="right" label="Satisfação" width="120">
                <template #default="{ row }">{{
                  row.csatMedia ? `${row.csatMedia} (${row.avaliacoes})` : '—'
                }}</template>
              </el-table-column>
            </el-table>
          </ContentWrap>
        </el-col>
        <el-col :lg="10" :span="24">
          <ContentWrap title="Caixas">
            <el-table :data="r.porCaixa" size="small">
              <el-table-column label="Caixa" min-width="150" prop="nome" />
              <el-table-column align="right" label="Resolvidas" prop="resolvidas" width="100" />
              <el-table-column align="right" label="1ª resposta" width="110">
                <template #default="{ row }">{{ duracao(row.primeiraRespostaMediaSeg) }}</template>
              </el-table-column>
              <el-table-column align="right" label="Satisfação" width="110">
                <template #default="{ row }">{{ row.csatMedia ?? '—' }}</template>
              </el-table-column>
            </el-table>
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="12" :span="24">
          <ContentWrap title="Avaliações mais recentes">
            <el-empty v-if="!r.satisfacao.ultimas.length" :image-size="60" description="Nenhuma" />
            <el-table v-else :data="r.satisfacao.ultimas" size="small">
              <el-table-column label="Nota" width="70">
                <template #default="{ row }"
                  ><b>{{ row.nota }}</b
                  ><span class="nota"> / 5</span></template
                >
              </el-table-column>
              <el-table-column label="Contato" min-width="140" prop="contato" />
              <el-table-column label="Atendente" min-width="120">
                <template #default="{ row }">{{ row.atendente || '—' }}</template>
              </el-table-column>
              <el-table-column label="Quando" width="110">
                <template #default="{ row }">{{ hora(row.respondidaEm) }}</template>
              </el-table-column>
              <el-table-column width="60">
                <template #default="{ row }">
                  <el-button link type="primary" @click="abrirConversa(row.conversaId)"
                    ><Icon icon="ep:right"
                  /></el-button>
                </template>
              </el-table-column>
            </el-table>
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :span="24">
          <ContentWrap title="Série diária (tabela)">
            <el-table :data="r.serie" max-height="320" size="small">
              <el-table-column label="Dia">
                <template #default="{ row }">{{ diaCurto(row.dia) }}</template>
              </el-table-column>
              <el-table-column align="right" label="Novas" prop="novas" />
              <el-table-column align="right" label="Resolvidas" prop="resolvidas" />
            </el-table>
          </ContentWrap>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import { Echart } from '@/components/Echart'
import { formatDate } from '@/utils/formatTime'
import { useAppStore } from '@/store/modules/app'
import { duracao } from '@/api/itsm'
import * as Atd from '@/api/atendimento'

defineOptions({ name: 'AtendimentoRelatorio' })
const appStore = useAppStore()
const { push } = useRouter()

const loading = ref(false)
const r = ref<Atd.Relatorio>()
const atalho = ref(7)
const periodo = ref<string[]>([])
const caixaId = ref<number>()
const agenteId = ref<number>()
const util = ref(false)
const caixas = ref<{ id: number; nome: string }[]>([])
const atendentes = ref<{ id: number; nome: string }[]>([])

// paleta categórica validada (a mesma do painel do Service Desk); sequencial de um só tom para o mapa
const escuro = computed(() => appStore.getIsDark)
const cor = computed(() =>
  escuro.value
    ? {
        s1: '#3987e5',
        s2: '#d95926',
        texto: '#c9c8c3',
        fraco: '#8a8984',
        grade: '#383835',
        seq: ['#1e2a38', '#2b5d99', '#3987e5', '#a8c9f2']
      }
    : {
        s1: '#2a78d6',
        s2: '#eb6834',
        texto: '#3d3d3a',
        fraco: '#76756f',
        grade: '#e8e7e3',
        seq: ['#f1f6fd', '#a8c9f2', '#2a78d6', '#173f73']
      }
)

const dia = (d: Date) => formatDate(d, 'YYYY-MM-DD')
const diaCurto = (s: string) => s.slice(5).split('-').reverse().join('/')
const hora = (t: number) => (t ? formatDate(new Date(t), 'DD/MM HH:mm') : '')
const aplicarAtalho = () => {
  const fim = new Date()
  const ini = new Date()
  ini.setDate(fim.getDate() - (atalho.value - 1))
  periodo.value = [dia(ini), dia(fim)]
  carregar()
}
const periodoManual = () => {
  atalho.value = 0
  carregar()
}

const carregar = async () => {
  loading.value = true
  try {
    r.value = await Atd.getRelatorio({
      de: periodo.value[0],
      ate: periodo.value[1],
      caixaId: caixaId.value,
      agenteId: agenteId.value,
      horarioUtil: util.value
    })
  } finally {
    loading.value = false
  }
}

const tiles = computed(() => {
  const x = r.value!
  const s = x.satisfacao
  return [
    {
      rotulo: 'Abertas agora',
      valor: x.abertas,
      nota: `${x.semAtendente} sem atendente · ${x.pendentes} pendentes`
    },
    { rotulo: 'Conversas novas', valor: x.novas, nota: `${x.resolvidas} resolvidas no período` },
    {
      rotulo: 'Primeira resposta',
      valor: duracao(x.primeiraRespostaMedianaSeg),
      nota: `mediana · média ${duracao(x.primeiraRespostaMediaSeg)}`
    },
    {
      rotulo: 'Tempo até resolver',
      valor: duracao(x.resolucaoMedianaSeg),
      nota: `mediana · média ${duracao(x.resolucaoMediaSeg)}`
    },
    {
      rotulo: 'Mensagens',
      valor: x.mensagensRecebidas,
      nota: `recebidas · ${x.mensagensEnviadas} enviadas`
    },
    {
      rotulo: 'Satisfação',
      valor: s.media ? `${s.media} / 5` : '—',
      nota: s.enviadas
        ? `${s.respondidas} de ${s.enviadas} responderam${s.percentualSatisfeitos != null ? ` · ${s.percentualSatisfeitos}% notas 4 e 5` : ''}`
        : 'pesquisa não enviada no período'
    }
  ]
})

const eixos = () => ({
  axisLine: { lineStyle: { color: cor.value.grade } },
  axisLabel: { color: cor.value.fraco },
  splitLine: { lineStyle: { color: cor.value.grade } }
})

const opSerie = computed<EChartsOption>(() => {
  const s = r.value!.serie
  const linha = (nome: string, campo: 'novas' | 'resolvidas', c: string) => ({
    name: nome,
    type: 'line' as const,
    data: s.map((x) => x[campo]),
    showSymbol: s.length <= 31,
    symbolSize: 8,
    lineStyle: { width: 2, color: c },
    itemStyle: { color: c },
    endLabel: { show: true, formatter: nome, color: cor.value.texto }
  })
  return {
    grid: { left: 40, right: 100, top: 30, bottom: 30 },
    legend: { top: 0, left: 0, textStyle: { color: cor.value.texto } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'line' } },
    xAxis: {
      type: 'category',
      data: s.map((x) => diaCurto(x.dia)),
      ...eixos(),
      splitLine: { show: false }
    },
    yAxis: { type: 'value', minInterval: 1, ...eixos() },
    series: [linha('Novas', 'novas', cor.value.s1), linha('Resolvidas', 'resolvidas', cor.value.s2)]
  }
})

const opNotas = computed<EChartsOption>(() => ({
  grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
  tooltip: { trigger: 'item', formatter: '{b}: {c} respostas' },
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    inverse: true,
    data: ['5 — ótimo', '4', '3', '2', '1 — ruim'],
    axisTick: { show: false },
    axisLine: { show: false },
    axisLabel: { color: cor.value.texto }
  },
  series: [
    {
      type: 'bar',
      data: [...r.value!.satisfacao.distribuicao].reverse(),
      barMaxWidth: 18,
      itemStyle: { color: cor.value.s1, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: cor.value.texto }
    }
  ]
}))

const DIAS = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom']
const opMapa = computed<EChartsOption>(() => {
  const dados: number[][] = []
  r.value!.porHora.forEach((linha, d) => linha.forEach((n, h) => dados.push([h, d, n])))
  const max = Math.max(1, ...dados.map((x) => x[2]))
  return {
    grid: { left: 40, right: 20, top: 10, bottom: 50 },
    tooltip: {
      formatter: (p: any) => `${DIAS[p.value[1]]}, ${p.value[0]}h: ${p.value[2]} mensagens`
    },
    xAxis: {
      type: 'category',
      data: Array.from({ length: 24 }, (_, h) => `${h}h`),
      ...eixos(),
      splitLine: { show: false }
    },
    yAxis: { type: 'category', data: DIAS, inverse: true, ...eixos(), splitLine: { show: false } },
    visualMap: {
      min: 0,
      max,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemHeight: 120,
      calculable: false,
      inRange: { color: cor.value.seq },
      textStyle: { color: cor.value.fraco }
    },
    series: [
      {
        type: 'heatmap',
        data: dados,
        itemStyle: {
          borderColor: escuro.value ? '#141414' : '#ffffff',
          borderWidth: 2,
          borderRadius: 3
        }
      }
    ]
  }
})

const abrirConversa = (id: number) => push({ path: '/atendimento/conversas', query: { id } })

onMounted(async () => {
  aplicarAtalho()
  const f = await Atd.getFiltrosRelatorio()
  caixas.value = f.caixas
  atendentes.value = f.atendentes
})
</script>

<style scoped>
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.tile {
  padding: 14px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.rotulo {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.valor {
  margin: 4px 0;
  font-size: 26px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}
.nota {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
