<!--
  Cabeçalho comum a todas as páginas do Centro de Operações:
  título, de onde vêm os dados, filtro de unidade (compartilhado), estado da conexão
  e uma faixa com as ocorrências críticas dos últimos 10 minutos (de qualquer módulo).
-->
<template>
  <div class="mh">
    <div class="mh__top">
      <div class="mh__title">
        <h1>{{ titulo }}</h1>
        <p v-if="fonte">{{ fonte }}</p>
      </div>
      <div class="mh__actions">
        <slot name="acoes"></slot>
        <el-select
          v-model="unidade"
          clearable
          filterable
          placeholder="Todas as unidades"
          style="width: 260px"
          aria-label="Filtrar por unidade"
        >
          <el-option v-for="u in store.unidades" :key="u.id" :label="`${u.name} · ${u.city}`" :value="u.id">
            <span class="mh__dot" :class="`is-${u.kpi?.status || 'ok'}`"></span>{{ u.name }}
            <small class="mh__city">{{ u.city }}</small>
          </el-option>
        </el-select>
        <span class="mh__live" :class="status === 'OPEN' ? 'is-on' : 'is-off'">
          <i></i>{{ status === 'OPEN' ? 'Ao vivo' : 'Reconectando…' }}
        </span>
      </div>
    </div>
    <router-link v-if="store.criticasRecentes.length && !semAlertas" class="mh__alert" :to="{ name: 'MonitorVisaoGeral', query: $route.query }">
      <b>{{ store.criticasRecentes.length }} {{ store.criticasRecentes.length === 1 ? 'ocorrência crítica' : 'ocorrências críticas' }}</b>
      nos últimos 10 min · mais recente: {{ store.nomeUnidade(store.criticasRecentes[0].unitId) }} — {{ store.criticasRecentes[0].texto }}
    </router-link>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import { useUnidadeFiltro } from './util'

defineProps<{ titulo: string; fonte?: string; semAlertas?: boolean }>()

const store = useUnidadeFiltro()
const { status } = useMonitorSocket()
const unidade = computed({
  get: () => store.unidadeId,
  set: (v?: number) => store.setUnidade(v)
})
</script>

<style scoped lang="scss">
.mh {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}
.mh__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
}
.mh__title {
  h1 { margin: 0; font-size: 20px; font-weight: 600; }
  p { margin: 2px 0 0; font-size: 13px; color: var(--el-text-color-secondary); }
}
.mh__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.mh__live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  white-space: nowrap;
  i { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
  &.is-on { color: var(--el-color-success); i { animation: mh-pulse 1.6s infinite; } }
  &.is-off { color: var(--el-color-warning); }
}
.mh__alert {
  display: block;
  padding: 8px 12px;
  border-radius: 6px;
  border-left: 4px solid var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  color: var(--el-text-color-primary);
  font-size: 13px;
  text-decoration: none;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  b { color: var(--el-color-danger); }
}
.mh__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
  &.is-ok { background: var(--el-color-success); }
  &.is-atencao { background: var(--el-color-warning); }
  &.is-critico { background: var(--el-color-danger); }
}
.mh__city { margin-left: 8px; color: var(--el-text-color-secondary); }
@keyframes mh-pulse { 50% { opacity: 0.35; } }
@media (prefers-reduced-motion: reduce) { .mh__live i { animation: none !important; } }
</style>
