<!-- Lista de mensagens no estilo do Graylog: linha com horário, origem e colunas; abaixo a mensagem em fonte fixa. -->
<template>
  <div class="lista-mensagens">
    <div class="cab">
      <span class="c-hora">Horário</span>
      <span class="c-origem">Origem</span>
      <span v-for="col in colunas" :key="col" class="c-col">{{ col }}</span>
    </div>
    <div
      v-for="(m, i) in mensagens"
      :key="i"
      class="linha"
      :class="'nivel-' + Math.min(m.nivel, 6)"
      @click="c.abrirMensagem(m)"
    >
      <div class="topo">
        <span class="c-hora">{{ quando(m.momento) }}</span>
        <span class="c-origem" @click.stop>
          <ValorClicavel campo="origem" :valor="m.origem" :base="base" />
        </span>
        <span v-for="col in colunas" :key="col" class="c-col" @click.stop>
          <ValorClicavel
            v-if="valor(m, col) !== undefined"
            :campo="col"
            :valor="valor(m, col)!"
            :base="base"
          />
        </span>
      </div>
      <div class="texto">{{ m.mensagem }}</div>
    </div>
    <el-empty v-if="!mensagens.length && !carregando" :image-size="60" description="Nenhuma mensagem" />
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { quando, useContexto } from '../contexto'
import ValorClicavel from './ValorClicavel.vue'

defineProps<{
  mensagens: Api.Mensagem[]
  colunas: string[]
  base?: Api.Filtro
  carregando?: boolean
}>()

const c = useContexto()

const COLUNAS_PROPRIAS: Record<string, keyof Api.Mensagem> = {
  fluxo: 'fluxo',
  aplicacao: 'aplicacao',
  ip: 'ip',
  nivel: 'nivel'
}
const valor = (m: Api.Mensagem, col: string) => {
  const propria = COLUNAS_PROPRIAS[col]
  const v = propria ? m[propria] : m.campos?.[col]
  return v === null || v === undefined || v === '' ? undefined : (v as string | number)
}
</script>

<style lang="scss" scoped>
.lista-mensagens {
  font-size: 12px;
}

.cab,
.topo {
  display: flex;
  gap: 12px;
}

.cab {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 6px 8px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
}

.c-hora {
  width: 150px;
  flex-shrink: 0;
}

.c-origem {
  width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

.c-col {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.linha {
  padding: 6px 8px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid transparent;

  &:hover {
    background: var(--el-fill-color-light);
  }

  .topo {
    font-weight: 600;
  }

  &.nivel-0,
  &.nivel-1,
  &.nivel-2,
  &.nivel-3 {
    border-left-color: var(--el-color-danger);
  }

  &.nivel-4 {
    border-left-color: var(--el-color-warning);
  }
}

.texto {
  display: -webkit-box;
  margin-top: 3px;
  overflow: hidden;
  font-family: Consolas, 'Courier New', monospace;
  line-height: 1.45;
  color: #2f6fb5; // azul, como no Graylog: lê melhor em fonte fixa que a cor do tema
  word-break: break-all;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

:global(html.dark .lista-mensagens .texto) {
  color: #7fb2ea;
}
</style>
