<!-- Painel do inventário: totais e distribuições do parque. Clicar num número ou numa barra abre a lista filtrada. -->
<template>
  <div v-loading="loading">
    <template v-if="p">
      <el-row :gutter="12">
        <el-col v-for="k in CARTOES" :key="k.chave" :lg="4" :md="6" :sm="8" :xs="12">
          <div
            :class="[
              'cartao',
              { clicavel: k.ir, alerta: k.alerta && (p.totais[k.chave] ?? 0) > 0 }
            ]"
            @click="k.ir && k.ir()"
          >
            <Icon :icon="k.icone" class="icone" />
            <div class="rotulo">{{ k.rotulo }}</div>
            <div class="valor">{{ (p.totais[k.chave] ?? 0).toLocaleString('pt-BR') }}</div>
            <div v-if="k.sub" class="sub">{{ k.sub(p) }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="8" :xs="24">
          <ContentWrap title="Tipos de equipamento">
            <Echart
              :height="260"
              :options="rosca('tipo', rotuloTipo)"
              @click="(e) => clicar(e, 'tipo')"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="8" :xs="24">
          <ContentWrap title="Situação">
            <Echart
              :height="260"
              :options="rosca('situacao', rotuloSituacao)"
              @click="(e) => clicar(e, 'situacao')"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="8" :xs="24">
          <ContentWrap title="Origem dos dados">
            <Echart
              :height="260"
              :options="rosca('origem', (v) => ORIGENS[v] ?? v)"
              @click="(e) => clicar(e, 'origem')"
            />
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Inventários recebidos por dia (máquinas, últimos 30 dias)">
            <Echart :height="260" :options="linha('inventariosPorDia', dia)" />
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Equipamentos novos por mês">
            <Echart :height="260" :options="colunas('novosPorMes', mes)" />
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Sistemas operacionais (computadores)">
            <Echart
              :height="alturaBarras('sistema')"
              :options="barras('sistema', (v) => semSuporte.has(v))"
              @click="(e) => clicar(e, 'sistema')"
            />
            <div v-if="semSuporte.size" class="ajuda">Em vermelho: sem suporte do fabricante.</div>
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Equipamentos por unidade (15 maiores)">
            <Echart :height="alturaBarras('unidade')" :options="barras('unidade')" />
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap title="Memória">
            <Echart :height="240" :options="colunas('memoria')" />
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap title="Tipo de disco">
            <Echart
              :height="240"
              :options="rosca('disco', rotuloDisco)"
              @click="(e) => clicar(e, 'discoTipo', 'sem_info')"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap title="Antivírus">
            <Echart
              :height="240"
              :options="rosca('antivirus', (v) => rotulo(ANTIVIRUS, v))"
              @click="(e) => clicar(e, 'antivirus')"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="6" :sm="12" :xs="24">
          <ContentWrap title="Arquitetura">
            <Echart :height="240" :options="rosca('arquitetura')" />
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Último contato dos agentes">
            <Echart :height="240" :options="colunas('contato', undefined, corContato)" />
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Alertas abertos">
            <Echart
              v-if="p.series.alertas.length"
              :height="240"
              :options="barras('alertas')"
              @click="router.push('/inventario/alertas')"
            />
            <el-empty v-else :image-size="60" description="Nenhum alerta aberto" />
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Fabricantes (computadores)">
            <Echart
              :height="alturaBarras('fabricante')"
              :options="barras('fabricante')"
              @click="(e) => clicar(e, 'fabricante', 'Não informado')"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Modelos mais comuns">
            <Echart
              :height="alturaBarras('modelo')"
              :options="barras('modelo')"
              @click="(e) => clicar(e, 'modelo', 'Não informado')"
            />
          </ContentWrap>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :lg="12" :xs="24">
          <ContentWrap title="Softwares mais instalados">
            <Echart
              :height="alturaBarras('softwares')"
              :options="barras('softwares')"
              @click="router.push('/inventario/softwares')"
            />
          </ContentWrap>
        </el-col>
        <el-col :lg="12" :xs="24">
          <ContentWrap title="TAG dos agentes">
            <Echart
              :height="alturaBarras('tag')"
              :options="barras('tag')"
              @click="(e) => clicar(e, 'tag', 'Sem TAG')"
            />
          </ContentWrap>
        </el-col>
      </el-row>

      <ContentWrap title="Versões do agente em uso">
        <el-table :data="p.series.agente" size="small">
          <el-table-column label="Agente" min-width="260" prop="valor" />
          <el-table-column align="right" label="Máquinas" prop="total" width="120" />
          <el-table-column label="" min-width="200">
            <template #default="{ row }">
              <el-progress :percentage="pct(row.total, totalAgentes)" :stroke-width="8" />
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </template>
  </div>
</template>

<script lang="ts" setup>
import type { EChartsOption } from 'echarts'
import * as Inv from '@/api/inventario'
import { ANTIVIRUS, DISCOS, ORIGENS, SITUACOES_ATIVO, TIPOS_ATIVO, rotulo } from '@/api/inventario'

defineOptions({ name: 'InventarioPainel' })
const router = useRouter()
const loading = ref(true)
const p = ref<Inv.PainelInventario>()

const irAtivos = (query: Record<string, string>) =>
  router.push({ path: '/inventario/ativos', query })

interface Cartao {
  chave: string
  rotulo: string
  icone: string
  alerta?: boolean
  ir?: () => void
  sub?: (p: Inv.PainelInventario) => string
}
const CARTOES: Cartao[] = [
  {
    chave: 'ativos',
    rotulo: 'Equipamentos ativos',
    icone: 'ep:box',
    ir: () => router.push('/inventario/ativos'),
    sub: (x) => `${x.totais.baixados ?? 0} baixados`
  },
  {
    chave: 'computadores',
    rotulo: 'Computadores',
    icone: 'ep:monitor',
    ir: () => irAtivos({ tipo: 'desktop' })
  },
  {
    chave: 'impressoras',
    rotulo: 'Impressoras',
    icone: 'ep:printer',
    ir: () => irAtivos({ tipo: 'impressora' })
  },
  {
    chave: 'rede',
    rotulo: 'Equipamentos de rede',
    icone: 'ep:connection',
    ir: () => irAtivos({ tipo: 'rede' })
  },
  {
    chave: 'contato24h',
    rotulo: 'Falaram nas últimas 24 h',
    icone: 'ep:refresh',
    sub: (x) => `${pct(x.totais.contato24h, x.totais.ativos)}% do parque`
  },
  {
    chave: 'semContato',
    rotulo: 'Sem comunicação',
    icone: 'ep:warning',
    alerta: true,
    ir: () => irAtivos({ alerta: 'sem_contato' }),
    sub: (x) => `há mais de ${x.semContatoDias} dias`
  },
  {
    chave: 'semSuporte',
    rotulo: 'Sistema sem suporte',
    icone: 'ep:circle-close',
    alerta: true,
    ir: () => irAtivos({ alerta: 'sem_suporte' })
  },
  {
    chave: 'antivirus',
    rotulo: 'Antivírus com problema',
    icone: 'ep:lock',
    alerta: true,
    ir: () => irAtivos({ alerta: 'antivirus' })
  },
  {
    chave: 'ssd',
    rotulo: 'Com SSD',
    icone: 'ep:lightning',
    ir: () => irAtivos({ discoTipo: 'com_ssd' }),
    sub: (x) => `${pct(x.totais.ssd, x.totais.computadores)}% dos computadores`
  },
  {
    chave: 'alertas',
    rotulo: 'Alertas abertos',
    icone: 'ep:bell',
    alerta: true,
    ir: () => router.push('/inventario/alertas')
  },
  {
    chave: 'redeDesconhecidos',
    rotulo: 'Desconhecidos na rede',
    icone: 'ep:question-filled',
    alerta: true,
    ir: () => router.push('/inventario/rede')
  },
  {
    chave: 'softwares',
    rotulo: 'Softwares diferentes',
    icone: 'ep:files',
    ir: () => router.push('/inventario/softwares')
  }
]

const pct = (n?: number, total?: number) => (!total ? 0 : Math.round(((n ?? 0) / total) * 100))
const rotuloTipo = (v: string) => rotulo(TIPOS_ATIVO, v)
const rotuloSituacao = (v: string) => rotulo(SITUACOES_ATIVO, v)
const rotuloDisco = (v: string) => (v === 'sem_info' ? 'Não informado' : rotulo(DISCOS, v))
const dia = (v: string) => `${v.slice(8)}/${v.slice(5, 7)}`
const mes = (v: string) => `${v.slice(5)}/${v.slice(2, 4)}`
const semSuporte = computed(() => new Set((p.value?.series.semSuporte ?? []).map((f) => f.valor)))
const totalAgentes = computed(() => (p.value?.series.agente ?? []).reduce((n, f) => n + f.total, 0))
const corContato = (v: string) =>
  ({
    'Últimas 24 h': '#67c23a',
    '1 a 7 dias': '#95d475',
    '8 a 30 dias': '#e6a23c',
    '31 a 90 dias': '#f56c6c',
    'Mais de 90 dias': '#c45656',
    Nunca: '#909399'
  })[v]

const serie = (k: string) => p.value?.series[k] ?? []
const alturaBarras = (k: string) => Math.max(160, serie(k).length * 26 + 40)

/** Clique numa fatia ou barra: abre os ativos com aquele filtro (menos o valor "sem informação"). */
const clicar = (e: any, filtro: string, ignorar?: string) => {
  const v = e?.data?.valor
  if (v !== undefined && v !== ignorar) irAtivos({ [filtro]: String(v) })
}

const rosca = (k: string, nome: (v: string) => string = (v) => v): EChartsOption => {
  const dados = serie(k).map((f) => ({ name: nome(f.valor), value: f.total, valor: f.valor }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { type: 'scroll', bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{c}' },
        data: dados
      }
    ]
  }
}

const barras = (k: string, vermelho?: (v: string) => boolean): EChartsOption => {
  const dados = [...serie(k)].reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: dados.map((f) => f.valor),
      axisLabel: { width: 220, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        data: dados.map((f) => ({
          value: f.total,
          valor: f.valor,
          itemStyle: vermelho?.(f.valor) ? { color: '#f56c6c' } : undefined
        })),
        label: { show: true, position: 'right' }
      }
    ]
  }
}

const colunas = (
  k: string,
  nome: (v: string) => string = (v) => v,
  cor?: (v: string) => string | undefined
): EChartsOption => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 8, top: 24, bottom: 8, containLabel: true },
  xAxis: { type: 'category', data: serie(k).map((f) => nome(f.valor)) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      type: 'bar',
      data: serie(k).map((f) => ({
        value: f.total,
        itemStyle: cor?.(f.valor) ? { color: cor(f.valor) } : undefined
      })),
      label: { show: true, position: 'top' }
    }
  ]
})

const linha = (k: string, nome: (v: string) => string): EChartsOption => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
  xAxis: { type: 'category', data: serie(k).map((f) => nome(f.valor)), boundaryGap: false },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    { type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, data: serie(k).map((f) => f.total) }
  ]
})

onMounted(async () => {
  try {
    p.value = await Inv.getPainelInventario()
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
.cartao {
  position: relative;
  padding: 14px 16px;
  margin-bottom: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  &.clicavel {
    cursor: pointer;

    &:hover {
      border-color: var(--el-color-primary-light-5);
    }
  }

  &.alerta .valor {
    color: var(--el-color-danger);
  }

  .icone {
    position: absolute;
    top: 14px;
    right: 14px;
    font-size: 20px;
    color: var(--el-text-color-placeholder);
  }

  .rotulo {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .valor {
    margin-top: 4px;
    font-size: 26px;
    font-weight: bold;
  }

  .sub {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
