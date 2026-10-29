<!-- Valor de um campo com as ações do Graylog: mostrar as mensagens, somar ao filtro, excluir, copiar. -->
<template>
  <el-dropdown trigger="click" :teleported="true" @command="acao">
    <span class="valor-clicavel" :title="`${campo} = ${valor}`"><slot>{{ valor }}</slot></span>
    <template #dropdown>
      <div class="cabecalho">{{ campo }} = {{ cortado }}</div>
      <el-dropdown-menu>
        <el-dropdown-item command="mostrar">
          <Icon class="mr-6px" icon="ep:document" />Mostrar mensagens deste valor
        </el-dropdown-item>
        <el-dropdown-item command="filtrar">
          <Icon class="mr-6px" icon="ep:filter" />Adicionar ao filtro
        </el-dropdown-item>
        <el-dropdown-item command="excluir">
          <Icon class="mr-6px" icon="ep:remove" />Excluir dos resultados
        </el-dropdown-item>
        <el-dropdown-item command="copiar" divided>
          <Icon class="mr-6px" icon="ep:copy-document" />Copiar valor
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { useContexto } from '../contexto'

const props = defineProps<{
  campo: string
  valor: string | number
  /** filtro de onde o valor veio (o quadro): "mostrar mensagens" parte dele */
  base?: Api.Filtro
}>()

const c = useContexto()
const message = useMessage()
const cortado = computed(() => {
  const v = String(props.valor)
  return v.length > 60 ? v.slice(0, 60) + '…' : v
})

const acao = async (cmd: string) => {
  const v = String(props.valor)
  if (cmd === 'mostrar') {
    const b = props.base ?? {}
    c.mostrarMensagens(`${props.campo} = ${cortado.value}`, {
      ...b,
      consulta: Api.juntar(b.consulta, Api.termo(props.campo, v))
    })
  } else if (cmd === 'filtrar' || cmd === 'excluir') {
    c.filtrar(props.campo, v, cmd === 'excluir')
  } else {
    try {
      await navigator.clipboard.writeText(v)
      message.success('Copiado')
    } catch {
      message.warning('O navegador não deixou copiar')
    }
  }
}
</script>

<style lang="scss" scoped>
.valor-clicavel {
  cursor: pointer;
  border-bottom: 1px dotted transparent;

  &:hover {
    color: var(--el-color-primary);
    border-bottom-color: var(--el-color-primary);
  }
}

.cabecalho {
  max-width: 320px;
  padding: 8px 14px 6px;
  overflow: hidden;
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
