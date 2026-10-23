<!-- Indicador com rótulo, valor, linha de apoio e cor de estado. Com "to", vira atalho para outra página. -->
<template>
  <component :is="to ? 'router-link' : 'div'" :to="to" class="kpi" :class="[nivel && `is-${nivel}`, { 'is-link': to }]">
    <span class="kpi__label">{{ label }}</span>
    <strong class="kpi__value">{{ valor }}</strong>
    <em v-if="apoio" class="kpi__sub">{{ apoio }}</em>
  </component>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
defineProps<{ label: string; valor: string | number; apoio?: string; nivel?: 'ok' | 'warn' | 'crit' | ''; to?: RouteLocationRaw }>()
</script>

<style scoped lang="scss">
.kpi {
  display: block;
  min-width: 0;
  padding: 10px 14px;
  border: 1px solid var(--el-border-color-light);
  border-top: 3px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  color: inherit;
  text-decoration: none;
  &.is-ok { border-top-color: var(--el-color-success); }
  &.is-warn { border-top-color: var(--el-color-warning); }
  &.is-crit { border-top-color: var(--el-color-danger); .kpi__value { color: var(--el-color-danger); } }
  &.is-link:hover { background: var(--el-fill-color-light); }
}
.kpi__label {
  display: block;
  overflow: hidden;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--el-text-color-secondary);
}
.kpi__value {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.kpi__sub {
  display: block;
  overflow: hidden;
  font-size: 12px;
  font-style: normal;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--el-text-color-secondary);
}
</style>
