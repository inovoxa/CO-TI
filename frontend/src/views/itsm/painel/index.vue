<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-10px">
      <el-radio-group v-model="atalho" @change="aplicarAtalho">
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
        v-model="categoriaId"
        class="!w-260px"
        clearable
        filterable
        placeholder="Todas as categorias"
        @change="carregar"
      >
        <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
      </el-select>
      <el-button @click="carregar"><Icon class="mr-4px" icon="ep:refresh" /> Atualizar</el-button>
      <el-button
        v-hasPermi="['itsm:chamado:exportar']"
        :disabled="!p"
        :loading="exportando"
        @click="exportarExcel"
      >
        <Icon class="mr-4px" icon="ep:download" /> Excel
      </el-button>
      <el-button :disabled="!p" @click="imprimir">
        <Icon class="mr-4px" icon="ep:printer" /> PDF
      </el-button>
    </div>
  </ContentWrap>

  <div v-loading="loading" class="painel-impressao">
    <template v-if="p">
      <div class="cabecalho-impressao">
        <h2>Painel do Service Desk</h2>
        <p>{{ descricaoFiltros }}</p>
      </div>
      <!-- números principais -->
      <div class="tiles">
        <div v-for="t in tiles" :key="t.rotulo" class="tile">
          <div class="rotulo">{{ t.rotulo }}</div>
          <div class="valor">{{ t.valor }}</div>
          <div class="nota">{{ t.nota }}</div>
        </div>
      </div>

      <el-row :gutter="12">
        <el-col :lg="16" :span="24">
          <ContentWrap title="Abertos e solucionados por dia">
            <Echart :height="300" :options="opSerie" />
          </ContentWrap>
        </el-col>
        <el-col :lg="8" :span="24">
          <ContentWrap title="Abertos agora, pela idade">
            <Echart :height="300" :options="opIdade" />
          </ContentWrap>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :lg="12" :span="24">
          <ContentWrap title="Categorias mais pedidas no período">
            <el-empty
              v-if="!p.porCategoria.length"
              :image-size="60"
              description="Sem chamados no período"
            />
            <Echart
              v-else
              :height="Math.max(160, p.porCategoria.length * 30 + 40)"
              :options="opBarras(p.porCategoria)"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :span="24">
          <ContentWrap title="Abertos por prioridade">
            <el-empty v-if="!p.porPrioridade.length" :image-size="60" description="Nada aberto" />
            <Echart
              v-else
              :height="Math.max(160, p.porPrioridade.length * 30 + 40)"
              :options="opBarras(p.porPrioridade)"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :span="24">
          <ContentWrap title="Por canal de entrada">
            <el-empty
              v-if="!p.porOrigem.length"
              :image-size="60"
              description="Sem chamados no período"
            />
            <Echart
              v-else
              :height="Math.max(160, p.porOrigem.length * 30 + 40)"
              :options="opBarras(p.porOrigem)"
            />
          </ContentWrap>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :lg="12" :span="24">
          <ContentWrap title="Técnicos">
            <el-table :data="p.porTecnico" size="small">
              <el-table-column label="Técnico" min-width="160" prop="nome" />
              <el-table-column align="right" label="Abertos agora" prop="abertos" width="120" />
              <el-table-column align="right" label="Solucionados" prop="solucionados" width="120" />
              <el-table-column align="right" label="Tempo médio" width="130">
                <template #default="{ row }">{{ duracao(row.mediaSolucaoSeg) }}</template>
              </el-table-column>
            </el-table>
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :span="24">
          <ContentWrap title="Equipes">
            <el-table :data="p.porEquipe" size="small">
              <el-table-column label="Equipe" min-width="160" prop="nome" />
              <el-table-column align="right" label="Abertos agora" prop="abertos" width="120" />
              <el-table-column align="right" label="Solucionados" prop="solucionados" width="120" />
            </el-table>
          </ContentWrap>
        </el-col>
      </el-row>
      <ContentWrap class="sem-impressao" title="Série diária (tabela)">
        <el-collapse>
          <el-collapse-item title="Ver os números por dia" name="t">
            <el-table :data="p.serie" max-height="320" size="small">
              <el-table-column label="Dia" prop="dia" />
              <el-table-column align="right" label="Abertos" prop="abertos" />
              <el-table-column align="right" label="Solucionados" prop="solucionados" />
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </ContentWrap>
    </template>
  </div>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import { Echart } from '@/components/Echart'
import { formatDate } from '@/utils/formatTime'
import { useAppStore } from '@/store/modules/app'
import download from '@/utils/download'
import * as ItsmApi from '@/api/itsm'
import { duracao } from '@/api/itsm'

defineOptions({ name: 'ItsmPainel' })
const appStore = useAppStore()

const loading = ref(false)
const p = ref<any>()
const atalho = ref(30)
const periodo = ref<string[]>([])
const categoriaId = ref<number>()
const categorias = ref<ItsmApi.Categoria[]>([])

// cores: paleta categórica validada (azul, laranja), com passos próprios no tema escuro
const escuro = computed(() => appStore.getIsDark)
const cor = computed(() =>
  escuro.value
    ? { s1: '#3987e5', s2: '#d95926', texto: '#c9c8c3', fraco: '#8a8984', grade: '#383835' }
    : { s1: '#2a78d6', s2: '#eb6834', texto: '#3d3d3a', fraco: '#76756f', grade: '#e8e7e3' }
)

// data local (toISOString daria o dia em UTC: à noite, já seria amanhã)
const dia = (d: Date) => formatDate(d, 'YYYY-MM-DD')
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
    p.value = await ItsmApi.getPainel({
      de: periodo.value[0],
      ate: periodo.value[1],
      categoriaId: categoriaId.value
    })
  } finally {
    loading.value = false
  }
}

const exportando = ref(false)
const exportarExcel = async () => {
  exportando.value = true
  try {
    const dados = await ItsmApi.exportarPainel({
      de: periodo.value[0],
      ate: periodo.value[1],
      categoriaId: categoriaId.value
    })
    download.excel(dados, `painel-service-desk-${periodo.value[0]}-a-${periodo.value[1]}.xlsx`)
  } finally {
    exportando.value = false
  }
}

const descricaoFiltros = computed(() => {
  const data = (d?: string) => (d ? d.split('-').reverse().join('/') : '')
  const categoria = categorias.value.find((c) => c.id === categoriaId.value)?.nomeCompleto
  return `${data(periodo.value[0])} a ${data(periodo.value[1])} · ${categoria ?? 'Todas as categorias'}`
})

// o navegador oferece "Salvar como PDF"; a classe no body mostra só o painel enquanto imprime
const imprimir = () => {
  const terminar = () => document.body.classList.remove('imprimindo-painel')
  window.addEventListener('afterprint', terminar, { once: true })
  document.body.classList.add('imprimindo-painel')
  window.print()
}

const fmt = (v: any, suf = '') => (v === null || v === undefined ? '—' : `${v}${suf}`)
const tiles = computed(() => {
  const r = p.value.resumo
  const s = p.value.satisfacao
  return [
    {
      rotulo: 'Abertos agora',
      valor: r.abertos,
      nota: `${r.semTecnico} sem técnico · ${r.pendentes} pendentes`
    },
    { rotulo: 'Prazo vencido', valor: r.vencidos, nota: 'abertos com a solução atrasada' },
    {
      rotulo: 'Novos no período',
      valor: r.novos,
      nota: `${r.solucionados} solucionados · ${r.fechados} fechados`
    },
    {
      rotulo: 'Solucionados no prazo',
      valor: fmt(r.percentualNoPrazo, '%'),
      nota: `${r.noPrazo} de ${r.comPrazo} com SLA`
    },
    {
      rotulo: 'Assumidos no prazo',
      valor: fmt(r.percentualAtendimentoNoPrazo, '%'),
      nota: `tempo médio ${duracao(r.mediaAssumirSeg)}`
    },
    {
      rotulo: 'Tempo médio de solução',
      valor: duracao(r.mediaSolucaoSeg),
      nota: 'em horário útil do SLA'
    },
    {
      rotulo: 'Satisfação',
      valor: s.media ? `${s.media} / 5` : '—',
      nota: `${s.respondidas} de ${s.enviadas} responderam`
    }
  ]
})

const eixos = () => ({
  axisLine: { lineStyle: { color: cor.value.grade } },
  axisLabel: { color: cor.value.fraco },
  splitLine: { lineStyle: { color: cor.value.grade } }
})

const opSerie = computed<EChartsOption>(() => {
  const s = p.value.serie as any[]
  const linha = (nome: string, campo: string, c: string) => ({
    name: nome,
    type: 'line' as const,
    data: s.map((x) => x[campo]),
    showSymbol: s.length <= 31,
    symbolSize: 8,
    lineStyle: { width: 2, color: c },
    itemStyle: { color: c },
    // rótulo direto no fim da linha: a identidade não depende só da cor
    endLabel: { show: true, formatter: nome, color: cor.value.texto }
  })
  return {
    grid: { left: 40, right: 100, top: 30, bottom: 30 },
    legend: { top: 0, left: 0, textStyle: { color: cor.value.texto } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'line' } },
    xAxis: {
      type: 'category',
      data: s.map((x) => x.dia.slice(5).split('-').reverse().join('/')),
      ...eixos(),
      splitLine: { show: false }
    },
    yAxis: { type: 'value', minInterval: 1, ...eixos() },
    series: [
      linha('Abertos', 'abertos', cor.value.s1),
      linha('Solucionados', 'solucionados', cor.value.s2)
    ]
  }
})

const opBarras = (lista: { nome: string; total: number }[]): EChartsOption => ({
  grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
  tooltip: { trigger: 'item' },
  xAxis: { type: 'value', show: false },
  yAxis: {
    type: 'category',
    inverse: true,
    data: lista.map((x) => x.nome),
    axisTick: { show: false },
    axisLine: { show: false },
    axisLabel: { color: cor.value.texto, width: 220, overflow: 'truncate' }
  },
  series: [
    {
      type: 'bar',
      data: lista.map((x) => x.total),
      barMaxWidth: 18,
      itemStyle: { color: cor.value.s1, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: cor.value.texto }
    }
  ]
})

const opIdade = computed<EChartsOption>(() => {
  const i = p.value.idade
  return opBarras([
    { nome: 'até 1 dia', total: i.ate1d },
    { nome: '1 a 3 dias', total: i.ate3d },
    { nome: '3 a 7 dias', total: i.ate7d },
    { nome: '7 a 30 dias', total: i.ate30d },
    { nome: 'mais de 30 dias', total: i.mais30d }
  ])
})

onMounted(async () => {
  categorias.value = await ItsmApi.getCategorias(true)
  aplicarAtalho()
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
.cabecalho-impressao {
  display: none;
}
</style>

<!-- sem scoped: precisa esconder o menu e soltar as rolagens do layout, que estão fora deste componente -->
<style>
@media print {
  body.imprimindo-painel,
  body.imprimindo-painel #app,
  body.imprimindo-painel .el-scrollbar,
  body.imprimindo-painel .el-scrollbar__wrap {
    height: auto !important;
    overflow: visible !important;
  }
  body.imprimindo-painel * {
    visibility: hidden;
  }
  body.imprimindo-painel .painel-impressao,
  body.imprimindo-painel .painel-impressao * {
    visibility: visible;
  }
  body.imprimindo-painel .painel-impressao {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
  }
  body.imprimindo-painel .painel-impressao .cabecalho-impressao {
    display: block;
    margin-bottom: 12px;
  }
  body.imprimindo-painel .painel-impressao .sem-impressao {
    display: none;
  }
  body.imprimindo-painel .painel-impressao canvas {
    max-width: 100%;
  }
  body.imprimindo-painel .painel-impressao .el-card,
  body.imprimindo-painel .painel-impressao .tile {
    break-inside: avoid;
  }
}
</style>
