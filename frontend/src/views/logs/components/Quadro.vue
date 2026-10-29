<!-- Quadro de um painel: cabeçalho com as ações do Graylog (atualizar, largura, tela cheia, editar, mais) e o conteúdo
     conforme o tipo. -->
<template>
  <div class="quadro" :class="'largura-' + largura">
    <div class="cabecalho">
      <div class="titulo" :title="quadro.titulo">
        <Icon class="alca" icon="ep:operation" :size="14" />{{ quadro.titulo }}
      </div>
      <div class="acoes">
        <el-tooltip content="Atualizar" placement="top" :show-after="400">
          <button @click="corpo?.recarregar()"><Icon icon="ep:caret-right" :size="16" /></button>
        </el-tooltip>
        <el-tooltip v-if="editavel" content="Largura" placement="top" :show-after="400">
          <button @click="emit('largura')"><Icon icon="ep:d-caret" :size="15" class="girado" /></button>
        </el-tooltip>
        <el-tooltip content="Tela cheia" placement="top" :show-after="400">
          <button @click="cheio = true"><Icon icon="ep:full-screen" :size="14" /></button>
        </el-tooltip>
        <el-tooltip v-if="editavel" content="Editar" placement="top" :show-after="400">
          <button @click="emit('editar')"><Icon icon="ep:edit" :size="14" /></button>
        </el-tooltip>
        <el-dropdown trigger="click" @command="menu">
          <button><Icon icon="ep:arrow-down" :size="14" /></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="busca"><Icon icon="ep:search" />Abrir na busca</el-dropdown-item>
              <template v-if="editavel">
                <el-dropdown-item command="duplicar"><Icon icon="ep:copy-document" />Duplicar</el-dropdown-item>
                <el-dropdown-item command="esquerda"><Icon icon="ep:back" />Mover para trás</el-dropdown-item>
                <el-dropdown-item command="direita"><Icon icon="ep:right" />Mover para frente</el-dropdown-item>
                <el-dropdown-item command="remover" divided>
                  <span class="text-[var(--el-color-danger)]"><Icon icon="ep:delete" />Excluir</span>
                </el-dropdown-item>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <div class="corpo" :class="'tipo-' + quadro.tipo">
      <component :is="COMPONENTES[quadro.tipo]" ref="corpo" :quadro="quadro" />
    </div>

    <el-dialog v-model="cheio" :title="quadro.titulo" fullscreen append-to-body destroy-on-close>
      <div class="cheio">
        <component :is="COMPONENTES[quadro.tipo]" :quadro="cheioQuadro" />
      </div>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { filtroDoQuadro, useContexto } from '../contexto'
import QuadroContador from './QuadroContador.vue'
import QuadroHistograma from './QuadroHistograma.vue'
import QuadroMensagens from './QuadroMensagens.vue'
import QuadroRanking from './QuadroRanking.vue'

const props = defineProps<{ quadro: Api.Quadro; editavel?: boolean }>()
const emit = defineEmits<{
  (e: 'editar' | 'remover' | 'duplicar' | 'largura' | 'esquerda' | 'direita'): void
}>()

const COMPONENTES = {
  contador: QuadroContador,
  mensagens: QuadroMensagens,
  ranking: QuadroRanking,
  histograma: QuadroHistograma
}

const router = useRouter()
const contexto = useContexto()
const corpo = ref<{ recarregar: () => void }>()
const cheio = ref(false)
const largura = computed(() => props.quadro.largura || 1)
// em tela cheia o ranking mostra mais itens
const cheioQuadro = computed(() =>
  props.quadro.tipo === 'ranking' ? { ...props.quadro, limite: Math.max(props.quadro.limite || 10, 50) } : props.quadro
)

const menu = (cmd: string) => {
  if (cmd === 'busca') {
    const f = filtroDoQuadro(props.quadro, contexto)
    router.push({
      name: 'LogsBusca',
      query: { q: Api.juntar(f.consulta, f.filtroGeral), fluxo: f.fluxo || undefined, periodo: f.periodo }
    })
  } else {
    emit(cmd as any)
  }
}
</script>

<style lang="scss" scoped>
.quadro {
  display: flex;
  min-width: 0;
  overflow: hidden;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  flex-direction: column;
}

.cabecalho {
  display: flex;
  padding: 7px 8px 6px 10px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.titulo {
  display: flex;
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
  align-items: center;
  gap: 8px;
}

.alca {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.acoes {
  display: flex;
  flex-shrink: 0;
  align-items: center;

  button {
    display: inline-flex;
    width: 24px;
    height: 24px;
    padding: 0;
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: none;
    border: none;
    border-radius: 4px;
    align-items: center;
    justify-content: center;

    &:hover {
      color: var(--el-color-primary);
      background: var(--el-fill-color-light);
    }
  }
}

.girado {
  transform: rotate(90deg);
}

.corpo {
  min-height: 0;
  padding: 0 10px 8px;
  flex: 1;

  &.tipo-mensagens {
    height: 420px;
  }

  &.tipo-ranking {
    height: 330px;
  }

  &.tipo-histograma {
    height: 260px;
  }
}

.cheio {
  height: calc(100vh - 110px);
}
</style>
