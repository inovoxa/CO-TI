<template>
  <el-empty
    v-if="!linhas.length"
    :image-size="60"
    description="Coloque datas previstas nas tarefas para ver o cronograma"
  />
  <div v-else class="gantt" role="img" :aria-label="`Cronograma de ${linhas.length} tarefas`">
    <!-- cabeçalho: meses -->
    <div class="linha cabecalho">
      <div class="nome"></div>
      <div class="trilho">
        <span v-for="m in meses" :key="m.rotulo" class="mes" :style="{ left: m.esq + '%' }">{{
          m.rotulo
        }}</span>
      </div>
    </div>
    <div v-for="l in linhas" :key="l.t.id" class="linha">
      <div class="nome" :style="{ paddingLeft: l.nivel * 14 + 'px' }" :title="l.t.nome">{{
        l.t.nome
      }}</div>
      <div class="trilho">
        <span v-if="hojeEsq !== null" class="hoje" :style="{ left: hojeEsq + '%' }"></span>
        <span
          v-if="l.t.marco"
          class="marco"
          :class="{ feita: l.t.situacao === 3, atrasada: l.t.atrasada }"
          :style="{ left: l.esq + l.larg + '%' }"
          :title="`${l.t.nome} — marco em ${data(l.t.fimPrevisto || l.t.inicioPrevisto)}`"
        ></span>
        <span
          v-else
          class="barra"
          :class="{
            feita: l.t.situacao === 3,
            cancelada: l.t.situacao === 4,
            atrasada: l.t.atrasada
          }"
          :style="{ left: l.esq + '%', width: l.larg + '%' }"
          :title="`${l.t.nome}: ${data(l.t.inicioPrevisto)} a ${data(l.t.fimPrevisto)} · ${l.t.situacaoRotulo} · ${l.t.percentual}%`"
        >
          <span class="feito" :style="{ width: l.t.percentual + '%' }"></span>
        </span>
        <span class="pct" :style="{ left: Math.min(l.esq + l.larg, 92) + '%' }"
          >{{ l.t.percentual }}%</span
        >
      </div>
    </div>
    <div class="legenda">
      <span><i class="amostra plano"></i>Previsto</span>
      <span><i class="amostra feito-am"></i>Feito</span>
      <span><i class="amostra concluida"></i>Concluída</span>
      <span><i class="amostra atraso"></i>Atrasada (contorno)</span>
      <span><i class="losango"></i>Marco</span>
      <span><i class="traco"></i>Hoje</span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { Tarefa } from '@/api/itsm/governanca'
import { data } from '@/api/itsm/governanca'

defineOptions({ name: 'ProjetoGantt' })
const props = defineProps<{ tarefas: Tarefa[] }>()

const DIA = 86_400_000
const ms = (d: string) => new Date(d + 'T00:00:00').getTime()

/** Tarefas com datas, na ordem da árvore (subtarefa logo abaixo da superior). */
const ordenadas = computed(() => {
  const filhos = new Map<number | undefined, Tarefa[]>()
  props.tarefas.forEach((t) => {
    const k = props.tarefas.some((p) => p.id === t.tarefaPaiId) ? t.tarefaPaiId : undefined
    filhos.set(k, [...(filhos.get(k) || []), t])
  })
  const out: { t: Tarefa; nivel: number }[] = []
  const visitar = (pai: number | undefined, nivel: number) =>
    (filhos.get(pai) || []).forEach((t) => {
      out.push({ t, nivel })
      visitar(t.id, nivel + 1)
    })
  visitar(undefined, 0)
  return out.filter((x) => x.t.inicioPrevisto || x.t.fimPrevisto)
})

const faixa = computed(() => {
  const datas = ordenadas.value
    .flatMap((x) => [x.t.inicioPrevisto, x.t.fimPrevisto])
    .filter(Boolean) as string[]
  if (!datas.length) return { ini: 0, fim: 1 }
  const ini = Math.min(...datas.map(ms)) - 2 * DIA
  const fim = Math.max(...datas.map(ms)) + 3 * DIA
  return { ini, fim }
})
const pos = (t: number) => ((t - faixa.value.ini) / (faixa.value.fim - faixa.value.ini)) * 100

const linhas = computed(() =>
  ordenadas.value.map(({ t, nivel }) => {
    const ini = ms((t.inicioPrevisto || t.fimPrevisto)!)
    const fim = ms((t.fimPrevisto || t.inicioPrevisto)!) + DIA
    return { t, nivel, esq: pos(ini), larg: Math.max(pos(fim) - pos(ini), 0.8) }
  })
)

const meses = computed(() => {
  const out: { rotulo: string; esq: number }[] = []
  const d = new Date(faixa.value.ini)
  d.setDate(1)
  d.setMonth(d.getMonth() + 1)
  while (d.getTime() < faixa.value.fim) {
    out.push({
      rotulo: d.toLocaleDateString('pt-BR', { month: 'short', year: '2-digit' }),
      esq: pos(d.getTime())
    })
    d.setMonth(d.getMonth() + 1)
  }
  return out
})

const hojeEsq = computed(() => {
  const h = new Date()
  h.setHours(0, 0, 0, 0)
  const p = pos(h.getTime())
  return p >= 0 && p <= 100 ? p : null
})
</script>

<style scoped>
.gantt {
  font-size: 12px;
}
.linha {
  display: flex;
  align-items: center;
  height: 28px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.cabecalho {
  height: 22px;
  color: var(--el-text-color-secondary);
}
.nome {
  overflow: hidden;
  width: 220px;
  flex-shrink: 0;
  padding-right: 8px;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.trilho {
  position: relative;
  height: 100%;
  flex: 1;
}
.mes {
  position: absolute;
  top: 3px;
  padding-left: 3px;
  border-left: 1px solid var(--el-border-color);
}
.barra {
  position: absolute;
  top: 7px;
  overflow: hidden;
  height: 14px;
  background: var(--el-color-primary-light-7);
  border-radius: 4px;
}
.barra .feito {
  display: block;
  height: 100%;
  background: var(--el-color-primary);
}
.barra.feita .feito {
  background: var(--el-color-success);
}
.barra.cancelada {
  opacity: 0.35;
}
.barra.atrasada {
  box-shadow: inset 0 0 0 2px var(--el-color-danger);
}
.marco {
  position: absolute;
  top: 8px;
  width: 12px;
  height: 12px;
  margin-left: -6px;
  background: var(--el-color-warning);
  transform: rotate(45deg);
}
.marco.feita {
  background: var(--el-color-success);
}
.marco.atrasada {
  outline: 2px solid var(--el-color-danger);
}
.pct {
  position: absolute;
  top: 6px;
  padding-left: 6px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
.hoje {
  position: absolute;
  top: 0;
  bottom: 0;
  border-left: 2px dashed var(--el-color-danger);
}
.legenda {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
}
.amostra {
  display: inline-block;
  width: 14px;
  height: 10px;
  margin-right: 4px;
  vertical-align: -1px;
  border-radius: 3px;
}
.plano {
  background: var(--el-color-primary-light-7);
}
.feito-am {
  background: var(--el-color-primary);
}
.concluida {
  background: var(--el-color-success);
}
.atraso {
  box-shadow: inset 0 0 0 2px var(--el-color-danger);
}
.losango {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 5px;
  background: var(--el-color-warning);
  transform: rotate(45deg);
}
.traco {
  display: inline-block;
  height: 12px;
  margin-right: 5px;
  vertical-align: -2px;
  border-left: 2px dashed var(--el-color-danger);
}
</style>
